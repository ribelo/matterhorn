(ns matterhorn.provider.cboe.events
  (:require
   [taoensso.encore :as enc]
   [re-frame.core :as rf]
   [cuerdas.core :as str]
   [cljs-bean.core :refer [->clj]]
   [ribelo.danzig :as dz :refer [=>>]]
   [ribelo.doxa :as dx]
   [matterhorn.provider.cboe.util :as u]
   [matterhorn.options.api :as opt.api]
   ["date-fns" :as dtf]))

(rf/reg-event-fx
 ::fetch-options-data
 (fn [_ [_ ticker]]
   (let [url (enc/format "https://cdn.cboe.com/api/global/delayed_quotes/options/%s.json"
                         (str/upper (name ticker)))]
     {:fx [[:axios {:method :get
                    :url url
                    :on-success [::fetch-options-data-success ticker]}]]})))

(rf/reg-event-fx
 ::fetch-options-data-success
 (fn [_ [_eid ticker ^js resp]]
   (let [data (->clj (.-data resp))]
     {:fx [[:commit       [:cboe/cache [:dx/put    [:db/id ticker] :options-data data]]]
           [:commit-later [(enc/ms :mins 1) [_eid ticker]
                           :cboe/cache [:dx/delete [:db/id ticker] :options-data]]]]})))

(defn parse-ticker-data [m]
  (persistent!
   (reduce-kv
    (fn [acc k v]
      (let [k (str/keyword k)]
        (enc/cond!
          (enc/kw-identical? k :last-trade-time)
          (assoc! acc k (some-> v js/Date. dtf/formatISO))

          (#{:security-type :symbol :tick} k)
          (assoc! acc k (-> v str/lower keyword))

          (#{:exchange-id :options :seqno} k)
          acc

          :else
          (assoc! acc k v))))
    (transient {:timestamp (m :timestamp)})
    (m :data))))

(defn parse-options-data [m]
  (mapv
   (fn [opt]
     (persistent!
      (reduce-kv
       (fn [acc k v]
         (enc/cond!
           (enc/kw-identical? k :option)
           (reduce-kv
            (fn [acc k v] (assoc! acc k v))
            acc
            (u/option-str->data v))

           (#{:symbol} k)
           (assoc! acc k (-> v str/lower str/keyword))

           (#{:delta :gamma :theta :vega :rho :volume} k)
           (assoc! acc k v)

           :else
           acc))
       (transient {})
       opt)))
   (get-in m [:data :options])))

(comment
  (enc/do-true (tap> (parse-options-data @atm))))

(rf/reg-event-fx
 ::fetch-ticker
 [(rf/inject-cofx ::dx/with-dx! [:cache :cboe/cache])]
 (fn [{:keys [cache]} [_eid ticker]]
   (enc/have! keyword? ticker)
   (enc/cond!
     :let [htree (get-in cache [:db/id ticker :options-data])]

     (some? htree)
     {:fx [[:commit [:cboe/ticker [:dx/put [:db/id ticker] (parse-ticker-data htree)]]]]}

     (nil? htree)
     (do
       (tap> [:test _eid ticker :nill-htree])
       {:fx [[:async-flow
              {:id             (enc/merge-keywords [_eid :async-flow ticker])
               :first-dispatch [::fetch-options-data ticker]
               :rules          [{:when     :seen? :events ::fetch-options-data-success
                                 :dispatch [::fetch-ticker ticker]
                                 :halt?    true}]}]]}))))

(rf/reg-event-fx
 ::fetch-options
 [(rf/inject-cofx ::dx/with-dx! [:cache :cboe/cache])]
 (fn [{:keys [cache]} [_eid ticker]]
   (enc/have! keyword? ticker)
   (enc/cond!
     :let [htree (get-in cache [:db/id ticker :options-data])]
     (some? htree)
     {:fx [[:commit [:cboe/options [:dx/put (parse-options-data htree)]]]]}

     (nil? htree)
     (do
       (tap> [:test _eid ticker :nill-htree])
       {:fx [[:async-flow
              {:id             (enc/merge-keywords [_eid :async-flow ticker])
               :first-dispatch [::fetch-options-data ticker]
               :rules          [{:when     :seen? :events ::fetch-options-data-success
                                 :dispatch [::fetch-options ticker]
                                 :halt?    true}]}]]}))))

(comment

  (type (assoc (.-EMPTY cljs.core/PersistentHashMap) :a 1))
  (type (assoc (hash-map) :a 1))

  (rf/dispatch [::fetch-options :msft])
  (dx/with-dx [options :cboe/options]
    (opt.api/nope @options :msft))
  (dx/with-dx [cache :cboe/cache]
    (let [data (get-in @cache [:db/id :msft :options-data])]
      (tap> data)))

  (dx/commit {} [:dx/put [:db/id 1] [{:a 1 :db/id 2} {:a 2 :db/id 3}]]))
