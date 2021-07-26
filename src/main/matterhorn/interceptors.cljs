(ns matterhorn.interceptors
  (:require
   [re-frame.core :as rf]
   [re-frame.interceptor :as rfi]))

(defn after-dx [store f]
  (rf/->interceptor
   :id :after-dx
   :after (fn [context]
            (let [db (rfi/get-coeffect context store)]
              (f store db)
              context))))
