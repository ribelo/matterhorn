(ns matterhorn.transit
  (:require
   [cognitect.transit :as t]
   [cljs-bean.transit :as bt]))

(def write-handlers
  (bt/writer-handlers))

(defn write-transit [s]
  (t/write (t/writer :json {:handlers write-handlers :transform t/write-meta}) s))

(defn read-transit [s]
  (t/read (t/reader :json) s))
