(ns matterhorn.db.fx
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [missionary.core :as mi]
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

(let [tasks_ (atom {})]
  (rf/reg-fx
   :commit-later
   (fn [data]
     (binding [dx/*atom-fn* r/atom]
       (if (zero? (mod (count data) 4))
         (let [it (iter data)]
           (while (.hasNext it)
             (let [ms     (.next it)
                   id     (.next it)
                   store  (.next it)
                   txs    (.next it)
                   tx     (nth txs 0)
                   cancel (@tasks_ id)]
               (when cancel (cancel))
               (dx/with-dx! [db store]
                 (when-let [task (enc/cond!
                                   (vector? tx)
                                   (mi/sp (mi/? (mi/sleep ms)) (dx/commit! db txs))

                                   (keyword? tx)
                                   (mi/sp (mi/? (mi/sleep ms)) (dx/commit! db [txs])))]
                   (swap! tasks_ assoc id (task #() #())))))))
         (timbre/error "matterhorn: \":commit\" number of elements should be a multiple of 4"))))))
