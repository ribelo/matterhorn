(ns matterhorn.settings.events
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [ribelo.kemnath :as math]
   [ribelo.doxa :as dx]
   [matterhorn.quant.events :as q.evt]
   [matterhorn.wallet.events :as wall.evt]))

(rf/reg-event-fx
 ::update-money
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid x]]
   (timbre/debug _eid (math/round2 x))
   (let [money (get-in settings [:db/id :settings :money])]
     (when (>= (+ money x) 0)
       {:fx [[:commit [:app/settings [:dx/update [:db/id :settings] :money (comp math/round +) x]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch] [::wall.evt/calc-allocations]]]
             [:freeze-store :app/settings]]}))))

(rf/reg-event-fx
 ::update-risk
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid x]]
   (timbre/debug _eid (math/round2 x))
   (let [risk (get-in settings [:db/id :settings :risk])]
     (when (>= (+ risk x) 0)
       {:fx [[:commit [:app/settings [:dx/update [:db/id :settings] :risk (comp math/round2 +) x]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :quats] [::q.evt/refresh-quants]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :wallet] [::wall.evt/calc-allocations]]]
             [:freeze-store :app/settings]]}))))

(rf/reg-event-fx
 ::update-frisk
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid x]]
   (timbre/debug _eid (math/round2 x))
   (let [frisk (get-in settings [:db/id :settings :frisk])]
     (when (>= (+ frisk x) -0.05)
       {:fx [[:commit [:app/settings [:dx/update [:db/id :settings] :frisk (comp math/round2 +) x]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :quants] [::q.evt/refresh-quants]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :wallet] [::wall.evt/calc-allocations]]]
             [:freeze-store :app/settings]]}))))

(rf/reg-event-fx
 ::change-tf
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid]]
   (timbre/debug _eid)
   (let [tf (get-in settings [:db/id :settings :tf])]
     {:fx [(case tf
             (nil :mn) [:commit [:app/settings [[:dx/put    [:db/id :settings] :tf :d1]
                                                [:dx/update [:db/id :settings] :freq
                                                 (fn [freq] (* freq 21))]]]]
             :d1       [:commit [:app/settings [[:dx/put    [:db/id :settings] :tf :mn]
                                                [:dx/update [:db/id :settings] :freq
                                                 (fn [freq] (math/max 1 (math/floor (/ freq 21))))]]]])
           [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch] [::wall.evt/calc-allocations]]]
           [:freeze-store :app/settings]]})))

(rf/reg-event-fx
 ::update-freq
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid x]]
   (timbre/debug _eid (math/round2 x))
   (let [freq (get-in settings [:db/id :settings :freq])]
     (when (>= (+ freq x) 1)
       {:fx [[:commit [:app/settings [:dx/update [:db/id :settings] :freq (comp math/round +) x]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch] [::wall.evt/calc-allocations]]]
             [:freeze-store :app/settings]]}))))

(rf/reg-event-fx
 ::update-max-assets
 [(rf/inject-cofx ::dx/with-dx! [:settings :app/settings])]
 (fn [{:keys [settings]} [_eid x]]
   (timbre/debug _eid (math/round2 x))
   (let [n (get-in settings [:db/id :settings :max-assets])]
     (when (>= (+ n x) 1)
       {:fx [[:commit [:app/settings [:dx/update [:db/id :settings] :max-assets (comp math/round +) x]]]
             [:dispatch-later [(enc/ms :ms 500) [_eid :dispatch] [::wall.evt/calc-allocations]]]
             [:freeze-store :app/settings]
             ]}))))
