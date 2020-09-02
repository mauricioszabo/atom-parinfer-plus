(ns parinfer-plus.core
  (:require [parinfer-plus.aux :as aux]
            [parinfer-plus.status-bar :as bar]
            [clojure.string :as str]
            ["atom" :refer [TextBuffer]]))

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
            res ^js (if pasting-complex-code?
                      @smart-mode-res
                      (code new-code params))
            new-text (.-text res)]

        (when (.-success ^js res)
          (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] false)
          (when-not (= new-text new-code)
            (.setText editor (.-text ^js res))
            (.groupLastChanges buffer)
            (.setCursorBufferPosition editor #js [(.-cursorLine ^js res) (.-cursorX ^js res)]))))))
  (swap! aux/state assoc-in [:editors (.-id editor) :can-change?] true))

(def ^:private grammars #{"source.clojure"
                          "source.lisp"
                          "source.scheme"
                          "source.racket"})

(defn- activate! [^js editor]
  (let [editor-txt (.getText editor)
        parinfer-result (paren-mode editor-txt {})
        parinfer-txt (.-text parinfer-result)]
    (cond
      (not (.-success ^js parinfer-result))
      (.. js/atom -notifications (addError "Your editor is unbalanced"))

      (= editor-txt parinfer-txt)
      (swap! aux/state assoc-in [:editors (.-id editor) :active?] true)

      (. js/atom confirm  #js {:message "Parinfer will change your file"
                                     :detailedMessage "Parinfer must change your file's indentation. Continue?"
                                     :buttons #js {:Yes (constantly true)
                                                   :No (constantly false)}})
      (do
        (.setText editor parinfer-txt)
        (swap! aux/state assoc-in [:editors (.-id editor) :active?] true)))))

(defn- toggle-parinfer! []
  (let [editor (.. js/atom -workspace getActiveTextEditor)]
    (if (:active? (aux/state-for editor))
      (swap! aux/state assoc-in [:editors (.-id editor) :active?] false)
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

(defn- observe-editor [^js editor]
  (swap! aux/state assoc-in [:editors (.-id editor)]
         {:can-change? true
          :active? (should-be-active? editor)})
  (aux/subscribe! (.. editor getBuffer
                      (onDidChange #(run-parinfer! editor %))))
  (aux/subscribe!
   (. editor (onDidDestroy #(swap! aux/state update :editors dissoc (.-id editor))))))

(defn main []
  (. wasm-p then
    #(do
      (aux/subscribe! (.. js/atom -workspace (observeTextEditors observe-editor)))
      (aux/subscribe! (.. js/atom -workspace (observeActiveTextEditor bar/update-editor!)))

      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:toggle" toggle-parinfer!)))

      (aux/subscribe! (.. js/atom
                          -commands
                          (add "atom-text-editor" "parinfer-plus:toggle-mode" toggle-mode!))))))

(defn ^:dev/after-load after-load []
  (.. js/atom -notifications (addSuccess "Reloaded Parinfer-Plus"))
  (main))
