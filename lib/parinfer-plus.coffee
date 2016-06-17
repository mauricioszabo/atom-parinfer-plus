{CompositeDisposable} = require 'atom'
Parinfer = require './parinfer'

module.exports =
  activate: (state) ->
    @subscriptions = new CompositeDisposable()
    @parinfer = new Parinfer()

    @subscriptions.add atom.commands.add('atom-text-editor', 'parinfer-plus:toggle': =>
      @parinfer.toggle(atom.workspace.getActiveTextEditor()))

    @subscriptions.add atom.commands.add('atom-text-editor', 'parinfer-plus:toggle-mode': =>
      @parinfer.toggleMode(atom.workspace.getActiveTextEditor()))

  deactivate: ->
    @subscriptions.dispose();
    @statusBarTile?.destroy()
    @statusBarTile = null

  consumeStatusBar: (statusBar) ->
    div = document.createElement('div')
    div.classList.add('inline-block', 'parinfer-plus')
    @statusBarTile = statusBar.addRightTile(item: div, priority: 100)
    @parinfer.setTitleBarItem(div)
