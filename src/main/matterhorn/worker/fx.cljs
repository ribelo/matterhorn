(ns matterhorn.worker.fx
  (:require
   [meander.epsilon :as m]
   [re-frame.core :as rf]
   [promesa.core :as p]
   [ribelo.doxa :as dx]
   [matterhorn.worker.core :as worker]
   [matterhorn.market.payments.api :as payments.api]))

(def ^:private worker-pool (worker/worker-pool "js/worker.js" 2))

(comment
  (-> (worker/dispatch! worker-pool '(+ 1 1))
      (p/then (fn [x] (println :x x)))
      (p/catch (fn [x] (println :err x)))))

(rf/reg-fx
 :worker
 (fn [events]
   (m/match events
     [(m/cata !events) ...]
     !events
     ;;
     {:event ?event
      :on-success ?on-success
      :on-failure ?on-failure}
     (cond-> (worker/dispatch! worker-pool ?event)
       ?on-success
       (p/then (fn [data]
                 (m/match ?on-success
                   (m/pred fn? ?f)
                   (?f data)
                   ;;
                   [(m/pred keyword?) & _ :as ?evt]
                   (rf/dispatch ?evt))))
       ?on-failure
       (p/catch (fn [data]
                  (m/match ?on-failure
                    (m/pred fn? ?f)
                    (?f data)
                    ;;
                    [(m/pred keyword?) & _ :as ?evt]
                    (rf/dispatch ?evt))))))))

(rf/reg-event-fx
 ::test-worker
 (fn [_ [_ _event]]
   {:worker [{:event [::payments.api/read-file "/home/ribelo/Public/rk/payments/f01752.txt"]
              :on-success (fn [data]
                            (println :success)
                            (rf/dispatch [:commit :market [:dx/put data]]))}]}))
(comment
  (rf/dispatch [::test-worker nil])

  (dx/with-dx! [dx_ :market]
    dx_)

  (dx/listen! ))
