(ns parinfer-plus.status-bar
  (:require [parinfer-plus.aux :as aux]
            [clojure.string :as str]))

(defonce status-bar (atom nil))
(defonce span (atom nil))

(defn- set-text! [txt]
  (aset @span "innerText" txt))

(defn ^:dev/after-load activate [bar]
  (when bar
    (reset! status-bar bar))
  (reset! span (doto (.createElement js/document "span")
                     (.. -classList (add "inline-block"))))
  (let [tile (.addRightTile ^js @status-bar
                           #js {:item @span
                                :priority 100})]
    (aux/subscribe! #js {:dispose #(.destroy tile)})))

(defn update-editor! [ ^js editor]
  (when editor
    (if (:active? (aux/state-for editor))
      (-> @aux/state :mode name str/capitalize (str " mode") set-text!)
      (set-text! ""))))
