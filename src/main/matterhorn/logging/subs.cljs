(ns matterhorn.logging.subs
  (:require
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [meander.epsilon :as m]
   [ribelo.doxa :as dx]
   ["date-fns" :as dt]))

(rf/reg-sub
 ::data
 (fn [db _]
   (get-in db [:db/id :log :data] [])))

(rf/reg-sub
 ::last-event
 (fn [db _]
   (get-in db [:db/id :log :last-event])))
