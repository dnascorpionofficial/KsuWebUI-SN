window.ksuToolKeys = function (names) {
  var devtools = names.some(function (k) { return /^vue-devtools/.test(k); });
  return function (k) {
    if (/^eruda-/.test(k) || k === 'active-eruda' || /^vue-devtools/.test(k)) return true;
    return devtools && (/^(split-pane|timeline\.)/.test(k) || k === 'route' || k === 'previous-session-theme');
  };
};

window.ksuPluginStorage = function () {
  if (window.ksuPluginStorage.on) return;
  window.ksuPluginStorage.on = true;
  var prefix = 'eruda-plugin:';
  var proto = Storage.prototype;
  var get = proto.getItem;
  var set = proto.setItem;
  var remove = proto.removeItem;
  function fromPlugin() {
    var limit = Error.stackTraceLimit;
    Error.stackTraceLimit = 4;
    var stack = new Error().stack;
    Error.stackTraceLimit = limit;
    var line = typeof stack === 'string' ? stack.split('\n')[3] : '';
    return !!line && line.indexOf('cdn.jsdelivr.net/npm/eruda-') !== -1;
  }
  proto.getItem = function (k) { return get.call(this, fromPlugin() ? prefix + k : k); };
  proto.setItem = function (k, v) { return set.call(this, fromPlugin() ? prefix + k : k, v); };
  proto.removeItem = function (k) { return remove.call(this, fromPlugin() ? prefix + k : k); };
};
