(ns matterhorn.schema
  (:require
   [taoensso.encore :as enc]
   [cuerdas.core :as str]
   [malli.core :as mc]
   [malli.registry :as mr]
   [malli.util :as mu]))

(def registry_
  (atom {}))

(defn register!
  [type schema]
  (enc/do-true
   (swap! registry_ assoc type schema)))

(mr/set-default-registry!
 (mr/composite-registry
  (mr/mutable-registry registry_)
  (merge
   (mc/predicate-schemas)
   (mc/class-schemas)
   (mc/comparator-schemas)
   (mc/type-schemas)
   (mc/sequence-schemas)
   (mc/base-schemas))))

(register! :date
           [:re #"^\d{4}-\d{2}-\d{2}$"])

(register! :datetime
           [:re #"\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d([+-][0-2]\d:[0-5]\d|Z)"])

(register! :candle/price
           [:and number? pos?])

(register! :candle/volume
           [:and number? pos?])

(register! :candle/tf
           [:enum :m1 :m2 :m3 :m4 :m5 :m10 :m15 :m30 :h1 :h4 :d1 :wn1 :mn])

(register! :quotes/symbol :keyword)

(register! :candle/bar
           [:map
            [:time   [:or :date :datetime]]
            [:open   :candle/price]
            [:high   :candle/price]
            [:low    :candle/price]
            [:close  :candle/price]
            [:volume {:optional true}
             :candle/volume]])

(register! :quotes/entity
           (mu/merge
            :candle/bar
            [:map
             [:period :candle/tf]
             [:symbol :quotes/symbol]]))

(def candle-bar? (mc/validator :candle/bar))
(def quotes-entity? (mc/validator :quotes/entity))
