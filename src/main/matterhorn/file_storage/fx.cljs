(ns matterhorn.file-storage.fx
  (:require
   [re-frame.core :as rf]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [ribelo.doxa :as dx]
   [matterhorn.file-storage.util :as u]))

(let [timeout_ (atom {})]
  (rf/reg-fx
   :freeze-dx
   (fn [store db]
     (timbre/debug ::freeze-dx store)
     (-> (@timeout_ store) js/clearTimeout)
     (swap! timeout_ assoc store (js/setTimeout #(u/freeze-dx store db) 3000)))))

;; TODO on-success/failure
(let [timeout_ (atom {})]
  (rf/reg-fx
   :freeze-store
   (fn [store]
     (timbre/debug ::freeze-store store)
     (-> (@timeout_ store) js/clearTimeout)
     (swap! timeout_ assoc (js/setTimeout #(u/freeze-store store) 3000))
     ;; (some-> on-success rf/dispatch)
     ;; (some-> on-failure rf/dispatch)
     )))

(rf/reg-fx
 :thaw-store
 (fn [{:keys [store on-success on-failure]}]
   (timbre/debug ::thaw-store store)
   (if (u/thaw-store store)
     (some-> on-success rf/dispatch)
     (some-> on-failure rf/dispatch))))

(rf/reg-fx
 :sync-store
 (fn [store]
   (timbre/debug ::sync-store store)
   (dx/with-dx! [dx_ store]
     (dx/listen! dx_ (enc/merge-keywords [:sync/store store])
                 (fn [db] (when db (u/freeze-dx store db)))))))
