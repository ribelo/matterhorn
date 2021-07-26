(ns matterhorn.fx
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [re-frame.core :as rf]
   [re-frame.router :as router]))

(let [timeouts (atom {})]
  (rf/reg-fx
   :dispatch-later
   (fn [data]
     (if (zero? (mod (count data) 3))
       (let [it (iter data)]
         (while (.hasNext it)
           (let [ms    (.next it)
                 id    (.next it)
                 evts  (.next it)]
             (let [t (enc/cond!
                       :do            (some-> (@timeouts id) js/clearTimeout)
                       :let           [evt (nth evts 0)]
                       (vector?  evt) (js/setTimeout #(doseq [evt evts] (router/dispatch evt)) ms)
                       (keyword? evt) (js/setTimeout #(router/dispatch evts) ms))]
               (swap! timeouts assoc id t)))))
       (timbre/error "matterhorn: \":dispatch-debounce\" number of elements should be multiple of 3")))))
