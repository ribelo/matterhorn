(ns matterhorn.ui.subs
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]))

(rf/reg-sub-raw
 ::view
 (fn [_ _]
   (dx/with-dx! [db_ :app/db]
     (ra/reaction
      (get-in @db_ [:db/id :ui/main :view] [:main :settings])))))

(rf/reg-sub-raw
 ::last-key
 (fn [_ _]
   (dx/with-dx [db_ :app/db]
     (ra/reaction
      (get-in @db_ [:db/id :ui/main :last-key])))))
