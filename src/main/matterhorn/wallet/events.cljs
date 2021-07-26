(ns matterhorn.wallet.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [meander.epsilon :as m]
   [re-frame.core :as rf]
   [net.cgrand.xforms :as x]
   [ribelo.doxa :as dx]
   [ribelo.qualsdorf :as quant]
   [matterhorn.quotes.api :as q]))

(rf/reg-event-fx
 ::toggle-ticker
 [(rf/inject-cofx ::dx/with-dx! [:yahoo :yahoo/db])]
 (fn [{:keys [yahoo]} [_eid {:keys [ticker]}]]
   (timbre/debug _eid ticker)
   (let [in-wallet? (get-in yahoo [:db/id ticker :in-wallet?])]
     (if-not in-wallet?
       {:fx [[:commit [:yahoo/db  [:dx/put [:db/id ticker] :in-wallet? true]]]
             [:dispatch [::calc-allocations]]]}
       {:fx [[:commit [:yahoo/db [[:dx/delete [:db/id ticker] :in-wallet?]
                                  [:dx/delete [:db/id ticker] :wallet-percentage]
                                  [:dx/delete [:db/id ticker] :wallet-money]]]]
             [:dispatch [::calc-allocations]]]}))))

(rf/reg-event-fx
 ::calc-allocations
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings :quotes :yahoo/quotes :yahoo :yahoo/db])]
 (fn [{:keys [settings quotes yahoo]} [_eid]]
   (timbre/debug _eid)
   (let [{:keys [risk frisk freq tf money]} (get-in settings [:db/id :settings])
         tickers                            (vec (m/search yahoo
                                                   {:db/id {?ticker {:in-wallet? true}}}
                                                   ?ticker))
         assets                             (into []
                                                  (comp
                                                   (map (fn [ticker] (q/quotes quotes ticker tf)))
                                                   (filter not-empty)
                                                   (map (fn [data]
                                                          (q/close data (- (min freq (count data))) -1))))
                                                  tickers)
         allocations                        (quant/redp-multiple-allocation frisk risk assets)]
     (when (seq allocations)
       {:fx [[:commit [:yahoo/db
                       (into []
                             (mapcat (fn [[ticker v]]
                                       [[:dx/put [:db/id ticker] :wallet-percentage v]
                                        [:dx/put [:db/id ticker] :wallet-money (* v money)]]))
                             (mapv vector tickers allocations))]]
             [:freeze-store :yahoo/db]]}))))


(comment
  (dx/with-dx! [db_ :yahoo/db settings_ :app/settings quotes_ :yahoo/quotes]
    ))
