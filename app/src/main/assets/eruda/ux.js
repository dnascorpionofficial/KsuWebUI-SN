(function() {
  try {
    if (window.__ksuUx) return;
    window.__ksuUx = true;
    var host = document.getElementById('eruda');
    var root = host && (host.shadowRoot || host);
    if (!host || !root) return;
    var st = document.createElement('style');
    st.textContent = '.eruda-container .luna-tab-item:hover:not(.luna-tab-selected) { background: none !important; color: var(--foreground) !important; } '
      + '.luna-setting-item { -webkit-tap-highlight-color: transparent; } '
      + '.luna-setting-item:hover, .luna-setting-item.luna-setting-selected { background: none !important; }';
    root.appendChild(st);
    function info(e) {
      var path = e.composedPath ? e.composedPath() : [];
      var inPanel = false;
      var skip = false;
      for (var i = 0; i < path.length; i++) {
        var n = path[i];
        if (!n || !n.classList) continue;
        if (n.classList.contains('eruda-dev-tools')) inPanel = true;
        if (n.classList.contains('eruda-resizer') || n.classList.contains('luna-data-grid-resizer') || n.classList.contains('eruda-entry-btn')) skip = true;
      }
      return { path: path, inPanel: inPanel, skip: skip };
    }
    function hScrollable(path) {
      for (var i = 0; i < path.length; i++) {
        var n = path[i];
        if (!n || n.nodeType !== 1) continue;
        if (n.classList && n.classList.contains('eruda-dev-tools')) break;
        var tag = n.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA' || n.isContentEditable) return true;
        if (n.classList && n.classList.contains('luna-tab')) return true;
        var cs = getComputedStyle(n);
        if ((cs.overflowX === 'auto' || cs.overflowX === 'scroll') && n.scrollWidth > n.clientWidth + 1) return true;
      }
      return false;
    }
    function switchTool(step) {
      var sel = root.querySelector('.luna-tab-item.luna-tab-selected');
      var next = sel;
      while (next) {
        next = step > 0 ? next.nextElementSibling : next.previousElementSibling;
        if (next && next.classList && next.classList.contains('luna-tab-item')) break;
      }
      if (next) next.click();
    }
    var sx = 0;
    var sy = 0;
    var sInfo = null;
    host.addEventListener('touchstart', function(e) {
      sInfo = info(e);
      var t = e.touches[0];
      if (t) { sx = t.clientX; sy = t.clientY; }
    }, { passive: true });
    host.addEventListener('touchend', function(e) {
      if (!sInfo || !sInfo.inPanel || sInfo.skip) return;
      var t = e.changedTouches[0];
      if (!t) return;
      var dx = t.clientX - sx;
      var dy = t.clientY - sy;
      if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.8 && !hScrollable(sInfo.path)) switchTool(dx < 0 ? 1 : -1);
    }, { passive: true });
    root.addEventListener('click', function(e) {
      var t = e.target;
      if (!t || !t.closest || t.closest('input')) return;
      var row = t.closest('.luna-setting-item-checkbox');
      if (!row) return;
      var input = row.querySelector('input[type="checkbox"]');
      if (!input || input.disabled) return;
      e.preventDefault();
      input.checked = !input.checked;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }, true);
    function fixLevels() {
      var ctrl = root.querySelector('.eruda-console .eruda-control');
      if (!ctrl) return false;
      if (ctrl.__ksuLevels) return true;
      ctrl.__ksuLevels = true;
      ctrl.addEventListener('click', function(e) {
        var t = e.target;
        while (t && t !== ctrl && !(t.classList && t.classList.contains('eruda-level'))) t = t.parentNode;
        if (!t || t === ctrl) return;
        var items = ctrl.querySelectorAll('.eruda-level');
        for (var i = 0; i < items.length; i++) {
          if (items[i] === t) items[i].classList.add('eruda-active');
          else items[i].classList.remove('eruda-active');
        }
      });
      return true;
    }
    function guards() {
      var g = window.__ksuGuard;
      if (!g) return false;
      var consoleBtn = root.querySelector('.eruda-console .eruda-clear-console');
      var netBtn = root.querySelector('.eruda-network .eruda-clear-request');
      var nodeBtn = root.querySelector('.eruda-elements .eruda-delete-node');
      var restoreBtn = null;
      var items = root.querySelectorAll('.luna-setting-item-button');
      for (var i = 0; i < items.length; i++) {
        if ((items[i].textContent || '').indexOf('Restore defaults') >= 0) restoreBtn = items[i];
      }
      g(consoleBtn, function() { return window.ksuText.clearConsole; });
      g(netBtn, function() { return window.ksuText.clearNetwork; });
      g(nodeBtn, function() {
        var el = window.eruda && eruda.get && eruda.get('elements');
        var n = el && el._curNode;
        if (!n || n.nodeType !== 1) return undefined;
        var label = n.tagName.toLowerCase();
        if (n.id) label += '#' + n.id;
        else if (n.classList && n.classList.length) label += '.' + n.classList[0];
        return window.ksuText.deleteItem.replace('%s', label);
      });
      g(restoreBtn, function() { return window.ksuText.restoreDefaults; });
      return !!(consoleBtn && netBtn && nodeBtn && restoreBtn);
    }
    var tries = 0;
    var timer = setInterval(function() {
      var a = fixLevels();
      var b = guards();
      if ((a && b) || ++tries > 40) clearInterval(timer);
    }, 250);
  } catch (e) {}
})();
