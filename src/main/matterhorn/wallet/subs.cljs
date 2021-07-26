(ns matterhorn.wallet.subs
  (:require
   [taoensso.encore :as enc]
   [meander.epsilon :as m]
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [ribelo.doxa :as dx]))

(rf/reg-sub-raw
 ::tickers
 (fn [_ _]
   (dx/with-dx! [db_ :yahoo/db]
     (ra/reaction
      (m/search @db_
        {:db/id {?ticker {:in-wallet? true}}}
        ?ticker)))))

(rf/reg-sub-raw
 ::in-wallet?
 (fn [_ [_ ticker]]
   (dx/with-dx! [db_ :yahoo/db]
     (ra/reaction
      (m/find @db_
        {:db/id {~ticker {:in-wallet? true}}}
        true
        _ false)))))

(rf/reg-sub-raw
 ::wallet
 (fn [_ _]
   (dx/with-dx! [db_ :yahoo/db]
     (ra/reaction
      (->> (m/search @db_
             {:db/id {?ticker {:wallet-percentage (m/pred pos? ?p)
                               :wallet-money      (m/pred pos? ?m)}}}
             [?ticker ?p ?m])
           (sort-by second enc/rcompare))))))
