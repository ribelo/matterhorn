(ns matterhorn.db.core
  (:require
   [re-frame.db]
   [ribelo.doxa :as dx]
   [reagent.core :as r]))

(def default-db (dx/create-dx))

(dx/reg-dx! :app/db re-frame.db/app-db)
(dx/reg-dx!
 :app/settings
 (r/atom (dx/commit (hash-map) [[:dx/put [:db/id :settings] {:tf :mn :risk 0.3 :frisk 0.0 :freq 12 :money 0.0}]])))
(dx/reg-dx! :yahoo/db          (r/atom (hash-map)))
(dx/reg-dx! :yahoo/quotes      (r/atom (hash-map)))
(dx/reg-dx! :app/cache         (r/atom (hash-map)))
