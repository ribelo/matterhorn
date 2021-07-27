(ns matterhorn.provider.cboe.util
  (:require
   [taoensso.encore :as enc]
   [meander.epsilon :as m]
   [lambdaisland.regal :as regal]
   [cuerdas.core :as str]))

(defn option-str->data [s]
  (let [rgx (regal/regex
             [:cat
              :start
              [:capture [:+ [:class [\A \Z]]]]
              [:capture [:repeat :digit 2]]
              [:capture [:repeat :digit 2]]
              [:capture [:repeat :digit 2]]
              [:capture [:class [\A \Z]]]
              [:capture [:+ :digit]]
              :end])]
    (m/match (re-matches rgx s)
      [_ ?ticker ?year ?month ?day ?type ?strike]
      (let [ticker (keyword (str/lower ?ticker))
            exp    (enc/format "20%s-%s-%s" ?year ?month ?day)
            tp     (case ?type "C" :call "P" :put)
            strike (enc/parse-int ?strike)]
        {(enc/merge-keywords [ticker :id]) [ticker exp tp strike]
         :ticker ticker
         :exp    exp
         :type   tp
         :strike strike}))))
