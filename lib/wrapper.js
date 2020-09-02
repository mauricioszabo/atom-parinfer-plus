var wasm_p = require(__dirname + '/parinfer_rust.js');
var wasm
wasm_p.then (e => wasm = e )

function mode(mode) {
  return function(text, options) {
    return JSON.parse(wasm.run_parinfer(JSON.stringify({
      mode: mode,
      text: text,
      options: options
    })));
  };
}

module.exports = {
  wasm_p: wasm_p,
  run_parinfer: async (json) => { (await wasm_p).run_parinfer(json) },
  indentMode: mode('indent'),
  parenMode: mode('paren'),
  smartMode: mode('smart')
};
