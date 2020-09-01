(ns parinfer-plus.status-bar)

(defonce status-bar (atom nil))

(defn activate [bar]
  (reset! status-bar bar))
