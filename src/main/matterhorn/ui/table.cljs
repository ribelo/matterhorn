(ns matterhorn.ui.table
  (:require
   [taoensso.encore :as enc]
   [reagent.core :as r]
   [reagent.ratom :as ra]
   [re-frame.core :as rf]
   [meander.epsilon :as m]
   [ribelo.dye :as dye]
   [ribelo.kemnath :as math]
   [matterhorn.ui.subs :as ui.sub]
   [matterhorn.provider.yf.subs :as yf.sub]
   [matterhorn.provider.yf.events :as yf.evt]
   [matterhorn.wallet.events :as wall.evt]
   [matterhorn.wallet.subs :as wall.sub]))

(def header-cells
  [["ticker"      8 :left]
   ["name"       24 :left]
   ["price"       8 :right]
   ["$"           8 :right]
   ["%"           8 :right]
   ["β"           8 :right]
   ["ma 200"      8 :right]
   ["ma 50"       8 :right]
   ["avg dd"      8 :right]
   ["max dd"      8 :right]
   ["3σ"          8 :right]
   ["ret"         8 :right]
   ["risk"        8 :right]
   ["sr"          8 :right]
   ["calmar"      8 :right]
   ["redp"        8 :right]
   ["salloc"      8 :right]])

(defn -width [s]
  (m/find header-cells
    (m/scan [~s ?x & _]) ?x))

(defn -total-width []
  (m/rewrite header-cells
    [[_ !vs _] ...] ~(reduce + !vs)))

(defn calc-width [s w]
  (math/floor (* (/ (-width s) (-total-width)) (- w 2))))

(defn header-cell [{:keys [text width align] :as props}]
  [dye/text-raw (into {:bold true :width width :align align} props)
   text])

(defn header [total-width]
  (into
   [dye/hbox {:margin-bottom 1}]
   (map (fn [[text width align]]
          (let [w (math/floor (* (/ width (-total-width)) (- total-width 2)))]
            [header-cell {:text text :width w :align align}])))
   header-cells))

(comment
  (tap> [@(rf/subscribe [:pull :yahoo/db
                         [:*]
                         [:db/id :amzn]])])
  )

(defn row [{:keys [total-width] :as props} ticker]
  (let [info        @(rf/subscribe [:pull :yahoo/db [:*] [:db/id ticker]])
        valid-data? @(rf/subscribe [::yf.sub/valid-quotes-data? ticker])
        in-wallet?  @(rf/subscribe [::wall.sub/in-wallet? ticker])
        fetched?    @(rf/subscribe [::yf.sub/fetched? ticker])
        failure?    @(rf/subscribe [::yf.sub/failure? ticker])]
    [dye/hbox
     [dye/text {:wrap      :truncate
                :dim-color (not fetched?)}
      [dye/text-raw (into {:width (calc-width "ticker" total-width)
                           :align :left
                           :color (cond
                                    (or (not valid-data?) failure?) :red
                                    (= :pos in-wallet?)             :blue
                                    (= :net in-wallet?)             :yellow)}
                          props)
       (name ticker)]
      (let [v (:full-name info)]
        [dye/text-raw (into {:width         (calc-width "name" total-width)
                             :align         :left
                             :padding-right 1
                             :color         (cond
                                              (or (not valid-data?) failure?) :red
                                              (= :pos in-wallet?)             :blue
                                              (= :net in-wallet?)             :yellow)}
                            props)
         v])
      (let [v (:price info)]
        [dye/text-raw (into {:width (calc-width "price" total-width)
                             :align :right}
                            props)
         (some-> v (.toFixed 2))])
      (let [v (:diff-value info)]
        [dye/text-raw (into {:width (calc-width "$" total-width)
                             :align :right
                             :color (if (pos? (:diff-value info)) :green :red)}
                            props)
         (some-> v (.toFixed 2))])
      (let [v (:diff-percentage info)]
        [dye/text-raw (into {:width (calc-width "%" total-width)
                             :align :right
                             :color (if (pos? v) :green :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:beta info)]
        [dye/text-raw (into {:width (calc-width "β" total-width)
                             :align :right
                             :color (if (> v 1.0) :blue :yellow)}
                            props)
         (or (some-> v (.toFixed 2)) "----")])
      (let [v (:day-ma-200 info)]
        [dye/text-raw (into {:width (calc-width "ma 200" total-width)
                             :align :right
                             :color (if (> (:day-ma-50 info) (:day-ma-200 info)) :green :red)}
                            props)
         (some-> v (.toFixed 2))])
      (let [v (:day-ma-50 info)]
        [dye/text-raw (into {:width (calc-width "ma 50" total-width)
                             :align :right
                             :color (if (> (:day-ma-50 info) (:day-ma-200 info)) :green :red)}
                            props)
         (some-> v (.toFixed 2))])
      (let [v (:average-drawndown info)]
        [dye/text-raw (into {:width (calc-width "avg dd" total-width)
                             :align :right
                             :color (cond
                                      (<= v 0.05) :green
                                      (<= v 0.10) :blue
                                      (<= v 0.15) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:maximum-drawndown info)]
        [dye/text-raw (into {:width (calc-width "max dd" total-width)
                             :align :right
                             :color (cond
                                      (<= v 0.10) :green
                                      (<= v 0.15) :blue
                                      (<= v 0.30) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:std3 info)]
        [dye/text-raw (into {:width (calc-width "3σ" total-width)
                             :align :right
                             :color (cond
                                      (<= v 0.15) :green
                                      (<= v 0.30) :blue
                                      (<= v 0.45) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:annualized-return info)]
        [dye/text-raw (into {:width (calc-width "ret" total-width)
                             :align :right
                             :color (cond
                                      (>= v 0.30) :green
                                      (>= v 0.15) :blue
                                      (>= v 0.00) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:annualized-risk info)]
        [dye/text-raw (into {:width (calc-width "risk" total-width)
                             :align :right
                             :color (cond
                                      (<= v 0.15) :green
                                      (<= v 0.30) :blue
                                      (<= v 0.50) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:annualized-sharpe-ratio info)]
        [dye/text-raw (into {:width (calc-width "sr" total-width)
                             :align :right
                             :color (cond
                                      (>= v 3.00) :green
                                      (>= v 2.00) :blue
                                      (>= v 1.00) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:calmar-ratio info)]
        [dye/text-raw (into {:width (calc-width "calmar" total-width)
                             :align :right
                             :color (cond
                                      (>= v 5.00) :green
                                      (>= v 2.00) :blue
                                      (>= v 0.00) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:redp info)]
        [dye/text-raw (into {:width (calc-width "redp" total-width)
                             :align :right
                             :color (cond
                                      (=  v 0.00) :green
                                      (<= v 0.05) :blue
                                      (<= v 0.10) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])
      (let [v (:allocation info)]
        [dye/text-raw (into {:width (calc-width "salloc" total-width)
                             :align :right
                             :color (cond
                                      (=  v 1.00) :green
                                      (>= v 0.75) :blue
                                      (>= v 0.50) :yellow
                                      :else       :red)}
                            props)
         (some-> v (.toFixed 3))])]]))

(defn tickers-table []
  (let [view_    (rf/subscribe [::ui.sub/view])
        active?_ (ra/reaction (= @view_ [:main :table]))
        tickers_ (rf/subscribe [::yf.sub/quotes-tickers])
        height_  (r/atom nil)
        idx_     (r/atom 0)
        width_   (r/atom 0)]
    (fn []
      [dye/vbox {:title        "matterhorn"
                 :border-style :round
                 :border-color (when @active?_ :blue)
                 :flex-grow    1}
       [dye/box {:padding-left 1} [header @width_]]
       (into
        [dye/vlist {:active?   @active?_
                    :height    @height_
                    :flex-grow 1
                    :idx_      idx_
                    :on-submit #(rf/dispatch [::wall.evt/toggle-ticker {:ticker (nth @tickers_ @idx_)}])
                    :ref       (fn [el]
                                 (enc/when-let [{:keys [width height]} (some-> el dye/measure-element)]
                                   (when (and (some? height) (not= height @height_))
                                     (reset! height_ height))
                                   (when (and (some? width) (not= width @width_))
                                     (reset! width_ width))))
                    :keymap    [["delete" #(rf/dispatch [::yf.evt/toggle-download-ticker {:ticker (nth @tickers_ @idx_)}])]
                                ["C-r"    #(rf/dispatch [::yf.evt/refresh-matterhorn])]]}]
        (map-indexed
         (fn [i ticker]
           [row {:total-width @width_
                 :bold        (and @active?_ (= i @idx_))
                 :underline   (and @active?_ (= i @idx_))}
            ticker]))
        @tickers_)])))
