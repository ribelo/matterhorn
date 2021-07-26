(ns matterhorn.quant.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]
   [ribelo.qualsdorf :as quant]
   [ribelo.kemnath :as math]
   [matterhorn.quotes.api :as q]))

(rf/reg-event-fx
 ::refresh-quant
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes :settings :app/settings])]
 (fn [{:keys [settings quotes]} [_ {:keys [ticker] :as m}]]
   (enc/when-let [{:keys [money risk frisk tf freq max-assets]}
                  (get-in settings [:db/id :settings])
                  data  (q/quotes quotes ticker (or tf :mn))
                  freq  (math/min freq (count data))
                  close (q/close data (- freq) -1)
                  ret   (quant/tick->ret close)
                  stat  {:annualized-return       (quant/annualized-return freq ret)
                         :annualized-risk         (quant/annualized-risk freq ret)
                         :annualized-sharpe-ratio (quant/annualized-sharpe-ratio frisk freq ret)
                         :continous-drawdown      (quant/continuous-drawdown ret)
                         :average-drawndown       (quant/average-drawdown ret)
                         :maximum-drawndown       (quant/maximum-drawdown ret)
                         :calmar-ratio            (quant/calmar-ratio frisk ret)
                         :hist-var                (quant/hist-var ret)
                         :ror                     (quant/rate-of-return ret)
                         :cagr                    (quant/cagr ret)
                         :redp                    (quant/rolling-economic-drawndown close)
                         :allocation              (quant/redp-single-allocation frisk frisk close)}]
     {:fx [[:commit [:yahoo/db [:dx/put [:db/id ticker] stat]]]
           [:freeze-store :yahoo/db]]})))

(rf/reg-event-fx
 ::refresh-quants
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes])]
 (fn [{:keys [quotes]} [_eid]]
   (timbre/debug _eid)
   (let [tickers (keys (quotes :db/id))]
     {:fx (mapv (fn [ticker] [:dispatch [::refresh-quant ticker]]) tickers)})))


(comment
  (dx/with-dx! [quotes_ :yahoo/quotes]
    (tap> @quotes_))
  (rf/dispatch [::refresh-quant :msft])
  )
