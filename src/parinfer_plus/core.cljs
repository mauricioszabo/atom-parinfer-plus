(ns parinfer-plus.core
  (:require [parinfer-plus.aux :as aux]
            [clojure.string :as str]
            ["atom" :refer [TextBuffer]]))

(let [wrapper (js/require "./wrapper")]
  (def indent-mode (.-indentMode wrapper))
  (def paren-mode (.-parenMode wrapper))
  (def smart-mode (.-smartMode wrapper)))

(defn- run-parinfer! [^js editor, can-change?, ^js changes]
  (when @can-change?
    (let [buffer ^js (.getBuffer editor)
          new-range (.-newRange changes)
          old-range (.-oldRange changes)
          new-code (.getText buffer)
          old-code (-> (TextBuffer. #js {:text new-code})
                       (doto (.setTextInRange new-range
                                              (.-oldText changes)))
                       .getText)
          res ^js (smart-mode new-code
                              #js {:prevText old-code
                                   :cursorLine (.. new-range -end -row)
                                   :prevCursorLine (.. old-range -end -row)
                                   :cursorX (.. new-range -end -column)
                                   :prevCursorX (.. old-range -end -column)})
          new-text (.-text res)]

      (when (.-success res)
        (reset! can-change? false)
        (when-not (= new-text new-code)
          (.setText editor (.-text res))
          (.setCursorBufferPosition editor #js [(.-cursorLine res) (.-cursorX res)])
          (.groupLastChanges buffer)))))
  (reset! can-change? true))

(defn- observe-editor [^js editor]
  (let [can-change? (atom true)]
    (aux/subscribe! (.. editor getBuffer
                        (onDidChange #(run-parinfer! editor can-change? %))))))

(defn main []
  (aux/subscribe! (.. js/atom -workspace (observeTextEditors observe-editor))))
    ; @subscriptions.add atom.workspace.observeActivePaneItem (item) =>
    ;   @parinfer.updateStatusBar(item) if item instanceof TextEditor))

(defn ^:dev/after-load after-load []
  (.. js/atom -notifications (addSuccess "Reloaded Parinfer-Plus"))
  (main))

#_
(foo [a 11
      b 2])
