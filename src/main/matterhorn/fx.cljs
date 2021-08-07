(ns matterhorn.fx
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [missionary.core :as mi]
   [re-frame.core :as rf]
   [re-frame.router :as router]))

(let [tasks_ (atom {})]
  (rf/reg-fx
   :dispatch-later
   (fn [data]
     (if (zero? (mod (count data) 3))
       (let [it (iter data)]
         (while (.hasNext it)
           (let [ms     (.next it)
                 id     (.next it)
                 evts   (.next it)
                 evt    (nth evts 0)
                 cancel (@tasks_ id)]
             (when cancel (cancel))
             (when-let [task (enc/cond!
                               (vector? evt)
                               (mi/sp (mi/? (mi/sleep ms)) (doseq [evt evts] (router/dispatch evt)))

                               (keyword? evt)
                               (mi/sp (mi/? (mi/sleep ms)) (router/dispatch evts)))]
               (swap! tasks_ assoc id (task #() #()))))))
       (timbre/error "matterhorn: \":dispatch-debounce\" number of elements should be multiple of 3")))))
