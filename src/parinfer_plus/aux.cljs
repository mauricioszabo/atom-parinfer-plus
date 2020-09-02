(ns parinfer-plus.aux
  (:require ["atom" :refer [CompositeDisposable]]))

(defonce disposables (atom (CompositeDisposable.)))

(defn ^:dev/before-load reset-disposables! []
  (.dispose ^js @disposables)
  (reset! disposables (CompositeDisposable.)))

(defn subscribe! [disposable]
  (.add @disposables disposable))

(defonce state (atom {:mode :smart
                      :use-smart? true
                      :editors {}}))

(defn state-for [^js editor]
  (get-in @state [:editors (.-id editor)]))
