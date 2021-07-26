(ns matterhorn.init
  (:require
   [reagent.ratom :as ra :refer [reaction]]
   [re-frame.core :as rf]
   [day8.re-frame.async-flow-fx]
   [cljs-bean.core :as bean :refer [->js ->clj]]
   [shadow.resource :as rc]
   [applied-science.js-interop :as j]
   [taoensso.timbre :as timbre]
   [ribelo.doxa :as dx]
   [matterhorn.subs]
   ;; [matterhorn.worker.fx]
   [matterhorn.schema]
   [matterhorn.fx]
   [matterhorn.cofx]
   [matterhorn.axios.fx]
   [matterhorn.file-storage.fx]
   [matterhorn.file-storage.events :as fs.evt]
   ;; [matterhorn.firebase.fx]
   ;; [matterhorn.firebase.events]
   [matterhorn.db.core]
   [matterhorn.db.fx]
   [matterhorn.db.events :as db.evt]
   [matterhorn.logging.events :as log.evt]
   [matterhorn.logging.subs :as log.sub]
   ;; [matterhorn.ui.subs]
   ;; [matterhorn.ui.events]
   ;; [matterhorn.auth.fx]
   ;; [matterhorn.auth.subs]
   ;; [matterhorn.auth.events]
   [matterhorn.provider.yf.api]
   [matterhorn.provider.yf.events :as yf.evt]
   [matterhorn.provider.yf.subs]
   [matterhorn.quant.events :as quant.evt]
   [matterhorn.wallet.events]
   [matterhorn.wallet.subs]

   [matterhorn.provider.cboe.events :as cboe.evt]))

(let [log-appender {:enabled?   true
                    :async?     true
                    :min-level  nil
                    :rate-limit nil
                    :output-fn  :inherit
                    :fn         (fn [data]
                                  (rf/dispatch [::log.evt/append data]))}
      tap-appender {:enabled?   true
                    :async?     true
                    :min-level  nil
                    :rate-limit nil
                    :output-fn  :inherit
                    :fn         (fn [data]
                                  (let [level     (:level data)
                                        timestamp @(:timestamp_ data)
                                        msg       @(:msg_ data)]
                                    (tap> [level timestamp msg])))}]
  (timbre/merge-config! {:level     :debug
                         :appenders {:re-frame log-appender
                                     :tap      tap-appender
                                     :console  {:enabled? false}
                                     :println  {:enabled? false}}}))

(rf/reg-event-fx
 ::set-boot-successful
 (fn [_ _]
   (timbre/info ::set-boot-successful)
   {:fx [[:commit [:app/db [:dx/put [:db/id :settings] :boot-successful? true]]]]}))

(rf/reg-sub-raw
 ::boot-successful?
 (fn [_ _]
   (dx/with-dx! [dx_ :app/db]
     (reaction
      (get-in @dx_ [:db/id :settings :boot-successful?])))))

(rf/reg-event-fx
 ::boot
 (fn [{:keys [db]} _]
   {:fx [[:dispatch [::db.evt/init-db]]
         [:dispatch [::fs.evt/thaw-store :app/settings]]
         [:dispatch [::fs.evt/thaw-store :yahoo/quotes]]
         [:dispatch [::fs.evt/thaw-store :yahoo/db]]
         ;; [:dispatch [::yf.evt/refresh-quotes]]
         ;;
         ;; [:dispatch [::yf.evt/fetch-selected-quotes]]

         [:dispatch [::set-boot-successful]]
         ]}))

(comment
  (rf/dispatch [::boot])
  (meta @re-frame.db/app-db))

