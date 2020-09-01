(ns parinfer-plus.aux
  (:require ["atom" :refer [CompositeDisposable]]))

(defonce disposables (atom (CompositeDisposable.)))

(defn ^:dev/before-load reset-disposables! []
  (.dispose ^js @disposables)
  (reset! disposables (CompositeDisposable.)))

(defn subscribe! [disposable]
  (.add @disposables disposable))

    ; @subscriptions.add atom.commands.add('atom-text-editor', 'parinfer-plus:toggle': =>)
    ;   @parinfer.toggle(atom.workspace.getActiveTextEditor())
    ;
    ; @subscriptions.add atom.commands.add('atom-text-editor', 'parinfer-plus:toggle-mode': =>)
    ;   @parinfer.toggleMode(atom.workspace.getActiveTextEditor())
    ;
    ; @subscriptions.add atom.workspace.observeActivePaneItem (item) =>
    ;   @parinfer.updateStatusBar(item) if item instanceof TextEditor))
