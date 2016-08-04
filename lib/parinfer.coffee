par = require 'parinfer'

module.exports = class Parinfer
  parinferEditors: new Map()
  constructor: (@subscriptions) ->

  toggleMode: (editor) ->
    modes = @parinferEditors.get(editor)
    return unless modes
    [disposable, mode] = modes
    newMode = if mode == 'indent'
      'paren'
    else
      'indent'
    @parinferEditors.set(editor, [disposable, newMode])
    @updateStatusBar(editor)

  toggle: (editor) ->
    modes = @parinferEditors.get(editor)
    if modes
      modes[0].dispose()
      @parinferEditors.delete(editor)
    else
      @startParinferFor(editor)
    @updateStatusBar(editor)

  startParinferFor: (editor) ->
    # Starts on paren mode, then changes to indent mode
    return unless @triggerFirstChange(editor)

    # Adds editor callback
    @parinferText = null # We can't trigger "CHANGE" multiple times...
    disposable = editor.onDidStopChanging ({changes}) =>
      # Do parinfer's magic
      console.log(changes)
      return if !changes[0] || @parinferText == changes[0].newText
      modes = @parinferEditors.get(editor)
      return if !modes

      oldRanges = editor.getSelectedBufferRanges()
      @computeChange(editor, changes[0], oldRanges[0], modes[1])
      editor.setSelectedBufferRanges(oldRanges)

    @parinferEditors.set(editor, [disposable, 'indent'])
    @subscriptions.add(disposable)

  triggerFirstChange: (editor) ->
    {success, text, changedLines} = @parenMode(editor)
    if success
      if changedLines.length == 0
        return true
      else
        atom.confirm
          message: 'Parinfer will change your file'
          detailedMessage: "Parinfer must change your file's indentation. #{changedLines.length} line(s) will be affected.\n\n
                            Continue?"
          buttons:
            Yes: =>
              oldRanges = editor.getSelectedBufferRanges()
              editor.setText(text)
              editor.setSelectedBufferRanges(oldRanges)
              true
            No: => false
    else
      atom.confirm
        message: 'Parinfer error'
        detailedMessage: "Your file is unbalanced. Check if there are missing parenthesis, and start Parinfer again.."
        buttons: ["Ok, Sorry..."]
      return false

  parenMode: (editor) ->
    par.parenMode(editor.getText())

  computeChange: (editor, change, cursorRange, mode) ->
    {oldExtent, newExtent} = change
    opts = { cursorLine: cursorRange.end.row, cursorX: cursorRange.end.column }

    opts.cursorDx = @calculateDx(change) if mode == 'paren'

    oldText = editor.getText()
    parResult = if mode == 'indent'
      par.indentMode(oldText, opts)
    else
      par.parenMode(oldText, opts)
    {text, success, cursorX} = parResult
    @parinferText = text
    if success && oldText != @parinferText
      if mode == 'paren'
        cursorRange.start.column = cursorX
        cursorRange.end.column = cursorX
      buffer = editor.getBuffer()
      buffer.setTextInRange(buffer.getRange(), @parinferText, undo: 'skip')

  calculateDx: (change) ->
    {oldExtent, newExtent, start} = change
    if oldExtent.row == newExtent.row
      newExtent.column - oldExtent.column
    else if oldExtent.row < newExtent.row # Added a line
      newExtent.column - start.column
    else # Removed a line
      start.column - oldExtent.column

  setTitleBarItem: (item) ->
    @item = item
    @updateStatusBar(atom.workspace.getActiveTextEditor())

  updateStatusBar: (editor) ->
    return unless @item
    modes = @parinferEditors.get(editor)
    if modes
      @item.innerHTML = "Parinfer: #{modes[1]} mode"
    else
      @item.innerHTML = ""
