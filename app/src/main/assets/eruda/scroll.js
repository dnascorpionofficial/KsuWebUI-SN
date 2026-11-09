(function() {
  try {
    if (window.__ksuScroll) return;
    window.__ksuScroll = true;
    var dt = eruda._devTools;
    if (!dt || !dt.on || !eruda.Settings) return;
    var cfg = eruda.Settings.createCfg('ksu-webui', { bgScroll: false });
    var saved = null;
    function targets() {
      return [document.documentElement, document.body];
    }
    function lock(on) {
      var els = targets();
      if (on) {
        if (saved) return;
        saved = els.map(function(el) {
          return el ? [el.style.getPropertyValue('overflow'), el.style.getPropertyPriority('overflow')] : null;
        });
        els.forEach(function(el) {
          if (el) el.style.setProperty('overflow', 'hidden', 'important');
        });
      } else {
        if (!saved) return;
        els.forEach(function(el, i) {
          if (!el || !saved[i]) return;
          if (saved[i][0]) el.style.setProperty('overflow', saved[i][0], saved[i][1]);
          else el.style.removeProperty('overflow');
        });
        saved = null;
      }
    }
    function sync() {
      lock(!!dt._isShow && !cfg.get('bgScroll'));
    }
    dt.on('show', sync);
    dt.on('hide', sync);
    cfg.on('change', function(k) {
      if (k === 'bgScroll') sync();
    });
    var settings = eruda.get('settings');
    if (settings) settings.separator().switch(cfg, 'bgScroll', window.ksuText.bgScroll);
  } catch (e) {}
})();
