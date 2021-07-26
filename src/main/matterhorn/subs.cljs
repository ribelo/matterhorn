(ns matterhorn.subs
  (:require
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]
   [matterhorn.ipc :as ipc]))

(rf/reg-sub-raw
 :pull
 (fn [_ [_ store q eid]]
   (ra/reaction
    (dx/with-dx! [db_ store]
      (dx/pull @db_ q eid)))))

(rf/reg-sub-raw
 :pull-one
 (fn [_ [_ store q eid]]
   (ra/reaction
    (dx/with-dx! [db_ store]
      (dx/pull-one @db_ q eid)))))


(comment
  (rf/clear-subscription-cache!)
  (rf/subscribe [:matterhorn/version]))
