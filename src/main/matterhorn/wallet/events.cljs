(ns matterhorn.wallet.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [meander.epsilon :as m]
   [re-frame.core :as rf]
   [net.cgrand.xforms :as x]
   [ribelo.doxa :as dx]
   [ribelo.danzig :as dz :refer [=>>]]
   [ribelo.qualsdorf :as quant]
   [ribelo.kemnath :as math]
   [matterhorn.quotes.api :as q]
   [matterhorn.wallet.util :as u]))

(rf/reg-event-fx
 ::toggle-ticker
 [(rf/inject-cofx ::dx/with-dx! [:yahoo :yahoo/db])]
 (fn [{:keys [yahoo]} [_eid {:keys [ticker]}]]
   (timbre/debug _eid ticker)
   (let [in-wallet? (get-in yahoo [:db/id ticker :in-wallet?])]
     (if-not in-wallet?
       {:fx [[:commit [:yahoo/db  [:dx/put [:db/id ticker] :in-wallet? true]]]
             [:dispatch [::calc-allocations]]]}
       {:fx [[:commit [:yahoo/db [[:dx/delete [:db/id ticker] :in-wallet?       ]
                                  [:dx/delete [:db/id ticker] :wallet-percentage]
                                  [:dx/delete [:db/id ticker] :wallet-money     ]
                                  [:dx/delete [:db/id ticker] :wallet-cnt       ]]]]
             [:dispatch [::calc-allocations]]]}))))

(rf/reg-event-fx
 ::calc-allocations
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings :quotes :yahoo/quotes :yahoo :yahoo/db])]
 (fn [{:keys [settings quotes yahoo]} [_eid]]
   (timbre/debug _eid)
   (let [{:keys [risk frisk freq tf money max-assets]}
         (get-in settings [:db/id :settings])
         all-tickers     (vec (m/search yahoo
                            {:db/id {?ticker {:in-wallet? true}}}
                            ?ticker))
         pricef      (fn [ticker] (get-in yahoo [:db/id ticker :price]))
         assets      (=>> all-tickers
                          (keep (fn [ticker]
                                  (when-let [data (not-empty (q/quotes quotes ticker tf))]
                                    {:ticker ticker
                                     :data   (q/close data (- (math/min freq (count data))) -1)
                                     :price  (pricef ticker)}))))
         allocations
         (loop [i 0 wallet assets]
           (when (< i 10)
             (tap> [:wallet i max-assets (u/add-percentage-allocations frisk risk wallet)])
             (let [wallet' (->> (u/add-percentage-allocations frisk risk wallet)
                                (u/add-money-allocations money))]
               (if (> (count wallet') max-assets)
                 (recur (inc i) (u/drop-worse-allocation wallet'))
                 wallet'))))]

     (when (seq allocations)
       {:fx [[:commit [:yahoo/db
                       (into []
                             (mapcat (fn [ticker]
                                       [[:dx/delete [:db/id ticker] :wallet-percentage]
                                        [:dx/delete [:db/id ticker] :wallet-money     ]
                                        [:dx/delete [:db/id ticker] :wallet-cnt       ]]))
                             all-tickers)]]
             [:commit [:yahoo/db
                       (into []
                             (mapcat (fn [{:keys [ticker allocation money cnt]}]
                                       [[:dx/put [:db/id ticker] :wallet-percentage allocation]
                                        [:dx/put [:db/id ticker] :wallet-money      money]
                                        [:dx/put [:db/id ticker] :wallet-cnt        cnt]]))
                             allocations)]]
             [:freeze-store :yahoo/db]]}))))


(comment
  (dx/with-dx! [db_ :yahoo/db settings_ :app/settings quotes_ :yahoo/quotes]
    ))
