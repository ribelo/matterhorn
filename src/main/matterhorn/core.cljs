(ns matterhorn.core
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [matterhorn.init :as init]
   [matterhorn.ui :as ui]
   ;; [matterhorn.ipc :as ipc]
   [ribelo.dye :as dye]
   ["process" :as process]
   ["readline" :as readline]
   ["console" :as console]))

(defonce renderer (atom nil))

(defn view->element [view]
  (-> (r/reactify-component view)
      (r/create-element #js {})))

(defn reload! [_]
  (.clear console)
  ;; (.unmount  ^js @renderer)
  (.rerender ^js @renderer (view->element ui/view)))

(defn main []
  (.clear console)
  (reset! renderer (-> (view->element ui/view)
                       (dye/render #js {:exitOnCtrlC false})))
  (.emitKeypressEvents readline (.-stdin process))
  (rf/dispatch-sync [::init/boot]))
