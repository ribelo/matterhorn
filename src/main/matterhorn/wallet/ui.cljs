(ns matterhorn.wallet.ui
  (:require
   [taoensso.encore :as enc]
   [re-frame.core :as rf]
   [ribelo.dye :as dye]
   [matterhorn.ui.subs :as ui.sub]
   [matterhorn.wallet.subs :as wall.sub]))

(defn wallet []
  (let [view @(rf/subscribe [::ui.sub/view])
        active? (= [:main :wallet] view)
        wallet @(rf/subscribe [::wall.sub/wallet])]
    (into [dye/vlist {:title "wallet"
                      :flex-grow 1
                      :border-style :round
                      :border-color (when active? :blue)}]
          (map (fn [[ticker p v]]
                 [dye/hbox {:flex-grow 1}
                  [dye/box {:flex-grow 1}
                   [dye/text ticker]]
                  [dye/text (.toFixed p 2)]
                  [dye/text " "]
                  [dye/text (enc/format "%10s" (.toFixed v 2))]
                  ]))
          wallet)))
