(ns matterhorn.provider.yf.subs
  (:require
   [taoensso.encore :as enc]
   [meander.epsilon :as m]
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [cuerdas.core :as str]
   [net.cgrand.xforms :as x]
   [ribelo.doxa :as dx]
   [ribelo.danzig :as dz :refer [=>>]]
   [matterhorn.provider.yf.events :as yf.evt]))

;;
;; * ui
;;

(rf/reg-sub-raw
 ::ticker-exists?
 (fn [_ [_ ticker]]
   (dx/with-dx! [cache_ :app/cache]
     (ra/reaction
      (when (seq ticker) (some? (get-in @cache_ [:db/id (keyword ticker) :crumb])))))))

(comment
  (rf/subscribe [::ticker-exists? "or.pa"])
  )

(rf/reg-sub-raw
 ::search-ticker-input
 (fn [_ _]
   (dx/with-dx! [app_ :app/db]
     (ra/reaction
      (get-in @app_ [:db/id :app/ui :input-search ] "")))))

(comment
  (rf/clear-subscription-cache!)
  (rf/subscribe [::search-ticker-input]))

(rf/reg-sub-raw
 ::searching-ticker?
 (fn [_ _]
   (ra/reaction
    (dx/with-dx! [cache_ :app/cache]
      (get-in @cache_ [:db/id :yahoo/web :searching-ticker?])))))

(rf/reg-sub-raw
 ::searching-failure?
 (fn [_ _]
   (ra/reaction
    (dx/with-dx! [cache_ :app/cache]
      (boolean (get-in @cache_ [:db/id :yahoo/web :searching-failure?]))))))

(rf/reg-sub-raw
 ::search-ticker-results
 (fn [_ _]
   (ra/reaction
    (dx/with-dx! [cache_ :app/cache]
      (get-in @cache_ [:db/id :yahoo/web :search-ticker-result])))))

(comment
  (rf/clear-subscription-cache!)
  @(rf/subscribe [::search-ticker-input])
  @(rf/subscribe [::search-ticker-results])

  @(rf/subscribe [::input-ticker-exists?])
  @(rf/subscribe [::ticker-exists? :goog])
  (dx/with-dx! [cache_ :yahoo/db]
    (tap> @cache_)))

;;
;; * web
;;



;;
;; * trading
;;

(rf/reg-sub-raw
 ::quotes-tickers
 (fn [_ _]
   (ra/reaction
    (dx/with-dx! [quotes_ :yahoo/quotes]
      (into [] (x/sort) (keys (get-in @quotes_ [:db/id])))))))

(rf/reg-sub-raw
 ::valid-quotes-data?
 (fn [_ [_ ticker]]
   (ra/reaction
    (dx/with-dx! [quotes_ :yahoo/quotes]
      (not (empty? (get-in @quotes_ [:db/id ticker :data])))))))

(rf/reg-sub-raw
 ::download-ticker?
 (fn [_ [_ ticker]]
   (enc/have! keyword? ticker)
   (ra/reaction
    (when ticker
      (->> @(rf/subscribe [::quotes-tickers])
           (enc/rsome #{ticker}))))))

(rf/reg-sub-raw
 ::ticker-full-name
 (fn [_ [_ ticker]]
   (ra/reaction
    (dx/with-dx! [app_ :yahoo/db]
      (get-in @app_ [:db/id ticker :full-name])))))

(comment
  (rf/clear-subscription-cache!)

  (rf/subscribe [::ticker-full-name :msft])
  (rf/subscribe [:pull-one :yahoo/db [:full-name] [:db/id :msft]])
  (dx/with-dx! [app_ :yahoo/db]
    @app_))

(comment
  (rf/subscribe [::download-ticker? "msft"])
  (dx/with-dx! [cache_ :yahoo/db]
    (tap> [:app/cache @cache_]))

  )

(rf/reg-sub-raw
 ::fetching?
 (fn [_ _]
   (dx/with-dx [cache_ :app/cache]
     (ra/reaction
      (m/find (get-in @cache_ [:db/id])
        (m/$ {:fetching? true}) true
        _ false)))))
