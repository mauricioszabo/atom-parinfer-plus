par = require 'parinfer'

module.exports = class Parinfer
  parinferEditors: new Map()

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

  triggerFirstChange: (editor) ->
    {success, text, changedLines} = par.parenMode(editor.getText())
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
              editor.setText(text)
              true
            No: => false
    else
      atom.confirm
        message: 'Parinfer error'
        detailedMessage: "Your file is unbalanced. Check if there are missing parenthesis, and start Parinfer again.."
        buttons: ["Ok, Sorry..."]
      return false


  computeChange: (editor, change, cursorRange, mode) ->
    console.log("Making change", change, mode)
    {oldExtent, newExtent} = change
    opts = { cursorLine: cursorRange.end.row, cursorX: cursorRange.end.column }

    if mode == 'paren'
      opts.cursorDx = newExtent.column - oldExtent.column

    oldText = editor.getText()
    console.log "Parinfer", opts, change
    parResult = if mode == 'indent'
      par.indentMode(oldText, opts)
    else
      par.parenMode(oldText, opts)
    console.log "Parinfer result", parResult
    {text, success, cursorX} = parResult
    @parinferText = text
    if success && oldText != @parinferText
      if mode == 'paren'
        cursorRange.start.column = cursorX
        cursorRange.end.column = cursorX
      buffer = editor.getBuffer()
      buffer.setTextInRange(buffer.getRange(), @parinferText, undo: 'skip')

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
