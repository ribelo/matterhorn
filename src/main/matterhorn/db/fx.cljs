(ns matterhorn.db.fx
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]))

(rf/reg-fx
 :commit
 (fn [data]
   (binding [dx/*atom-fn* r/atom]
     (if (even? (count data))
       (let [it (iter data)]
         (while (.hasNext it)
           (let [store (.next it)
                 txs   (.next it)]
             (dx/with-dx! [db store]
               (enc/cond
                 :let          [tx (nth txs 0)]
                 (vector?  tx) (dx/commit! db txs)
                 (keyword? tx) (dx/commit! db [txs]))))))
       (timbre/error "matterhorn: \":commit\" number of elements should be even")))))

(let [timeouts (atom {})]
  (rf/reg-fx
   :commit-later
   (fn [data]
     (binding [dx/*atom-fn* r/atom]
       (if (zero? (mod (count data) 4))
         (let [it (iter data)]
           (while (.hasNext it)
             (let [ms    (.next it)
                   id    (.next it)
                   store (.next it)
                   txs   (.next it)]
               (dx/with-dx! [db store]
                 (let [t (enc/cond!
                           :do           (some-> (@timeouts id) js/clearTimeout)
                           :let          [tx (nth txs 0)]
                           (vector?  tx) (js/setTimeout #(dx/commit! db txs)   ms)
                           (keyword? tx) (js/setTimeout #(dx/commit! db [txs]) ms))]
                   (swap! timeouts assoc id t))))))
         (timbre/error "matterhorn: \":commit\" number of elements should be a multiple of 4"))))))
