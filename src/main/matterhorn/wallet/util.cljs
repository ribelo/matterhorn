(ns matterhorn.wallet.util
  (:require
   [taoensso.encore :as enc]
   [net.cgrand.xforms :as x]
   [ribelo.kemnath :as math]
   [ribelo.qualsdorf :as quant]
   [ribelo.danzig :as dz :refer [=>>]]))

(defn add-percentage-allocations [frisk risk assets]
  (let [data    (=>> assets (map :data))
        alloc   (quant/redp-multiple-allocation frisk risk data)]
    (=>> (mapv vector assets alloc)
         (keep (fn [[asset alloc]]
                 (when (pos? alloc)
                   (assoc asset :allocation alloc)))))))

(defn add-money-allocations [money assets]
  (=>> assets
       (map
        (fn [{:keys [allocation price] :as m}]
          (let [v     (math/round2 (* money allocation))
                n     (math/floor (/ v price))
                v'    (math/round2 (* n price))
                alloc (math/round2 (/ v' money))]
            (into m {:allocation alloc :money v' :cnt n}))))))

(defn drop-worse-allocation [assets]
  (=>> assets
       (x/sort-by :allocation)
       (drop 1)))
