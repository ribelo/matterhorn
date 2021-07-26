(ns matterhorn.axios.fx
  (:require
   [cljs.core.async :as a :refer [<! go go-loop]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [meander.epsilon :as m]
   [re-frame.core :as rf]))

(def axios (js/require "axios"))

(defn <http
  [{:keys [method url]}]
  (go (case method
        :get  (<p! (-> (.get axios url) (.catch (fn [_err])))))))


(defn flatten-dispatch [x]
  (m/rewrite x
    (m/with [%a [(m/pred keyword?) . (m/pred map?) ... :as !vs]
             %b (m/or [(m/or %a %b) ...] %a nil)]
            %b)
    [!vs ...]
    _
    ~(timbre/error :flatte-dispatch x)))

(rf/reg-fx
 :axios
 (let [limiter (enc/limiter {:3s [5 5000]})]
   (fn [{:keys [method url on-success on-failure] :as m}]
     (go-loop []
       (enc/cond
         (limiter)
         (do (<! (a/timeout 1000)) (recur))

         :let [resp    (<! (<http m))
               code (some-> resp .-status)]

         (== 200 code)
         (enc/cond!
           (fn? on-success)
           (on-success resp)

           (vector? on-success)
           (doseq [[k m] (flatten-dispatch on-success)]
             (rf/dispatch [k (assoc m :resp resp)])))

         :else
         (do (timbre/warnf "request: %s code: %s" url code)
             (enc/cond!
               (fn? on-failure)
               (on-failure resp)

               (vector? on-failure)
               (doseq [[k m] (flatten-dispatch on-failure)]
                 (rf/dispatch [k (assoc m :resp resp)])))))))))
