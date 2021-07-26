(ns matterhorn.ipc
  (:require
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [matterhorn.transit :refer [write-transit read-transit]]
   [ribelo.doxa :as dx]))

;; (def electron    (js/require "electron"))
;; (def ipcRenderer (.-ipcRenderer electron))

;; (defmulti <-main first)

;; (defmethod <-main :timbre/debug
;;   [[_ msg]]
;;   (timbre/debug msg))

;; (defmethod <-main :timbre/info
;;   [[_ msg]]
;;   (timbre/info msg))

;; (defmethod <-main :timbre/warn
;;   [[_ msg]]
;;   (timbre/warn _ msg))

;; (defmethod <-main :timbre/error
;;   [[_ msg]]
;;   (timbre/error _ msg))

;; (defmethod <-main :re-frame/dispatch
;;   [[_ v]]
;;   (println :v v)
;;   (rf/dispatch v))

;; (defmethod <-main :app/version
;;   [[_ version]]
;;   (dx/with-dx! [dx_ :app]
;;     (println :version version)
;;     (dx/commit! dx_ [:dx/put [:db/id :app/info] :app/version version])))

;; (defn listen! []
;;   (.on ipcRenderer "reply"   (fn [^js e x]
;;                                (println :reply x (read-transit x))
;;                                (<-main (read-transit x))))
;;   (.on ipcRenderer "message" (fn [^js e x]
;;                                (println :message x (read-transit x))
;;                                (<-main (read-transit x)))))

;; (defn send! [v]
;;   (.send ^js ipcRenderer "message" (write-transit v)))

(comment
  (.send ipcRenderer "message" (write-transit [:app/version])))
