(ns matterhorn.file-storage.fx
  (:require
   [re-frame.core :as rf]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [missionary.core :as mi]
   [ribelo.doxa :as dx]
   [matterhorn.file-storage.util :as u]))

(let [tasks_ (atom {})]
  (rf/reg-fx
   :freeze-dx
   (fn [store db]
     (timbre/debug ::freeze-dx store)
     (when-let [cancel (@tasks_ store)] (cancel))
     (let [task (mi/sp (mi/? (mi/sleep 3000)) (u/freeze-dx store db))]
       (swap! tasks_ assoc store (task #() #()))))))

;; TODO on-success/failure
(let [tasks_ (atom {})]
  (rf/reg-fx
   :freeze-store
   (fn [store]
     (timbre/debug ::freeze-store store)
     (when-let [cancel (@tasks_ store)] (cancel))
     (let [task (mi/sp (mi/? (mi/sleep 3000)) (u/freeze-store store))]
       (swap! tasks_ assoc store (task #() #())))
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
