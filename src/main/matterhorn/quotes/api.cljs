(ns matterhorn.quotes.api
  (:refer-clojure :exclude [time])
  (:require
   [taoensso.encore :as enc]
   [meander.epsilon :as m]
   [net.cgrand.xforms :as x]
   [ribelo.doxa :as dx]
   [ribelo.danzig :as dz :refer [=>>]]
   [ribelo.halle :as h]
   [ribelo.kemnath :as math]
   [ribelo.stade :as stats]
   [ribelo.qualsdorf :as quant]
   ["date-fns" :as dtf]
   ["date-fns-tz" :as dtf-tz]))

(defprotocol Candle
  (candle [_  ] [_ i  ] [_   start end])
  (time   [_  ] [_ i  ] [_   start end])
  (open   [_  ] [_ i  ] [_   start end])
  (high   [_  ] [_ i  ] [_   start end])
  (low    [_  ] [_ i  ] [_   start end])
  (close  [_  ] [_ i  ] [_   start end])
  (volume [_  ] [_ i  ] [_   start end])
  (arg    [_ k] [_ k i] [_ k start end]))

(extend-protocol Candle
  cljs.core/PersistentVector
  (candle
    ([xs i]
     (when xs
       (enc/cond!
         (not (neg? i)) (get xs i)
         (neg? i)       (get xs (+ (count xs) i)))))
    ([xs start end]
     (when xs
       (enc/cond!
         (and (not (neg? start)) (not (neg? end)))
         (subvec xs start end)
         (and (neg? start) (neg? end))
         (subvec xs (+ (count xs) start) (+ (inc (count xs)) end))
         (and (not (neg? start)) (neg? end))
         (subvec xs start (+ (count xs) end))))))
  (time
    ([xs]
     (into [] (map (fn [c] (.get c :time))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :time)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :time))) (candle xs start end))))
  (open
    ([xs]
     (into [] (map (fn [c] (.get c :open))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :open)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :open))) (candle xs start end))))
  (high
    ([xs]
     (into [] (map (fn [c] (.get c :high))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :high)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :high))) (candle xs start end))))
  (low
    ([xs]
     (into [] (map (fn [c] (.get c :low))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :low)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :low))) (candle xs start end))))
  (close
    ([xs]
     (into [] (map (fn [c] (.get c :close))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :close)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :close))) (candle xs start end))))
  (volume
    ([xs]
     (into [] (map (fn [c] (.get c :volume))) xs))
    ([xs i]
     (when-let [c (candle xs i)] (.get c :volume)))
    ([xs start end]
     (into [] (map (fn [c] (.get c :volume))) (candle xs start end))))
  (arg
    ([xs k]
     (into [] (map (fn [c] (.get c k))) xs))
    ([xs k i]
     (when-let [c (candle xs i)] (.get c k)))
    ([xs k start end]
     (into [] (map k) (candle xs start end)))))

(defn same-period? [tf d1 d2]
  (case tf
    :d1 (identical? (dtf/getDate  d1) (dtf/getDate d2))
    :mn (identical? (dtf/getMonth d1) (dtf/getMonth d2))))

(defn resample [data tf]
    (let [it (iter data)]
      (when (.hasNext it)
        (loop [c1 (.next it) c2 (.next it) acc (transient []) bar (transient {})]
          (enc/cond!
            (not (.hasNext it))
            (persistent!
             (conj! acc
                    (persistent!
                     (assoc! bar
                             :time   (c2 :time)
                             :high   (-> (bar :high)   (math/max (c2 :high)))
                             :low    (-> (bar :low)    (math/min (c2 :low)))
                             :close  (c2 :close)
                             :volume (+  (bar :volume) (c2 :volume))))))

            :let [d1 (js/Date. (c1 :time))
                  d2 (js/Date. (c2 :time))]

            (not (same-period? tf d1 d2))
            (recur (.next it) (.next it)
                   (conj! acc (persistent!
                               (assoc! bar
                                       :time   (dtf/formatISO d1)
                                       :high   (-> (bar :high) (math/max (c1 :high)))
                                       :low    (-> (bar :low)  (math/max (c1 :low)))
                                       :close  (c1 :close)
                                       :volume (+  (c1 :volume)))))
                   (transient
                    {:open   (c2 :open)
                     :high   (c2 :high)
                     :low    (c2 :low)
                     :close  (c2 :close)
                     :volume (c2 :volume)}))

            (same-period? tf d1 d2)
            (recur c2 (.next it)
                   acc
                   (assoc! bar
                           :high (-> (bar :high) (math/max (c1 :high)))
                           :low (-> (bar :low) (math/min (c1 :low)))
                           :close (c1 :close)
                           :volume (+ (bar :volume) (c1 :volume)))))))))

(defn quotes [db ticker tf]
  (when-let [data (not-empty (get-in db [:db/id ticker :data]))]
    (case tf
      :d1 data
      :mn (resample data tf)
      (enc/do-nil (tap> [:quotes :err ticker tf])))))

(comment
  (quotes {} :msft :mn)
  )

;; (defmulti quotes (fn [provider _] provider))
