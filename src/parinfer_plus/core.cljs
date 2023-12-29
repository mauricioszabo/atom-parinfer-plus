(ns parinfer-plus.core
  (:require [parinfer-plus.aux :as aux]
            [parinfer-plus.status-bar :as bar]
            [clojure.string :as str]
            ["atom" :refer [TextBuffer Range]]))

(def config #js {})

(let [wrapper (js/require "./wrapper")]
  (def wasm-p (.-wasm_p wrapper))
  (def indent-mode (.-indentMode wrapper))
  (def paren-mode (.-parenMode wrapper))
  (def smart-mode (.-smartMode wrapper)))

(defn- run-parinfer! [^js editor, ^js changes]
  (let [{:keys [can-change? active?]} (aux/state-for editor)]
    (when (and active? can-change?)
      (let [buffer ^js (.getBuffer editor)
            new-range (.-newRange changes)
            old-range (.-oldRange changes)
            new-code (.getText buffer)
            old-code (-> (TextBuffer. #js {:text new-code})
                         (doto (.setTextInRange new-range
                                                (.-oldText changes)))
                         .getText)
            params #js {:prevText old-code
                        :cursorLine (.. new-range -end -row)
                        :prevCursorLine (.. old-range -end -row)
                        :cursorX (.. new-range -end -column)
                        :prevCursorX (.. old-range -end -column)}
            smart-mode-res (delay (paren-mode new-code params))
            pasting-complex-code? (and (re-find #"\n.*\n" (.-newText changes))
                                       (.-success ^js @smart-mode-res))
            code (case (:mode @aux/state)
                   :smart smart-mode
                   :paren paren-mode
                   :ident indent-mode)
            res ^js (if false #_pasting-complex-code?
                      @smart-mode-res
                      (code new-code params))
            new-text (.-text res)
            pos-after-change (delay (.getCursorBufferPosition editor))]

        (when (.-success ^js res)
          (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] false)
          (when-not (= new-text new-code)
            (.setText editor (.-text ^js res))
            (.groupLastChanges buffer)
            (.setCursorBufferPosition editor #js [(.-cursorLine ^js res) (.-cursorX ^js res)]))))))
  (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] true))

(let [a (:foo {}
              10)])

(defn- run-parinfer-for-range! [^js editor, ^js range, ^js changes]
  (let [{:keys [can-change? active?]} (aux/state-for editor)]
    (when (and active? can-change?)
      (let [buffer ^js (.getBuffer editor)
            new-range (.-newRange changes)
            old-range (.-oldRange changes)
            start (.-start range)
            new-code (.getTextInRange buffer range)
            old-code (-> (TextBuffer. #js {:text (.getText buffer)})
                         (doto (.setTextInRange new-range
                                                (.-oldText changes)))
                         (.getTextInRange range))
            old-line (- (.. old-range -end -row) (.-row start))
            old-x (.. old-range -end -column)
            new-line (- (.. new-range -end -row) (.-row start))
            new-x (.. new-range -end -column)
            params #js {:prevText old-code
                        :cursorLine new-line
                        :prevCursorLine old-line
                        :cursorX new-x
                        :prevCursorX old-x}
            code (case (:mode @aux/state)
                   :smart smart-mode
                   :paren paren-mode
                   :ident indent-mode)
            res ^js (code new-code params)
            new-text (.-text res)]

        (when (.-success ^js res)
          (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] false)
          (when-not (= new-text new-code)
            (.setTextInRange buffer range new-text)
            (.groupLastChanges buffer)
            (.setCursorBufferPosition editor #js [(+ (.-row start) (.-cursorLine ^js res))
                                                  (+ (.-column start) (.-cursorX ^js res))]))))))
  (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] true))

(def ^:private grammars #{"source.clojure"
                          "source.lisp"
                          "source.scheme"
                          "source.racket"})

(defn- activate-parinfer! [editor]
  (swap! aux/state assoc-in [:editors (.-id editor) :active?] true)
  (.. js/atom -views (getView editor) -classList (add "parinfer-plus")))

(defn- activate! [^js editor]
  (let [editor-txt (.getText editor)
        parinfer-result (paren-mode editor-txt {})
        parinfer-txt (.-text parinfer-result)]
    (cond
      (not (.-success ^js parinfer-result))
      (.. js/atom -notifications (addError "Your editor is unbalanced"))

      (= editor-txt parinfer-txt)
      (activate-parinfer! editor)

      (. js/atom confirm  #js {:message "Parinfer will change your file"
                               :detailedMessage "Parinfer must change your file's indentation. Continue?"
                               :buttons #js {:Yes (constantly true)
                                             :No (constantly false)}})
      (do
        (.setText editor parinfer-txt)
        (activate-parinfer! editor)))))

(defn- deactivate-parinfer! [editor]
  (swap! aux/state assoc-in [:editors (.-id editor) :active?] false)
  (.. js/atom -views (getView editor) -classList (remove "parinfer-plus")))

(defn- toggle-parinfer! []
  (let [editor (.. js/atom -workspace getActiveTextEditor)]
    (if (:active? (aux/state-for editor))
      (deactivate-parinfer! editor)
      (activate! editor))
    (bar/update-editor! editor)))

(defn- toggle-mode! []
  (let [editor (.. js/atom -workspace getActiveTextEditor)]
    (when (:active? (aux/state-for editor))
      (swap! aux/state update :mode #(if (#{:smart :indent} %)
                                       :paren
                                       (if (-> @aux/state :use-smart?) :smart :indent)))
      (bar/update-editor! editor))))

(defn- should-be-active? [^js editor]
  (let [grammar (.. editor getGrammar -id)
        editor-txt (delay (.getText editor))
        parinfer-result (delay (paren-mode @editor-txt {}))
        parinfer-txt (delay (.-text @parinfer-result))]
    (and (grammars (.. editor getGrammar -id))
         (.-success ^js @parinfer-result)
         (= @editor-txt @parinfer-txt))))

(defn- find-root-row [^js editor row last-row]
  (or
   (->> (range row last-row (if (< row last-row) 1 -1))
        (filter (fn [row]
                  (re-matches #"[^\s]" (.getTextInRange editor (clj->js [[row 0] [row 1]])))))
        first)
   last-row))

(defn- editor-changed [^js editor, ^js evt]
  (let [new-range (.-newRange evt)
        first-row (.. new-range -start -row)
        last-row (inc (.. new-range -end -row))
        last-editor-row (.. editor getBuffer getLastRow)
        last-range-row (find-root-row editor last-row last-editor-row)
        last-range-row (cond-> last-range-row
                         (not= last-editor-row last-range-row) dec)
        root-range (new Range #js {:column 0 :row (find-root-row editor first-row 0)}
                              #js {:column ##Inf :row last-range-row})]

    (if root-range
      (run-parinfer-for-range! editor root-range evt)
      (run-parinfer! editor evt))))

(defn- observe-editor [^js editor]
  (swap! aux/state assoc-in
         [:editors (.-id editor)]
         {:can-change? true})
  (when (should-be-active? editor) (activate-parinfer! editor))
  (aux/subscribe! (.. editor getBuffer
                      (onDidChange #(editor-changed editor %))))
  (aux/subscribe!
   (. editor (onDidDestroy #(swap! aux/state update :editors dissoc (.-id editor))))))

(defn- paste [after?]
  (let [editor (.. js/atom -workspace getActiveTextEditor)
        clipboard-text (.. js/atom -clipboard read)
        position (.getCursorBufferPosition editor)
        text (.getText editor)
        prev-row (.-row position)
        params {:prevText text
                :prevCursorLine prev-row
                :prevCursorX (.-column position)}
        fixed-clipboard-text (.-text (smart-mode clipboard-text (clj->js params)))
        lines (str/split-lines fixed-clipboard-text)
        new-row (+ prev-row (count lines) -1)
        new-col (if (= new-row prev-row)
                  (+ (:prevCursorX params) (count fixed-clipboard-text))
                  (-> lines last count))
        params (assoc params :cursorLine new-row :cursorX new-col)
        replaced-buffer (reduce (fn [^js buffer ^js selection]
                                  (let [range (.getBufferRange selection)
                                        range (if after?
                                                (let [p (.. range -start (traverse #js {:column 1}))]
                                                  (new Range p p))
                                                range)]
                                    (doto buffer
                                          (.setTextInRange range
                                                           fixed-clipboard-text))))
                                (TextBuffer. #js {:text text})
                                (.getSelections editor))
        ^js res (paren-mode (.getText ^js replaced-buffer) (clj->js params))]
    (if (.-success res)
      (do
        (.setText editor (.-text res))
        (.setCursorBufferPosition editor #js [(.-cursorLine ^js res) (.-cursorX ^js res)]))
      (.pasteText editor))))

(defn main []
  (. wasm-p then
    #(do
      (aux/subscribe! (.. js/atom -workspace (observeTextEditors observe-editor)))
      (aux/subscribe! (.. js/atom -workspace (observeActiveTextEditor bar/update-editor!)))

      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:paste" (fn [] (paste false)))))
      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:paste-after" (fn [] (paste true)))))
      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:toggle" toggle-parinfer!)))

      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:toggle-mode" toggle-mode!))))))

(defn ^:dev/after-load after-load []
  (.. js/atom -notifications (addSuccess "Reloaded Parinfer-Plus"))
  (main))
