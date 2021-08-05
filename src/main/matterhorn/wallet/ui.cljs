(ns matterhorn.wallet.ui
  (:require
   [taoensso.encore :as enc]
   [re-frame.core :as rf]
   [ribelo.dye :as dye]
   [matterhorn.ui.subs :as ui.sub]
   [matterhorn.wallet.subs :as wall.sub]))

(defn wallet []
  (let [view    @(rf/subscribe [::ui.sub/view])
        active? (= [:main :wallet] view)
        wallet  @(rf/subscribe [::wall.sub/wallet])
        total   @(rf/subscribe [::wall.sub/wallet-value])]
    (conj
     (into [dye/vlist {:title        "wallet"
                       :flex-grow    1
                       :border-style :round
                       :border-color (when active? :blue)}
            [dye/hbox {:flex-grow      1
                       :padding-bottom 1}
             [dye/box {:flex-grow 1}
              [dye/text "ticker"]]
             [dye/text (enc/format "%4s" "%")]
             [dye/text " "]
             [dye/text (enc/format "%6s" "n")]
             [dye/text " "]
             [dye/text (enc/format "%8s" "$")]]]
           (map (fn [[ticker p n v]]
                  [dye/hbox {:flex-grow 1}
                   [dye/box {:flex-grow 1}
                    [dye/text ticker]]
                   [dye/text (enc/format "%4s" (.toFixed p 2))]
                   [dye/text " "]
                   [dye/text (enc/format "%6s" (.toFixed n 2))]
                   [dye/text " "]
                   [dye/text (enc/format "%8s" (.toFixed v 2))]]))
           wallet)
     [dye/hbox {:flex-grow   1
                :justify-content :flex-end
                :padding-top 1}
      [dye/text "total"]
      [dye/text " "]
      [dye/text (enc/format "%8s" (.toFixed total 2))]])))
