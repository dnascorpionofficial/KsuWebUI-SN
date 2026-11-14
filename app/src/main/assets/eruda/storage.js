(function() {
  try {
    if (window.__erudaAddStorage) return;
    window.__erudaAddStorage = true;
    var ICON_ADD = '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="display:block"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>';
    var ICON_EDIT = '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="display:block"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>';
    function makeBtn(cls, title, icon) {
      var b = document.createElement('div');
      b.className = 'eruda-btn ' + cls;
      b.title = title;
      b.innerHTML = icon;
      return b;
    }
    function install(section, storeName, area) {
      var host = document.getElementById('eruda');
      var root = host && (host.shadowRoot || host);
      var refresh = root && root.querySelector('.eruda-' + section + ' .eruda-refresh-storage');
      var tool = window.eruda && eruda.get && eruda.get('resources');
      var store = tool && tool[storeName];
      if (!refresh || !store) return false;
      if (root.querySelector('.eruda-' + section + ' .eruda-edit-storage')) return true;
      var add = makeBtn('eruda-add-storage', window.ksuText.add, ICON_ADD);
      var edit = makeBtn('eruda-edit-storage eruda-btn-disabled', window.ksuText.edit, ICON_EDIT);
      refresh.insertAdjacentElement('afterend', edit);
      refresh.insertAdjacentElement('afterend', add);
      refresh.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopImmediatePropagation();
        var choice = window.prompt('__ksu_reload__');
        if (choice === 'webui') {
          location.reload();
        } else if (choice === 'table') {
          store.refresh();
          sync();
          window.prompt('__ksu_refreshed__', '');
        }
      }, true);
      function sync() {
        if (store._selectedItem) edit.classList.remove('eruda-btn-disabled');
        else edit.classList.add('eruda-btn-disabled');
      }
      var orig = store._updateButtons;
      store._updateButtons = function() {
        orig.apply(this, arguments);
        sync();
      };
      function save(key, value) {
        try {
          area.setItem(key, value);
        } catch (e) {
          window.alert(String(e));
          return;
        }
        store.refresh();
        sync();
      }
      add.addEventListener('click', function() {
        var key = window.prompt(window.ksuText.key);
        if (key === null) return;
        key = key.trim();
        if (!key) return;
        if (area.getItem(key) !== null) {
          window.alert(window.ksuText.existsHint.replace('%s', key));
          return;
        }
        var value = window.prompt(window.ksuText.value, '');
        if (value === null) return;
        save(key, value);
      });
      var copyBtn = root.querySelector('.eruda-' + section + ' .eruda-copy-storage');
      var viewBtn = root.querySelector('.eruda-' + section + ' .eruda-show-detail');
      function pick(kind) {
        return window.prompt('__ksu_pick__:' + kind);
      }
      function showSource(type, val) {
        var src = window.eruda && eruda.get && eruda.get('sources');
        if (!src) return;
        src.set(type, val);
        eruda.show('sources');
      }
      if (copyBtn) {
        copyBtn.addEventListener('click', function(e) {
          e.preventDefault();
          e.stopImmediatePropagation();
          var key = store._selectedItem;
          if (!key) return;
          var c = pick('copy');
          if (c === 'key') window.prompt('__ksu_copy__', key);
          else if (c === 'value') window.prompt('__ksu_copy__', area.getItem(key) || '');
        }, true);
      }
      if (viewBtn) {
        viewBtn.addEventListener('click', function(e) {
          e.preventDefault();
          e.stopImmediatePropagation();
          var key = store._selectedItem;
          if (!key) return;
          var c = pick('view');
          if (c === 'key') {
            showSource('raw', key);
          } else if (c === 'value') {
            var v = area.getItem(key) || '';
            try {
              showSource('object', JSON.parse(v));
            } catch (err) {
              showSource('raw', v);
            }
          }
        }, true);
      }
      edit.addEventListener('click', function() {
        var key = store._selectedItem;
        if (!key) return;
        var c = pick('edit');
        if (c === 'value') {
          var value = window.prompt(window.ksuText.valueFor.replace('%s', key), area.getItem(key) || '');
          if (value === null) return;
          save(key, value);
        } else if (c === 'key') {
          var nk = window.prompt(window.ksuText.key, key);
          if (nk === null) return;
          nk = nk.trim();
          if (!nk || nk === key) return;
          if (area.getItem(nk) !== null) {
            window.alert(window.ksuText.exists.replace('%s', nk));
            return;
          }
          var old = area.getItem(key);
          try {
            area.setItem(nk, old === null ? '' : old);
            area.removeItem(key);
          } catch (err) {
            window.alert(String(err));
            return;
          }
          store._selectedItem = null;
          store.refresh();
          sync();
        }
      });
      var canvas = document.createElement('canvas');
      function fit() {
        try {
          var dg = store._dataGrid;
          if (!dg || !dg.options || !dg.container) return;
          var cols = dg.options.columns;
          var w = dg.container.offsetWidth;
          if (w <= 0) return;
          var cs = getComputedStyle(dg.container);
          var ctx = canvas.getContext('2d');
          ctx.font = cs.fontSize + ' ' + cs.fontFamily;
          var max = ctx.measureText('Key').width;
          var data = store._storeData || [];
          for (var i = 0; i < data.length; i++) {
            var m = ctx.measureText(String(data[i].key)).width;
            if (m > max) max = m;
          }
          var scale = (parseFloat(cs.fontSize) || 12) / 12;
          var keyPx = Math.ceil(max + 10 * scale);
          keyPx = Math.max(40, Math.min(keyPx, Math.floor(w * 0.6)));
          var total = cols[0].weight + cols[1].weight;
          cols[0].weight = total * keyPx / w;
          cols[1].weight = total - cols[0].weight;
          dg.applyColWeights();
        } catch (err) {}
      }
      var origRefresh = store.refresh;
      store.refresh = function() {
        origRefresh.apply(this, arguments);
        fit();
      };
      window.addEventListener('resize', fit);
      fit();
      var delBtn = root.querySelector('.eruda-' + section + ' .eruda-delete-storage');
      var clrBtn = root.querySelector('.eruda-' + section + ' .eruda-clear-storage');
      var clearText = section === 'local-storage' ? window.ksuText.clearLocal : window.ksuText.clearSession;
      if (window.__ksuGuard) {
        window.__ksuGuard(delBtn, function() {
          var k = store._selectedItem;
          return k ? window.ksuText.deleteItem.replace('%s', k) : undefined;
        });
        window.__ksuGuard(clrBtn, function() {
          return clearText;
        });
      }
      return true;
    }
    var tries = 0;
    var timer = setInterval(function() {
      var a = install('local-storage', '_localStorage', localStorage);
      var b = install('session-storage', '_sessionStorage', sessionStorage);
      if ((a && b) || ++tries > 40) clearInterval(timer);
    }, 250);
  } catch (e) {}
})();
