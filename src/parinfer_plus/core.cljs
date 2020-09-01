(ns parinfer-plus.core
  (:require [parinfer-plus.aux :as aux]
            [clojure.string :as str]
            ["atom" :refer [TextBuffer]]))

(let [wrapper (js/require "./wrapper")]
  (def indent-mode (.-indentMode wrapper))
  (def paren-mode (.-parenMode wrapper))
  (def smart-mode (.-smartMode wrapper)))

(defn- run-parinfer! [^js buffer, ^js changes]
  (let [new-range (.-newRange changes)
        old-range (.-oldRange changes)
        new-code (.getText buffer)
        old-code (-> (TextBuffer. #js {:text new-code})
                     (doto (.setTextInRange new-range
                                            (.-oldText changes)))
                     .getText)
        res ^js (smart-mode new-code
                            #js {:prevText old-code})]
                                 ; :cursorLine (.. new-range -end -row)
                                 ; :prevCursorLine (.. old-range -end -row)
                                 ; :cursorX (.. new-range -end -col)
                                 ; :prevCursorX (.. old-range -end -col)})]
    (println :OLD)
    (println old-code)
    (println :NEW)
    (println new-code)
    (println
     (.-text res)
     (.-success res))))

    ; (.setTextInRange old-code new-range old-txt)))
    ; (println
    ;  (str/join "\n" (.getLines old-code)))
    ; (let [old-txt (.-oldText changes)])
    ;
    ; (def changes changes)))
  ; (tap> (.-changes changes)))

(defn- observe-editor [^js editor]
  (let [buffer (.getBuffer editor)]
    (aux/subscribe! (. buffer (onDidChange #(run-parinfer! buffer %))))))

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
