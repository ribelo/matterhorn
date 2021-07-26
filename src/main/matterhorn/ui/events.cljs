(ns matterhorn.ui.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [meander.epsilon :as m]))

(rf/reg-event-fx
 ::change-view
 (fn [{:keys [db]} [_ view]]
   (enc/have! keyword? :in view)
   (enc/cond
     :let [current-view (get-in db [:db/id :ui/main :view])
           last-view    (get-in db [:db/id :ui/main :last-view])]
     (not= view current-view)
     {:fx [[:commit [:app/db [[:dx/put [:db/id :ui/main] :view              view]
                           [:dx/put [:db/id :ui/main] :last-view current-view]]]]]}
     (= view current-view)
     {:fx [[:commit [:app/db [[:dx/put [:db/id :ui/main] :view      last-view]
                           [:dx/put [:db/id :ui/main] :last-view current-view]]]]]})))

(rf/reg-event-fx
 ::set-last-key
 (fn [_ [_eid key]]
   (enc/have! string? key)
   {:fx [[:commit       [         :app/db [[:dx/put    [:db/id :ui/main] :last-key key]]]]
         [:commit-later [500 _eid :app/db [[:dx/delete [:db/id :ui/main] :last-key]]]]
         ]}))
