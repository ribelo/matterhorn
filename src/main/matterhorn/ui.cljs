(ns matterhorn.ui
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [reagent.core :as r]
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [meander.epsilon :as m]
   [ribelo.dye :as dye]
   [ribelo.doxa :as dx]
   [ribelo.danzig :refer [=>>]]
   [matterhorn.ui.events :as ui.evt]
   [matterhorn.ui.subs :as ui.sub]
   [matterhorn.ui.table :as table]
   [matterhorn.wallet.ui :as wallet.ui]
   [matterhorn.logging.events :as log.evt]
   [matterhorn.logging.subs :as log.sub]
   [matterhorn.settings.events :as set.evt]
   [matterhorn.settings.subs :as set.sub]
   [matterhorn.provider.yf.events :as yf.evt]
   [matterhorn.provider.yf.subs :as yf.sub]
   ["process" :as process]))

(defn title []
  [dye/box {:justify-content :center}
   [dye/text "matterhorn"]])

(def atm_ (atom []))

(defn log-window []
  (let [height_   (r/atom nil)
        log-data_ (rf/subscribe [::log.sub/data])]
    (fn []
      (into [dye/vscroller {:id           :log-window
                            :active?      true
                            :height       @height_
                            :flex-grow    2
                            :border-style :round
                            :ref          (fn [el]
                                            (let [h (some-> el dye/measure-element .-height)]
                                              (when h (not (= h @height_))
                                                    (reset! height_ h))))}]
            (map (fn [[level timestamp msg]]
                   [dye/text {:key       timestamp
                              :dim-color false}
                    [dye/text {:color "gray"}
                     (enc/format "%-7s" (str level))]
                    " "
                    [dye/text timestamp]
                    " "
                    [dye/text msg]]))
            @log-data_))))

(defn settings []
  (let [view_       (rf/subscribe [::ui.sub/view])
        active?_    (ra/reaction (= @view_ [:main :settings]))
        idx_        (r/atom 0)
        m_          (rf/subscribe [:pull :app/settings
                                   [:money :risk :frisk :tf :freq :max-assets]
                                   [:db/id :settings]])]
    (fn []
      [dye/vlist {:id             :settings
                  :active?        @active?_
                  :title          "settings"
                  :flex-direction :column
                  :height         8
                  :border-style   :round
                  :border-color   (when @active?_ :blue)
                  :idx_           idx_}
       ;; money
       [dye/with-keys {:id      :money
                       :active? (and @active?_ (= @idx_ 0))
                       :keymap  [["left"    #(rf/dispatch [::set.evt/update-money  -10])]
                                 ["S-left"  #(rf/dispatch [::set.evt/update-money -100])]
                                 ["right"   #(rf/dispatch [::set.evt/update-money   10])]
                                 ["S-right" #(rf/dispatch [::set.evt/update-money  100])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 0)) :blue)}
          "money"]
         [dye/text {:color (when (and @active?_ (= @idx_ 0)) :blue)}
          (.toFixed (@m_ :money 0) 2)]]]
       ;; risk
       [dye/with-keys {:id      :risk
                       :active? (and @active?_ (= @idx_ 1))
                       :keymap  [["left"    #(rf/dispatch [::set.evt/update-risk -0.01])]
                                 ["S-left"  #(rf/dispatch [::set.evt/update-risk  -0.1])]
                                 ["right"   #(rf/dispatch [::set.evt/update-risk  0.01])]
                                 ["S-right" #(rf/dispatch [::set.evt/update-risk   0.1])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 1)) :blue)}
          "risk"]
         [dye/text {:color (when (and @active?_ (= @idx_ 1)) :blue)}
          (.toFixed (@m_ :risk 0) 2)]]]
       ;; frisk
       [dye/with-keys {:id      :free-risk
                       :active? (and @active?_ (= @idx_ 2))
                       :keymap  [["left"    #(rf/dispatch [::set.evt/update-frisk -0.01])]
                                 ["S-left"  #(rf/dispatch [::set.evt/update-frisk  -0.1])]
                                 ["right"   #(rf/dispatch [::set.evt/update-frisk  0.01])]
                                 ["S-right" #(rf/dispatch [::set.evt/update-frisk   0.1])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 2)) :blue)}
          "free risk"]
         [dye/text {:color (when (and @active?_ (= @idx_ 2)) :blue)}
          (.toFixed (@m_ :frisk 0) 2)]]]
       ;; max assets
       [dye/with-keys {:id      :max-assets
                       :active? (and @active?_ (= @idx_ 3))
                       :keymap  [["left"    #(rf/dispatch [::set.evt/update-max-assets  -1])]
                                 ["S-left"  #(rf/dispatch [::set.evt/update-max-assets -10])]
                                 ["right"   #(rf/dispatch [::set.evt/update-max-assets   1])]
                                 ["S-right" #(rf/dispatch [::set.evt/update-max-assets  10])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 3)) :blue)}
          "max assets"]
         [dye/text {:color (when (and @active?_ (= @idx_ 3)) :blue)}
          (.toFixed (@m_ :max-assets 0) 2)]]]
       ;; freq
       [dye/with-keys {:id      :freq
                       :active? (and @active?_ (= @idx_ 4))
                       :keymap  [["left"    #(rf/dispatch [::set.evt/update-freq  -1])]
                                 ["S-left"  #(rf/dispatch [::set.evt/update-freq -10])]
                                 ["right"   #(rf/dispatch [::set.evt/update-freq   1])]
                                 ["S-right" #(rf/dispatch [::set.evt/update-freq  10])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 4)) :blue)}
          "freq"]
         [dye/text {:color (when (and @active?_ (= @idx_ 4)) :blue)}
          (.toFixed (@m_ :freq 0) 2)]]]
       ;; tf
       [dye/with-keys {:id      :tf
                       :active? (and @active?_ (= @idx_ 5))
                       :keymap  [["left"   #(rf/dispatch [::set.evt/change-tf])]
                                 ["right"  #(rf/dispatch [::set.evt/change-tf])]]}
        [dye/box {:flex-grow 1
                  :flex-direction  :row
                  :justify-content :space-between}
         [dye/text {:color (when (and @active?_ (= @idx_ 5)) :blue)}
          "tf"]
         [dye/text {:color (when (and @active?_ (= @idx_ 5)) :blue)}
          (case (@m_ :tf :mn)
            :mn "month"
            :d1 "day")]]]])))

(defn footer-menu []
  (let [current-view @(rf/subscribe [::ui.sub/view])]
    (into [dye/hbox {:justify-content :space-between
                     :padding-x       4}]
          (comp
           (map (fn [{:keys [key view]}]
                  (let [active? (= view current-view)]
                    [dye/with-keys {:id      view
                                    :active? true
                                    :keymap  {key #(rf/dispatch [::ui.evt/change-view view])}}
                     (into
                      [dye/text {:color (when-not active? :gray)}
                       [dye/text
                        key]
                       [dye/text
                        ":"]]
                      (interpose "-" (into [] (map (fn [elem] [dye/text elem])) view)))])))
           (interpose [dye/box [dye/text "|"]]))
          [{:key "M-1" :view [:main :search]}
           {:key "M-2" :view [:main :settings]}
           {:key "M-3" :view [:main :wallet]}
           {:key "M-4" :view [:main :table]}
           {:key "M-0" :view [:log]}])))

(defn status-bar []
  (let [last-key  @(rf/subscribe [::ui.sub/last-key])
        [level timestamp msg
         :as evt] @(rf/subscribe [::log.sub/last-event])]
    [dye/hbox {:height    1
               :padding-x 4
               :justify-content :space-between}
     [dye/text {:color :gray}
      [dye/text last-key]]
     (when evt
       [dye/hbox
        [dye/text
         [dye/text {:color :gray} timestamp]
         [dye/text " "]
         [dye/text {:color :gray} (enc/get-substr msg 0 96)]
         [dye/text " "]
         [dye/text {:color (case level
                             :debug   :gray
                             :success :green
                             :info    :blue
                             :warn    :yellow
                             :error   :red)}
          (name level)]]])]))

(defn ticker-search-line [active? ticker]
  (let [download? @(rf/subscribe [::yf.sub/download-ticker? ticker])
        color     (enc/cond
                    (and active? (not download?))
                    :white
                    (and active? download?)
                    :green
                    :else
                    :gray)]
    [dye/box {:flex-grow       1
              :justify-content :space-between}
     [dye/text {:color color}
      ticker]]))


(defn yahoo-tickers []
  (let [view_    (rf/subscribe [::ui.sub/view])
        active?_ (ra/reaction (= [:main :tickers] @view_))
        tickers_ (rf/subscribe [::yf.sub/tickers-to-download])
        height_  (r/atom 2)]
    (fn []
      (when (seq @tickers_)
        (into [dye/vlist {:title        "tickers"
                          :active?      @active?_
                          :height       (- @height_ 2)
                          :flex-grow    1
                          :flex-shrink  2
                          :border-style :round
                          :border-color (when-not @active?_ :grey)
                          :ref (fn [el]
                                 (let [h (some-> el dye/measure-element :height)]
                                   (when (and (some? h) (not= h @height_))
                                     (reset! height_ h))))}]
              (map (fn [ticker]
                     [dye/text {:color (when-not @active?_ :grey)}
                      ticker]))
              @tickers_)))))

(defn ticker-search []
  (let [view       @(rf/subscribe [::ui.sub/view])
        active?    (= [:main :search] view)
        value      @(rf/subscribe [::yf.sub/search-ticker-input])
        tickers    @(rf/subscribe [::yf.sub/search-ticker-results])
        searching? @(rf/subscribe [::yf.sub/searching-ticker?])
        failure?   @(rf/subscribe [::yf.sub/searching-failure?])
        exists?    @(rf/subscribe [::yf.sub/ticker-exists? value])]
    [dye/vbox {:title        "search"
               :flex-shrink  0
               :border-style :round
               :border-color (when active? :blue)}
     [dye/box
      [dye/hbox {:flex-grow       1
                 :justify-content :space-between}
       [dye/text-input {:active?      active?
                        :value        value
                        :padding-left 1
                        :color        (cond
                                        (and failure? (not exists?)) :red
                                        (and failure? exists?)       :green)
                        :dim-color    (when (not active?) true)
                        :placeholder  "provide ticker"
                        :on-change    #(rf/dispatch [::yf.evt/set-input-search {:ticker %}])
                        :on-submit    (fn []
                                        (when (and failure? exists?)
                                          (rf/dispatch [::yf.evt/toggle-download-ticker {:ticker (keyword value)}])))}]
       (when searching? [dye/spinner])]]
     (when (and (seq value) (seq tickers))
       (into [dye/vlist {:id        "yaho-ticker-list"
                         :active?   active?
                         :up-key    "up"
                         :down-key  "down"
                         :on-submit (fn [idx] (rf/dispatch [::yf.evt/toggle-download-ticker {:ticker (nth tickers idx)}]))}]
             (map (fn [ticker] [ticker-search-line active? ticker]))
             tickers))]))

(defn view []
  (let [v @(rf/subscribe [::ui.sub/view])]
    [dye/stdin-consumer {:raw-mode? true}
     [dye/with-keys {:id :main-view
                     :active? true
                     :keymap [[nil (fn [key-seq]
                                     (rf/dispatch [::ui.evt/set-last-key (dye/-encode-keys key-seq)]) )]]}
      [dye/vbox {:height       (-> process .-stdout .-rows)
                 :padding-x    1
                 :border-color :gray}
       (m/find v

         (m/or nil [:main & _])
         [dye/hbox {:flex-grow 1}
          [dye/vbox {:width "16%"}
           [ticker-search]
           [settings]
           [wallet.ui/wallet]]
          [dye/vbox {:width "84%"}
           [table/tickers-table]]
          ]
         [:log & _]
         [dye/vbox {:flex-grow 3}
          [log-window]])
       [footer-menu]
       [status-bar]
       ]]]))
