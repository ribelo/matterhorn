(ns matterhorn.logging.events
  (:require
   [taoensso.encore :as enc]
   [re-frame.core :as rf]))

(rf/reg-event-fx
 ::append
 (fn [_ [_eid data]]
   (let [level     (:level data)
         timestamp @(:timestamp_ data)
         msg       @(:msg_ data)]
     {:fx [[:commit [:app/db [[:dx/update [:db/id :log] :data
                               (fn [data] (into [[level timestamp msg]] (take 1024) data))]
                           [:dx/put    [:db/id :log] :last-event [level timestamp msg]]]]]
           [:commit-later [(enc/ms :secs 5) _eid :app/db
                           [:dx/delete [:db/id :log] :last-event]]]]})))
