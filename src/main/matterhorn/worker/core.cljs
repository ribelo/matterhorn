(ns matterhorn.worker.core
  (:refer-clojure :exclude [-reset!])
  (:require
   [matterhorn.transit :as t]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [promesa.core :as p]))

(defprotocol IWorker
  (-reset! [_])
  (-handle [_ e promise])
  (dispatch! [_ msg])
  (kill! [_]))

(deftype Worker [path ^:mutable worker ^:mutable cnt uuid]
  ICounted
  (-count [_] cnt)
  IWorker
  (-reset! [_]
    (when worker
      (timbre/errorf "worker does not respond for too long, restart")
      (.terminate worker)
      (set! worker (js/Worker. path))))
  (-handle [this msg promise]
    (when worker
      (let [t (p/delay (enc/ms :mins 1) ::timeout)]
        (-> (p/race [promise t])
            (p/finally (fn [v]
                         (when (enc/kw-identical? v ::timeout)
                           (-reset! this)))))
        (.addEventListener worker "message"
                           (fn [^js e]
                             (let [[k data] (t/read-transit (.-data e))]
                               (cond
                                 (enc/kw-identical? k :ok)
                                 (p/resolve! promise data)
                                 (enc/kw-identical? k :err)
                                 (do
                                   (timbre/error "error in worker: " data)
                                   (p/reject! promise "sex")))
                               (set! cnt (dec cnt))))
                           #js {:once true})
        (.postMessage worker (t/write-transit msg)))))
  (dispatch!
    [this msg]
    (let [promise (p/deferred)]
      (set! cnt (inc cnt))
      (js/setTimeout #(-handle this msg promise))
      promise))
  (kill! [_]
    (when worker
      (.terminate worker)
      (set! worker nil))))

(defn worker [path]
  (Worker. path (js/Worker. path) 0 (enc/uuid-str)))

(deftype WorkerPool [pool]
  IWorker
  (dispatch! [_ msg]
    (let [w (->> pool (sort-by count) first)]
      (dispatch! w msg)))
  (kill! [_]
    (doseq [w pool] (kill! w))))

(defn worker-pool [path n]
  (WorkerPool. (mapv (partial worker path) (range n))))
