(function() {
  window.__ksuGuard = function(btn, detail) {
    if (!btn || btn.__ksuGuard) return;
    btn.__ksuGuard = true;
    btn.addEventListener('click', function(e) {
      if (btn.__ksuOk) {
        btn.__ksuOk = false;
        return;
      }
      var text = detail();
      if (text === undefined) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      if (text === null) return;
      if (window.prompt('__ksu_confirm__', text) === 'yes') {
        btn.__ksuOk = true;
        (e.target || btn).click();
      }
    }, true);
  };
})();
