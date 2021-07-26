(ns matterhorn.cofx
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [taoensso.encore :as enc]
   [ribelo.doxa :as dx]))

(rf/reg-cofx
 ::dx/with-dx!
 (fn [coeffects vks]
   (binding [dx/*empty-map* (hash-map)
             dx/*atom-fn*   r/atom]
     (enc/have! even? (count vks))
     (enc/reduce-kvs
      (fn [acc v k]
        (assoc acc v @(dx/get-dx! k)))
      coeffects
      vks))))
