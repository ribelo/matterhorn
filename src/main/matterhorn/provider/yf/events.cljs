(ns matterhorn.provider.yf.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [cuerdas.core :as str]
   [meander.epsilon :as m]
   [ribelo.danzig :as dz :refer [=>>]]
   ["date-fns" :as dtf]
   ["date-fns-tz" :as dtf-tz]
   [ribelo.doxa :as dx]
   [re-frame.core :as rf]
   [cljs-bean.core :refer [->clj]]
   [matterhorn.schema :as schema]
   [matterhorn.quotes.api :as q]
   [matterhorn.quant.events :as quant.evt]))

;;
;; * ui
;;

(rf/reg-event-fx
 ::set-input-search
 (fn [_ [_eid {:keys [ticker] :as m}]]
   (if (seq ticker)
     {:fx [[:commit-later   [(enc/ms :ms 500) [_eid :put]
                             :app/db [:dx/put [:db/id :app/ui] :input-search ticker]]]
           [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch]
                             [::search-ticker m]]]]}
     {:fx [[:commit [:app/cache [[:dx/delete [:db/id :yahoo/web] :search-ticker-result]
                                 [:dx/delete [:db/id :yahoo/web] :searching-failure?]
                                 [:dx/delete [:db/id :yahoo/web] :searching-ticker?]]]]
           [:commit-later   [(enc/ms :ms 500) [_eid :delete :input-search]
                             :app/db [:dx/delete [:db/id :app/ui] :input-search]]]
           [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch]
                             [::search-ticker m]]]]})))

;;
;; * web
;;

(rf/reg-event-fx
 ::search-ticker
 (fn [_ [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (if (seq ticker)
     {:fx [[:commit [:app/cache [:dx/put [:db/id :yahoo/web] :searching-ticker? true]]]
           [:axios {:method     :get
                    :url        (enc/format "https://query2.finance.yahoo.com/v1/finance/search?q=%s&lang=en-US" ticker)
                    :on-success [::search-ticker-success m]
                    :on-failure [::search-ticker-failure m]}]]}
     {:fx [[:commit [:app/cache [:dx/delete [:db/id :yahoo/web] :search-ticker-result]]]]})))

(rf/reg-event-fx
 ::search-ticker-success
 (fn [_ [_eid {:keys [ticker resp]}]]
   (timbre/debug _eid ticker)
   (let [tickers (m/search (->clj resp)
                   {:data {:quotes (m/scan {:symbol    (m/app (comp keyword str/lower) ?symbol)
                                            :shortname (m/app str/lower ?name)
                                            :quoteType "EQUITY"})}}
                   [?symbol ?name])]
     {:fx [[:commit [:app/cache [[:dx/delete [:db/id :yahoo/web] :searching-failure?]
                                 [:dx/delete [:db/id :yahoo/web] :searching-ticker?]
                                 [:dx/put    [:db/id :yahoo/web] :search-ticker-result (mapv first tickers)]]]]]})))

(rf/reg-event-fx
 ::search-ticker-failure
 (fn [_ [_eid {:keys [ticker] :as m}]]
   (timbre/error _eid ticker)
   {:fx [[:commit [:app/cache [[:dx/put    [:db/id :yahoo/web] :searching-failure? true]
                               [:dx/delete [:db/id :yahoo/web] :searching-ticker?]
                               [:dx/delete [:db/id :yahoo/web] :search-ticker-result]]]]
         [:dispatch [::fetch-crumb (update m :ticker keyword)]]]}))

;;
;; * trading
;;

(def htmlparser  (js/require "node-html-parser"))

(defn- str-time->epoch [s]
  (quot (js/Date. s) 1000))

(rf/reg-event-fx
 ::toggle-download-ticker
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes])]
 (fn [{:keys [quotes]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (let [tickers (keys (get-in quotes [:db/id]))]
     (if-not (enc/rsome #{ticker} tickers)
       {:fx [[:commit   [:app/cache    [:dx/put [:db/id ticker] :fetching? true]]]
             [:commit   [:yahoo/quotes [:dx/put [:db/id ticker] {}]]]
             [:dispatch [::fetch-header-info  m]]
             [:dispatch [::fetch-stats        m]]
             [:dispatch [::fetch-company-info m]]
             [:dispatch [::fetch-quotes       m]]]}
       {:fx [[:commit [:yahoo/db     [[:dx/delete [:db/id :settings] :download-ticker ticker]
                                      [:dx/delete [:db/id ticker]]]
                       :yahoo/quotes [[:dx/delete [:db/id ticker]]]]]
             [:freeze-store :yahoo/db]]}))))

(comment
  (rf/dispatch [::toggle-download-ticker :goog])
  (dx/commit {} [:dx/put [:db/id :goog] {}])
  (dx/with-dx! [quotes_ :yahoo/quotes]
    (tap> @quotes_))
  )

(rf/reg-event-fx
 ::fetch-crumb
 (fn [_ [_eid {:keys [ticker on-success on-failure] :as m}]]
   (enc/have! keyword? ticker)
   (timbre/debug _eid ticker)
   (let [url "https://finance.yahoo.com/quote/%s/history"]
     {:fx [[:commit [:app/cache [:dx/put [:db/id ticker] :featching? true]]]
           [:axios  {:method     :get
                     :url        (enc/format url (str/upper (name ticker)))
                     :on-success [[::fetch-crumb-success m] on-success]
                     :on-failure [[::fetch-crumb-failure m] on-failure]}]]})))

(rf/reg-event-fx
 ::fetch-crumb-success
 (fn [_ [_eid {:keys [ticker ^js resp] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! resp)
   (enc/when-let [body  (some-> resp .-data)
                  crumb (second (re-find #"\"CrumbStore\":\{\"crumb\":\"(.+?)\"\}" body))]
     (if crumb
       {:fx [[:commit       [:app/cache [[:dx/delete [:db/id ticker] :fetching?]
                                         [:dx/put    [:db/id ticker] :crumb crumb]]]]
             [:commit-later [(enc/ms :mins 1) [_eid ticker] :app/cache
                             [:dx/delete  [:db/id ticker] :crumb crumb]]]]}
       {:fx [[:dispatch [::fetch-crumb-failure m]]]}))))

(rf/reg-event-fx
 ::fetch-crumb-failure
 (fn [_ [_eid {:keys [ticker]}]]
   (timbre/error _eid ticker)
   {:fx [[:commit [:app/cache [:dx/delete [:db/id ticker] :featching?]]]]}))

(comment
  (rf/dispatch [::fetch-crumb {:ticker :msft}])
  (dx/with-dx! [cache_ :app/cache]
    @cache_)

  (rf/dispatch [::fetch-quotes {:ticker :msft}])
  (dx/with-dx! [quotes_ :yahoo/quotes]
    (q/quotes @quotes_ :msft :mn))
  )

(rf/reg-event-fx
 ::fetch-quotes
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes :cache :app/cache])]
 (fn [{:keys [quotes cache]} [_eid {:keys [ticker start-time end-time on-success on-failure] :as m}]]
   (timbre/debug _eid ticker :start-time start-time :end-time end-time)
   (enc/cond!
     :let [last-time   (some-> (q/quotes quotes ticker :d1) (q/time -1))
           start-time  (or start-time last-time (-> (js/Date.) (dtf/startOfYear) (dtf/subYears 5) (dtf/format "yyyy-MM-dd")))
           end-time    (or end-time (dtf/format (js/Date.) "yyyy-MM-dd"))
           crumb       (get-in cache [:db/id ticker :crumb])
           url         "https://query1.finance.yahoo.com/v7/finance/download/%s?period1=%s&period2=%s&interval=%s&events=%s&crumb=%s"
           start-time' (str-time->epoch start-time)
           end-time'   (str-time->epoch end-time)
           url         (enc/format url (str/upper (name ticker)) start-time' end-time' "1d" "history" crumb)]
     (some? crumb)
     {:fx [[:commit [:app/cache [:dx/put [:db/id ticker] :fetching? true]]]
           [:axios  {:method     :get
                     :url        url
                     :on-success [[::fetch-quotes-success m] on-success]
                     :on-failure [[::fetch-quotes-failure m] on-failure]}]]}
     (nil? crumb)
     {:fx [[:dispatch [::fetch-crumb (assoc m :on-success [::fetch-quotes m])]]]})))

(rf/reg-event-fx
 ::fetch-quotes-success
 (fn [_ [_eid {:keys [ticker ^js resp] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/have! resp)
   (enc/when-let [data (not-empty
                        (=>> (str/lines (.-data resp))
                             (map (fn [line] (str/split line ",")))
                             (drop 1)
                             (keep (fn [[t o h l c _ v]]
                                     (let [dt (dtf/formatISO (dtf-tz/zonedTimeToUtc t))]
                                       {:time   dt
                                        :open   (enc/as-?float o)
                                        :high   (enc/as-?float h)
                                        :low    (enc/as-?float l)
                                        :close  (enc/as-?float c)
                                        :volume (enc/as-?int v)})))
                             (filter schema/candle-bar?)))]
     {:fx [[:commit [:app/cache [:dx/delete [:db/id ticker] :fetching?]]]
           [:commit [:yahoo/quotes [[:dx/update [:db/id ticker] :data
                                     (fn [v]
                                       (into (or v []) (enc/xdistinct :time) data))]]]]
           [:dispatch [::quant.evt/refresh-quant m]]
           [:freeze-store :yahoo/quotes]]})))

(comment
  (rf/dispatch [::fetch-quotes {:ticker :amzn}])
  )

(rf/reg-event-fx
 ::fetch-quotes-failure
 (fn [_ [_eid {:keys [ticker]}]]
   (timbre/error _eid ticker)
   (enc/have! keyword? ticker)
   {:fx [[:commit [:app/cache [[:dx/delete [:db/id ticker] :fetching?]
                               [:dx/put [:db/id ticker] :error? true]]]]]}))

(rf/reg-event-fx
 ::refresh-quotes
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes])]
 (fn [{:keys [quotes]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (if (not ticker)
     ;; all
     (let [tickers (keys (quotes :db/id))]
       {:fx (into []
                  (mapcat (fn [ticker]
                            [[:dispatch [::fetch-header-info  {:ticker ticker}]]
                             [:dispatch [::fetch-stats        {:ticker ticker}]]
                             [:dispatch [::fetch-company-info {:ticker ticker}]]
                             [:dispatch [::fetch-quotes       {:ticker ticker}]]]))
                  tickers)})
     {:fx [[:dispatch [::quant.evt/refresh-quants m]]
           [:dispatch [::fetch-header-info        m]]
           [:dispatch [::fetch-stats              m]]
           [:dispatch [::fetch-company-info       m]]
           [:dispatch [::fetch-quotes             m]]]})))

(comment
  (rf/dispatch [::fetch-quotes {:ticker :aapl}])
  )

(rf/reg-event-fx
 ::fetch-quote-page
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker on-success on-failure] :as m}]]
   (timbre/debug _eid ticker)
   (enc/cond
     :let [url   (str "https://finance.yahoo.com/quote/" (str/upper (name ticker)))
           htree (get-in cache [:db/id ticker :quote-page])]
     (not htree)
     {:fx [[:axios {:method     :get
                    :url        url
                    :on-success [[::fetch-quote-page-success m] on-success]
                    :on-failure [[::fetch-quote-page-failure m] on-failure]}]]})))

(rf/reg-event-fx
 ::fetch-quote-page-success
 (fn [_ [_eid {:keys [ticker ^js resp] :as m}]]
   (timbre/debug _eid ticker)
   (when-let [v (some->> resp .-data (.parse htmlparser))]
     {:fx [[:commit [:app/cache [:dx/put [:db/id ticker] :quote-page v]]]
           [:commit-later [(enc/ms :mins 15) [_eid ticker] :app/cache
                           [:dx/delete [:db/id ticker] :quote-page]]]
           [:dispatch-later [(enc/ms :mins 15) [_eid :dispatch ticker]
                             [::fetch-quote-page m]]]]})))

(rf/reg-event-fx
 ::fetch-quote-page-failure
 (fn [_ [_eid ticker]]
   (timbre/error _eid ticker)
   (enc/do-nil (enc/have! keyword? ticker))))

(rf/reg-event-fx
 ::fetch-header-info
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (enc/cond!
     :let [htree (get-in cache [:db/id ticker :quote-page])]

     (some? htree)
     (let [price (-> htree (.querySelector "#quote-header-info .Trsdu\\(0\\.3s\\)") .-textContent
                     (str/replace "," ""))
           [_ diff-value diff-percentage]
           (->> (.querySelector htree "#quote-header-info .Trsdu\\(0\\.3s\\):nth-of-type(2)") .-textContent
                (re-find #"([+-]?\d+[,\d+]*.\d+) \(([+-]?\d+[,\d+]*.\d+)%\)"))
           m     {:price           (enc/as-?float price)
                  :diff-value      (enc/as-?float diff-value)
                  :diff-percentage (enc/as-?float diff-percentage)}]
       {:fx [[:commit [:yahoo/db [:dx/put [:db/id ticker] m]]]
             [:freeze-store :yahoo/db]]})

     (nil? htree)
     {:fx [[:dispatch [::fetch-quote-page (assoc m :on-success [::fetch-header-info m])]]]})))

(comment
  (re-find #"([+-]?\d+[,\d+]*.\d+) \(([+-]?\d+[,\d+]*.\d+)%\)" ""))

(def ^:private unit->value
  {"m" 1e6
   "b" 1e9
   "t" 1e12
   "%" 0.01})

(defn- ->value [s]
  (when (string? s)
    (let [num  (->> (str/replace s #"," "") (re-find #"([0-9]*.[0-9]+|[0-9]+)") first enc/as-?float)
          unit (str (last s))]
      (when num
        (* num (unit->value (str/lower unit) 1.0))))))

(defn- ->date [s]
  (when (string? s)
    (dtf/format (dtf/parse s "LLL dd, yyyy" (js/Date.)) "yyyy-MM-dd")))


(rf/reg-event-fx
 ::fetch-key-stats-page
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker on-success on-failure] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/cond
     :let [url   (enc/format "https://finance.yahoo.com/quote/%s/key-statistics" (str/upper (name ticker)))
           htree (get-in cache [:db/id :key-stats-page ticker])]
     (not htree)
     {:fx [[:axios {:method     :get
                    :url        url
                    :on-success [[::fetch-key-stats-page-success m] on-success]
                    :on-failure [[::fetch-key-stats-page-failure m] on-failure]}]]})))

(rf/reg-event-fx
 ::fetch-key-stats-page-success
 (fn [_ [_eid {:keys [ticker ^js resp]}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/have! resp)
   (let [v (->> (.-data resp) (.parse htmlparser))]
     {:fx [[:commit       [:app/cache [:dx/put    [:db/id ticker] :key-stats-page v]]]
           [:commit-later [(enc/ms :mins 15) [_eid ticker]
                           :app/cache [:dx/delete [:db/id ticker] :key-stats-page]]]]})))

(rf/reg-event-fx
 ::fetch-key-stats-page-failure
 (fn [_ [_eid {:keys [ticker]}]]
   (timbre/error _eid ticker)))

(defn- find-value-by-description [^js htree s]
  (-> htree
      (.querySelector (enc/format "td:icontains('%s') + td" s))
      .-textContent))

(defn parse-stats [htree]
  {:market-cap                          (enc/catching (-> (find-value-by-description htree "market cap")                     ->value))
   :enterprise-value                    (enc/catching (-> (find-value-by-description htree "enterprise value")               ->value))
   :trailing-pe-ratio                   (enc/catching (-> (find-value-by-description htree "trailing p/e")                   ->value))
   :forward-pe-ratio                    (enc/catching (-> (find-value-by-description htree "forward p/e")                    ->value))
   :peg-ratio                           (enc/catching (-> (find-value-by-description htree "peg ratio")                      ->value))
   :price-to-sales-ratio                (enc/catching (-> (find-value-by-description htree "price/sales")                    ->value))
   :price-to-book-ratio                 (enc/catching (-> (find-value-by-description htree "price/book")                     ->value))
   :enterprise-value-to-revenue-ratio   (enc/catching (-> (find-value-by-description htree "enterprise value/revenue")       ->value))
   :enterprise-value-to-ebitda-ratio    (enc/catching (-> (find-value-by-description htree "enterprise value/ebitda")        ->value))
   :fiscal-year-ends                    (enc/catching (-> (find-value-by-description htree "fiscal year ends")               ->date))
   :most-recent-quarter                 (enc/catching (-> (find-value-by-description htree "most recent quarter")            ->date))
   :profit-margin                       (enc/catching (-> (find-value-by-description htree "profit margin")                  ->value))
   :operating-margin                    (enc/catching (-> (find-value-by-description htree "operating margin")               ->value))
   :roa                                 (enc/catching (-> (find-value-by-description htree "return on assets")               ->value))
   :roe                                 (enc/catching (-> (find-value-by-description htree "return on equity")               ->value))
   :revenue                             (enc/catching (-> (find-value-by-description htree "revenue")                        ->value))
   :revenue-per-share                   (enc/catching (-> (find-value-by-description htree "revenue per share")              ->value))
   :quarterly-revenue-growth            (enc/catching (-> (find-value-by-description htree "quarterly revenue growth")       ->value))
   :gross-profit                        (enc/catching (-> (find-value-by-description htree "gross profit")                   ->value))
   :ebitda                              (enc/catching (-> (find-value-by-description htree "ebitda")                         ->value))
   :net-income-avi-to-common            (enc/catching (-> (find-value-by-description htree "net income avi to common")       ->value))
   :diluted-eps                         (enc/catching (-> (find-value-by-description htree "diluted eps")                    ->value))
   :total-cash                          (enc/catching (-> (find-value-by-description htree "total cash")                     ->value))
   :quarterly-earnings-growth           (enc/catching (-> (find-value-by-description htree "quarterly earnings growth")      ->value))
   :total-cash-per-share                (enc/catching (-> (find-value-by-description htree "total cash per share")           ->value))
   :total-debt                          (enc/catching (-> (find-value-by-description htree "total debt")                     ->value))
   :total-debt-to-equity                (enc/catching (-> (find-value-by-description htree "total debt/equity")              ->value))
   :current-ratio                       (enc/catching (-> (find-value-by-description htree "current ratio")                  ->value))
   :book-value-per-share                (enc/catching (-> (find-value-by-description htree "book value per share")           ->value))
   :operating-cash-flow                 (enc/catching (-> (find-value-by-description htree "operating cash flow")            ->value))
   :levered-free-cash-flow              (enc/catching (-> (find-value-by-description htree "levered free cash flow")         ->value))
   :beta                                (enc/catching (-> (find-value-by-description htree "beta (5y monthly)")              ->value))
   :week-change-52                      (enc/catching (-> (find-value-by-description htree "52-week change")                 ->value))
   :s&p500-week-change-52               (enc/catching (-> (find-value-by-description htree "s&p500 52-week change")          ->value))
   :week-high-52                        (enc/catching (-> (find-value-by-description htree "52 week high")                   ->value))
   :week-low-52                         (enc/catching (-> (find-value-by-description htree "52 week low")                    ->value))
   :day-ma-50                           (enc/catching (-> (find-value-by-description htree "50-day moving average")          ->value))
   :day-ma-200                          (enc/catching (-> (find-value-by-description htree "200-day moving average")         ->value))
   :avg-vol-3-month                     (enc/catching (-> (find-value-by-description htree "avg vol (3 month)")              ->value))
   :avg-vol-10-days                     (enc/catching (-> (find-value-by-description htree "avg vol (10 day)")               ->value))
   :shares-outstanding                  (enc/catching (-> (find-value-by-description htree "shares outstanding")             ->value))
   :float                               (enc/catching (-> (find-value-by-description htree "float")                          ->value))
   :percent-held-by-insiders            (enc/catching (-> (find-value-by-description htree "% held by insiders")             ->value))
   :percent-held-by-institutions        (enc/catching (-> (find-value-by-description htree "% held by institutions")         ->value))
   :shares-short                        (enc/catching (-> (find-value-by-description htree "shares short")                   ->value))
   :short-ratio                         (enc/catching (-> (find-value-by-description htree "short ratio")                    ->value))
   :short-percent-of-float              (enc/catching (-> (find-value-by-description htree "short % of float")               ->value))
   :short-percent-of-shares-outstanding (enc/catching (-> (find-value-by-description htree "short % of shares outstanding")  ->value))
   :forward-annual-dividend-rate        (enc/catching (-> (find-value-by-description htree "forward annual dividend rate")   ->value))
   :forward-annual-dividend-yield       (enc/catching (-> (find-value-by-description htree "forward annual dividend yield")  ->value))
   :trailing-annual-dividend-rate       (enc/catching (-> (find-value-by-description htree "trailing annual dividend rate")  ->value))
   :trailing-annual-dividend-yield      (enc/catching (-> (find-value-by-description htree "trailing annual dividend yield") ->value))
   :five-year-average-dividend-yield    (enc/catching (-> (find-value-by-description htree "5 year average dividend yield")  ->value))
   :payout-ratio                        (enc/catching (-> (find-value-by-description htree "payout ratio")                   ->value))
   :dividend-date                       (enc/catching (-> (find-value-by-description htree "dividend date")                  ->date))
   :ex-dividend-date                    (enc/catching (-> (find-value-by-description htree "ex-dividend date")               ->date))})

(rf/reg-event-fx
 ::fetch-stats
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/cond!
     :let [htree (get-in cache [:db/id ticker :key-stats-page])]

     (some? htree)
     {:fx [[:commit [:yahoo/db [:dx/put [:db/id ticker] (parse-stats htree)]]]
           [:freeze-store :yahoo/db]]}

     (nil? htree)
     {:fx [[:dispatch [::fetch-key-stats-page (assoc m :on-success [::fetch-stats m])]]]})))

(comment
  (dx/with-dx! [db_ :yahoo/db]
    (tap> @db_)))

(rf/reg-event-fx
 ::fetch-profile-page
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker on-success on-failure] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/cond
     :let [url   (enc/format "https://finance.yahoo.com/quote/%s/profile" (str/upper (name ticker)))
           htree (get-in cache [:db/id ticker :profile-page])]
     (not htree)
     {:fx [[:axios {:method     :get
                    :url        url
                    :on-success [[::fetch-profile-page-success m] on-success]
                    :on-failure [[::fetch-profile-page-failure m] on-failure]}]]})))

(rf/reg-event-fx
 ::fetch-profile-page-success
 (fn [_ [_eid {:keys [ticker ^js resp]}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/have! resp)
   (let [v (->> (.-data resp)
                (.parse htmlparser))]
     {:fx [[:commit       [:app/cache [:dx/put    [:db/id ticker] :profile-page v]]]
           [:commit-later [(enc/ms :mins 15) [_eid ticker]
                           :app/cache [:dx/delete [:db/id ticker] :profile-page]]]]})))

(rf/reg-event-fx
 ::fetch-profile-page-failure
 (fn [_ [_eid {:keys [ticker]}]]
   (timbre/debug _eid ticker)
   (enc/do-nil (enc/have! keyword? ticker))))

(defn parse-company-info [htree]
  {:full-name (some-> htree (.querySelector "[data-test='asset-profile'] h3") .-textContent str/lower)
   :street    (some-> htree (.querySelector "[data-test='asset-profile'] div p") .-innerHTML (str/split #"<.+?>")
                      ((fn [{street 0}] (some-> street str/lower str/trim))))
   :city      (some-> htree (.querySelector "[data-test='asset-profile'] div p") .-innerHTML (str/split #"<.+?>")
                      ((fn [{city 1}] (some-> city str/lower str/trim))))
   :country   (some-> htree (.querySelector "[data-test='asset-profile'] div p") .-innerHTML (str/split #"<.+?>")
                      ((fn [{country 2}] (some-> country str/lower str/trim))))
   :sector    (some-> htree (.querySelector "[data-test='asset-profile'] div p:nth-of-type(2)")
                      .-innerHTML (str/split #"<.+?>")
                      ((fn [{sector 3}] (some-> sector str/lower str/trim))))
   :industry  (some-> htree (.querySelector "[data-test='asset-profile'] div p:nth-of-type(2)")
                      .-innerHTML (str/split #"<.+?>")
                      ((fn [{industry 8}] (some-> industry str/lower str/trim))))
   :employees (some-> htree (.querySelector "[data-test='asset-profile'] div p:nth-of-type(2)")
                      .-innerHTML (str/split #"<.+?>")
                      ((fn [{employees 14}] (some-> employees str/lower str/trim ->value))))})

(rf/reg-event-fx
 ::fetch-company-info
 [(rf/inject-cofx ::dx/with-dx! [:cache :app/cache])]
 (fn [{:keys [cache]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
   (enc/have! keyword? ticker)
   (enc/cond!
     :let [htree (get-in cache [:db/id ticker :profile-page])]

     (some? htree)
     {:fx [[:commit [:yahoo/db [:dx/put [:db/id ticker] (parse-company-info htree)]]]
           [:freeze-store :yahoo/db]]}

     (nil? htree)
     {:fx [[:dispatch [::fetch-profile-page (assoc m :on-success [::fetch-company-info m])]]]})))
