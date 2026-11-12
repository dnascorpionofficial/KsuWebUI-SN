(function() {
  try {
    if (window.__ksuLive) return;
    window.__ksuLive = true;
    if (!eruda.Settings) return;
    var cfg = eruda.Settings.createCfg('ksu-livereload', { liveReload: false });
    function send() {
      window.prompt('__ksu_livereload__', cfg.get('liveReload') ? '1' : '0');
    }
    cfg.on('change', function(k) {
      if (k === 'liveReload') send();
    });
    var settings = eruda.get('settings');
    if (settings) settings.switch(cfg, 'liveReload', window.ksuText.liveReload);
    if (cfg.get('liveReload')) send();
  } catch (e) {}
})();
