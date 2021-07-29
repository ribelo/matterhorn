(ns matterhorn.quant.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]
   [ribelo.kemnath :as math]
   [ribelo.stade :as stats]
   [ribelo.qualsdorf :as quant]
   [matterhorn.quotes.api :as q]))

(rf/reg-event-fx
 ::refresh-quant
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes :settings :app/settings])]
 (fn [{:keys [settings quotes]} [_eid {:keys [ticker] :as m}]]
   (timbre/debug _eid ticker)
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
                         :std3                    (* 3 (stats/std ret))
                         :allocation              (quant/redp-single-allocation frisk risk close)}]
     {:fx [[:commit [:yahoo/db [:dx/put [:db/id ticker] stat]]]
           [:freeze-store :yahoo/db]]})))

(rf/reg-event-fx
 ::refresh-quants
 [(rf/inject-cofx ::dx/with-dx! [:quotes :yahoo/quotes])]
 (fn [{:keys [quotes]} [_eid]]
   (timbre/debug _eid)
   (let [tickers (keys (quotes :db/id))]
     {:fx (mapv (fn [ticker] [:dispatch [::refresh-quant {:ticker ticker}]]) tickers)})))


(comment
  (dx/with-dx! [quotes_ :yahoo/quotes]
    (let [data (-> (q/quotes @quotes_ :msft :mn)
                   (q/close -12 -1))]
      (quant/redp-single-allocation 0.0 0.3 data)))
  )
