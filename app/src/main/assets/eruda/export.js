(function () {
  try {
    if (!window.eruda || !eruda.Tool || window.__erudaExport) return;
    window.__erudaExport = true;

    var T = window.ksuText || {};
    var CSS = [
      '.kex-wrap{height:100%;overflow-y:auto;box-sizing:border-box;padding:12px;color:var(--foreground,inherit);font-size:13px}',
      '.kex-main{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;box-sizing:border-box;padding:12px;text-align:center;min-height:100%}',
      '.kex-title{font-size:15px;font-weight:bold;margin:0 0 8px}',
      '.kex-desc{max-width:320px;font-size:13px;line-height:1.5;opacity:.7;margin:0}',
      '.kex-open{padding:10px 22px;border:0;border-radius:22px;background:var(--accent,#4a8cff);color:#fff;font-size:14px;font-weight:bold}',
      '.kex-open[disabled]{opacity:.5}',
      '.kex-btn{padding:10px 20px;border:1px solid rgba(128,128,128,.55);border-radius:6px;background:rgba(128,128,128,.22);color:var(--foreground,inherit);font-size:13px}',
      '.kex-btn:active{background:rgba(128,128,128,.4)}',
      '.kex-btn.main{border-color:transparent;background:var(--accent,#4a8cff);color:#fff;font-weight:bold}',
      '.kex-btn.main:active{opacity:.8}',
      '.kex-btn[disabled]{opacity:.5}',
      '.kex-list{border:1px solid rgba(128,128,128,.4);border-radius:4px;margin-bottom:14px}',
      '.kex-row{display:flex;align-items:center;gap:12px;padding:14px 12px;border-bottom:1px solid rgba(128,128,128,.4)}',
      '.kex-row:last-child{border-bottom:0}',
      '.kex-row.all{background:rgba(128,128,128,.14);font-weight:bold}',
      '.kex-row.off{opacity:.35;pointer-events:none}',
      '.kex-row input{width:18px;height:18px;margin:0;accent-color:var(--accent,#4a8cff)}',
      '.kex-row span{flex:1}',
      '.kex-row small{font-size:11px;opacity:.8}',
      '.kex-bar{display:flex;gap:8px}',
      '.kex-bar .kex-btn{flex:1}'
    ].join('');

    var ITEMS = [
      { id: 'local', label: 'exportItemLocal', file: 'localStorage.json', run: collectLocal, has: hasLocal },
      { id: 'session', label: 'exportItemSession', file: 'sessionStorage.json', run: collectSession, has: hasSession },
      { id: 'cookies', label: 'exportItemCookies', file: 'cookies.json', run: collectCookies, has: hasCookies },
      { id: 'indexeddb', label: 'exportItemIndexeddb', file: 'indexedDB.json', run: collectIndexedDb, has: hasIndexedDb },
      { id: 'cache', label: 'exportItemCache', file: 'cacheStorage.json', run: collectCache, has: hasCache },
      { id: 'executions', label: 'exportItemExecutions', file: 'executions.txt', run: collectExecutions, has: hasExecutions },
      { id: 'network', label: 'exportItemNetwork', file: 'network.json', run: collectNetwork, has: hasNetwork },
      { id: 'console', label: 'exportItemConsole', file: 'console.txt', run: collectConsole, has: hasConsole }
    ];

    var picked = {};
    var found = {};
    var busy = false;
    var ui = {};

    function el(tag, cls, text) {
      var n = document.createElement(tag);
      if (cls) n.className = cls;
      if (text != null) n.textContent = text;
      return n;
    }

    function pad(n) { return (n < 10 ? '0' : '') + n; }

    function stamp() {
      var d = new Date();
      return pad(d.getDate()) + '-' + pad(d.getMonth() + 1) + '-' + d.getFullYear() + '_' +
        pad(d.getHours()) + '-' + pad(d.getMinutes()) + '-' + pad(d.getSeconds());
    }

    function root() {
      var host = document.getElementById('eruda');
      return host && (host.shadowRoot || host);
    }

    function json(v) { return JSON.stringify(v, null, 2); }

    function toBase64(buf) {
      var bytes = new Uint8Array(buf);
      var out = '';
      for (var i = 0; i < bytes.length; i += 0x8000) {
        out += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
      }
      return btoa(out);
    }

    function blobToBase64(blob) {
      return new Promise(function (resolve) {
        var r = new FileReader();
        r.onload = function () { resolve(String(r.result).split(',')[1] || ''); };
        r.onerror = function () { resolve(''); };
        r.readAsDataURL(blob);
      });
    }

    async function enc(v, seen, depth) {
      if (v === null || v === undefined) return v === undefined ? { __type: 'undefined' } : null;
      var t = typeof v;
      if (t === 'string' || t === 'boolean') return v;
      if (t === 'number') return isFinite(v) ? v : { __type: 'number', value: String(v) };
      if (t === 'bigint') return { __type: 'bigint', value: String(v) };
      if (t !== 'object') return { __type: t };
      if (depth > 30) return '[Max depth]';
      if (v instanceof Date) return { __type: 'Date', iso: isNaN(v) ? 'Invalid' : v.toISOString() };
      if (v instanceof Blob) {
        return { __type: v instanceof File ? 'File' : 'Blob', name: v.name, mime: v.type, size: v.size, base64: await blobToBase64(v) };
      }
      if (v instanceof ArrayBuffer) return { __type: 'ArrayBuffer', base64: toBase64(v) };
      if (ArrayBuffer.isView(v)) {
        return { __type: v.constructor.name, base64: toBase64(v.buffer.slice(v.byteOffset, v.byteOffset + v.byteLength)) };
      }
      if (seen.has(v)) return '[Circular]';
      seen.add(v);
      var out;
      if (Array.isArray(v)) {
        out = [];
        for (var i = 0; i < v.length; i++) out.push(await enc(v[i], seen, depth + 1));
      } else if (v instanceof Map) {
        out = { __type: 'Map', entries: [] };
        for (var pair of v) out.entries.push([await enc(pair[0], seen, depth + 1), await enc(pair[1], seen, depth + 1)]);
      } else if (v instanceof Set) {
        out = { __type: 'Set', values: [] };
        for (var item of v) out.values.push(await enc(item, seen, depth + 1));
      } else {
        out = {};
        var keys = Object.keys(v);
        for (var k = 0; k < keys.length; k++) {
          if (v[keys[k]] !== undefined) out[keys[k]] = await enc(v[keys[k]], seen, depth + 1);
        }
      }
      seen.delete(v);
      return out;
    }

    function isErudaKey(k) {
      return /^eruda/.test(k) || k === 'active-eruda';
    }

    function unquote(v) {
      if (typeof v === 'string') {
        if (v.length > 1 && v.charAt(0) === '"' && v.indexOf('"', 1) === v.length - 1) return v.slice(1, -1);
        return v;
      }
      if (Array.isArray(v)) return v.map(unquote);
      if (v && typeof v === 'object') {
        var out = {};
        Object.keys(v).forEach(function (k) { out[k] = unquote(v[k]); });
        return out;
      }
      return v;
    }

    function readable(v) {
      if (typeof v === 'string' && /^\s*[\[{]/.test(v)) {
        try { return unquote(JSON.parse(v)); } catch (e) {}
      }
      return unquote(v);
    }

    function isPlainObject(v) {
      return v !== null && typeof v === 'object' && !Array.isArray(v);
    }

    function inline(v) {
      if (Array.isArray(v)) return '[' + v.map(inline).join(', ') + ']';
      if (isPlainObject(v)) {
        var ks = Object.keys(v);
        if (!ks.length) return '{}';
        return '{ ' + ks.map(function (k) { return JSON.stringify(k) + ': ' + inline(v[k]); }).join(', ') + ' }';
      }
      return JSON.stringify(v);
    }

    function entry(key, val, indent) {
      var prefix = indent + JSON.stringify(key) + ': ';
      if (!isPlainObject(val) || !Object.keys(val).length) return prefix + inline(val);
      var pad = new Array(prefix.length + 3).join(' ');
      var rows = Object.keys(val).map(function (k, i) {
        return (i ? pad : prefix + '{ ') + JSON.stringify(k) + ': ' + inline(val[k]);
      });
      return rows.join(',\n') + ' }';
    }

    function section(name, obj) {
      var keys = Object.keys(obj);
      if (!keys.length) return '  ' + JSON.stringify(name) + ': {}';
      return '  ' + JSON.stringify(name) + ': {\n' +
        keys.map(function (k) { return entry(k, obj[k], '    '); }).join(',\n') + '\n  }';
    }

    function dumpArea(area) {
      var groups = { eruda: {}, module: {} };
      var keys = [];
      for (var i = 0; i < area.length; i++) keys.push(area.key(i));
      keys.sort();
      keys.forEach(function (k) {
        groups[isErudaKey(k) ? 'eruda' : 'module'][k] = readable(area.getItem(k));
      });
      return '{\n' + section('eruda', groups.eruda) + ',\n' + section('module', groups.module) + '\n}\n';
    }

    async function collectLocal() { return dumpArea(localStorage); }

    async function collectSession() { return dumpArea(sessionStorage); }

    async function collectCookies() {
      var out = {};
      document.cookie.split(';').forEach(function (part) {
        var i = part.indexOf('=');
        var name = (i < 0 ? part : part.slice(0, i)).trim();
        if (!name) return;
        var val = i < 0 ? '' : part.slice(i + 1).trim();
        try { val = decodeURIComponent(val); } catch (e) {}
        out[name] = val;
      });
      return json(out);
    }

    function reqP(r) {
      return new Promise(function (resolve, reject) {
        r.onsuccess = function () { resolve(r.result); };
        r.onerror = function () { reject(r.error); };
      });
    }

    async function dumpStore(db, name) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(name, 'readonly');
        var store = tx.objectStore(name);
        var meta = {
          keyPath: store.keyPath,
          autoIncrement: store.autoIncrement,
          indexes: Array.prototype.map.call(store.indexNames, function (n) {
            var ix = store.index(n);
            return { name: n, keyPath: ix.keyPath, unique: ix.unique, multiEntry: ix.multiEntry };
          })
        };
        var rows = [];
        var req = store.openCursor();
        req.onerror = function () { reject(req.error); };
        req.onsuccess = function () {
          var c = req.result;
          if (c) { rows.push({ key: c.key, value: c.value }); c.continue(); }
        };
        tx.oncomplete = function () {
          meta.rows = rows;
          resolve(meta);
        };
        tx.onerror = function () { reject(tx.error); };
      });
    }

    async function collectIndexedDb() {
      if (!indexedDB.databases) throw new Error('indexedDB.databases() is not supported');
      var list = await indexedDB.databases();
      var out = {};
      for (var i = 0; i < list.length; i++) {
        var info = list[i];
        var db = await reqP(indexedDB.open(info.name));
        try {
          var entry = { version: db.version, stores: {} };
          var names = Array.prototype.slice.call(db.objectStoreNames);
          for (var s = 0; s < names.length; s++) {
            entry.stores[names[s]] = await dumpStore(db, names[s]);
          }
          out[info.name] = await enc(entry, new WeakSet(), 0);
        } finally {
          db.close();
        }
      }
      return json(out);
    }

    async function collectCache() {
      if (!window.caches) throw new Error('Cache storage is not supported');
      var names = await caches.keys();
      var out = {};
      for (var i = 0; i < names.length; i++) {
        var cache = await caches.open(names[i]);
        var reqs = await cache.keys();
        out[names[i]] = [];
        for (var j = 0; j < reqs.length; j++) {
          var res = await cache.match(reqs[j]);
          out[names[i]].push({
            url: reqs[j].url,
            method: reqs[j].method,
            status: res ? res.status : null,
            type: res ? res.headers.get('content-type') : null,
            length: res ? res.headers.get('content-length') : null
          });
        }
      }
      return json(out);
    }

    async function collectExecutions() {
      return window.__ksuExecText ? window.__ksuExecText() : '';
    }

    var BODY_EXT = {
      html: 'html', json: 'json', javascript: 'js', css: 'css', xml: 'xml',
      plain: 'txt', svg: 'svg', csv: 'csv'
    };

    function bodyFileName(r, n) {
      var base = String(r.name || 'request').replace(/[^A-Za-z0-9._-]+/g, '_').slice(0, 60) || 'request';
      var ext = BODY_EXT[String(r.subType || '').toLowerCase()] || 'txt';
      var hasExt = /\.[A-Za-z0-9]{1,5}$/.test(base);
      return 'network/' + String(1000 + n).slice(1) + '-' + base + (hasExt ? '' : '.' + ext);
    }

    async function collectNetwork() {
      var net = window.eruda && eruda.get && eruda.get('network');
      var list = net && net.requests ? net.requests() : [];
      var files = [];
      var out = [];
      for (var i = 0; i < list.length; i++) {
        var r = list[i];
        var item = {
          name: r.name, url: r.url, method: r.method, status: r.status,
          type: r.type, subType: r.subType, reportedSize: r.size, time: r.time
        };
        if (r.startTime) item.startedAt = new Date(r.startTime).toISOString();
        if (r.reqHeaders) item.reqHeaders = unquote(r.reqHeaders);
        if (r.resHeaders) item.resHeaders = unquote(r.resHeaders);
        if (r.data !== undefined && r.data !== null && r.data !== '') {
          item.requestData = await enc(r.data, new WeakSet(), 0);
        }
        var body = r.resTxt;
        if (body !== undefined && body !== null && body !== '') {
          var text;
          if (typeof body === 'object') {
            text = json(body);
          } else {
            text = String(body);
            if (String(r.subType).toLowerCase() === 'json') {
              try { text = json(JSON.parse(text)); } catch (e) {}
            }
          }
          item.bodyFile = bodyFileName(r, i + 1);
          item.bodyBytes = new TextEncoder().encode(text).length;
          files.push({ name: item.bodyFile, data: text });
        } else {
          item.bodyFile = null;
        }
        out.push(item);
      }
      files.unshift({ name: 'network.json', data: json(out) + '\n' });
      return files;
    }

    function cleanConsoleText(raw) {
      var lines = String(raw || '').replace(/\r/g, '').split('\n');
      var out = [];
      lines.forEach(function (line) {
        line.replace(/\s{2,}(?=at\s)/g, '\n    ').split('\n').forEach(function (part, i) {
          var t = i ? part.replace(/\s+$/, '') : part.trim();
          if (t.trim()) out.push(i ? '    ' + t.trim() : t);
        });
      });
      return out;
    }

    async function collectConsole() {
      var r = root();
      var nodes = r ? r.querySelectorAll('.eruda-console .luna-console-log-item') : [];
      var types = ['error', 'warn', 'info', 'debug', 'output', 'input', 'log'];
      var entries = [];
      Array.prototype.forEach.call(nodes, function (n) {
        var type = 'log';
        for (var i = 0; i < types.length; i++) {
          if (n.classList.contains('luna-console-' + types[i])) { type = types[i]; break; }
        }
        var lines = cleanConsoleText(n.innerText || n.textContent);
        if (!lines.length) return;
        lines[0] = '[' + type + '] ' + lines[0];
        entries.push(lines.join('\n'));
      });
      return entries.join('\n\n') + (entries.length ? '\n' : '');
    }

    function hasLocal() { return localStorage.length > 0; }

    function hasSession() { return sessionStorage.length > 0; }

    function hasCookies() { return document.cookie.trim() !== ''; }

    async function hasIndexedDb() {
      return !!indexedDB.databases && (await indexedDB.databases()).length > 0;
    }

    async function hasCache() {
      return !!window.caches && (await caches.keys()).length > 0;
    }

    async function hasExecutions() {
      return !!window.__ksuExecText && !!window.__ksuExecText();
    }

    async function hasNetwork() {
      var net = window.eruda && eruda.get && eruda.get('network');
      return !!net && !!net.requests && net.requests().length > 0;
    }

    async function hasConsole() {
      var r = root();
      return !!r && r.querySelectorAll('.eruda-console .luna-console-log-item').length > 0;
    }

    var CRC_TABLE = (function () {
      var t = new Uint32Array(256);
      for (var n = 0; n < 256; n++) {
        var c = n;
        for (var k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        t[n] = c >>> 0;
      }
      return t;
    })();

    function crc32(bytes) {
      var c = 0xFFFFFFFF;
      for (var i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
      return (c ^ 0xFFFFFFFF) >>> 0;
    }

    async function deflate(bytes) {
      if (typeof CompressionStream !== 'function') return null;
      try {
        var stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream('deflate-raw'));
        return new Uint8Array(await new Response(stream).arrayBuffer());
      } catch (e) {
        return null;
      }
    }

    async function buildZip(files) {
      var enc8 = new TextEncoder();
      var d = new Date();
      var dosTime = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
      var dosDate = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
      var parts = [];
      var central = [];
      var offset = 0;
      for (var i = 0; i < files.length; i++) {
        var name = enc8.encode(files[i].name);
        var data = typeof files[i].data === 'string' ? enc8.encode(files[i].data) : files[i].data;
        var crc = crc32(data);
        var method = 0;
        var body = data;
        if (data.length > 64) {
          var z = await deflate(data);
          if (z && z.length < data.length) { method = 8; body = z; }
        }
        var lh = new DataView(new ArrayBuffer(30));
        lh.setUint32(0, 0x04034b50, true);
        lh.setUint16(4, 20, true);
        lh.setUint16(6, 0x0800, true);
        lh.setUint16(8, method, true);
        lh.setUint16(10, dosTime, true);
        lh.setUint16(12, dosDate, true);
        lh.setUint32(14, crc, true);
        lh.setUint32(18, body.length, true);
        lh.setUint32(22, data.length, true);
        lh.setUint16(26, name.length, true);
        lh.setUint16(28, 0, true);
        parts.push(lh.buffer, name, body);
        var ch = new DataView(new ArrayBuffer(46));
        ch.setUint32(0, 0x02014b50, true);
        ch.setUint16(4, 20, true);
        ch.setUint16(6, 20, true);
        ch.setUint16(8, 0x0800, true);
        ch.setUint16(10, method, true);
        ch.setUint16(12, dosTime, true);
        ch.setUint16(14, dosDate, true);
        ch.setUint32(16, crc, true);
        ch.setUint32(20, body.length, true);
        ch.setUint32(24, data.length, true);
        ch.setUint16(28, name.length, true);
        ch.setUint32(42, offset, true);
        central.push(ch.buffer, name);
        offset += 30 + name.length + body.length;
      }
      var cdSize = 0;
      central.forEach(function (c) { cdSize += c.byteLength !== undefined ? c.byteLength : c.length; });
      var end = new DataView(new ArrayBuffer(22));
      end.setUint32(0, 0x06054b50, true);
      end.setUint16(8, files.length, true);
      end.setUint16(10, files.length, true);
      end.setUint32(12, cdSize, true);
      end.setUint32(16, offset, true);
      return new Blob(parts.concat(central, [end.buffer]), { type: 'application/zip' });
    }

    function saveBlob(blob, fileName) {
      return new Promise(function (resolve, reject) {
        var r = new FileReader();
        r.onload = function () {
          try {
            window.ksu_download.save(String(r.result).split(',')[1] || '', fileName, 'application/zip');
            resolve();
          } catch (e) { reject(e); }
        };
        r.onerror = function () { reject(r.error || new Error('read failed')); };
        r.readAsDataURL(blob);
      });
    }

    async function runExport() {
      var chosen = ITEMS.filter(function (it) {
        return picked[it.id] && found[it.id];
      });
      if (!chosen.length) { window.alert(T.exportNone); return; }
      setBusy(true);
      try {
        var files = [];
        var failed = {};
        for (var i = 0; i < chosen.length; i++) {
          try {
            var res = await chosen[i].run();
            if (Array.isArray(res)) files = files.concat(res);
            else files.push({ name: chosen[i].file, data: res });
          } catch (e) {
            failed[chosen[i].id] = String(e && e.message || e);
            files.push({ name: chosen[i].file + '.error.txt', data: failed[chosen[i].id] + '\n' });
          }
        }
        files.unshift({
          name: 'info.json',
          data: json({
            exportedAt: new Date().toISOString(),
            url: location.href,
            userAgent: navigator.userAgent,
            items: chosen.map(function (it) { return it.id; }),
            failed: failed
          })
        });
        var zip = await buildZip(files);
        await saveBlob(zip, 'KsuWebUI-SN_export-' + stamp() + '.zip');
        showMain();
      } catch (e) {
        window.alert((T.exportFailed || '%s').replace('%s', String(e && e.message || e)));
      } finally {
        setBusy(false);
      }
    }

    function setBusy(on) {
      busy = on;
      ui.go.disabled = on;
      ui.cancel.disabled = on;
      ui.go.textContent = on ? T.exportWorking : T.exportConfirm;
    }

    function syncAll() {
      var avail = ITEMS.filter(function (it) { return found[it.id]; });
      ui.all.disabled = !avail.length;
      ui.all.checked = avail.length > 0 && avail.every(function (it) { return picked[it.id]; });
      ui.allRow.classList.toggle('off', !avail.length);
    }

    function showMain() {
      ui.main.style.display = '';
      ui.pick.style.display = 'none';
    }

    async function showPick() {
      ui.open.disabled = true;
      var res = await Promise.all(ITEMS.map(function (it) {
        return Promise.resolve().then(it.has).then(Boolean, function () { return false; });
      }));
      ITEMS.forEach(function (it, i) {
        found[it.id] = res[i];
        picked[it.id] = false;
      });
      ITEMS.filter(function (it) { return found[it.id]; })
        .concat(ITEMS.filter(function (it) { return !found[it.id]; }))
        .forEach(function (it) {
          var r = ui.rows[it.id];
          r.querySelector('input').checked = false;
          r.querySelector('input').disabled = !found[it.id];
          r.classList.toggle('off', !found[it.id]);
          r.querySelector('small').style.display = found[it.id] ? 'none' : '';
          ui.list.appendChild(r);
        });
      syncAll();
      ui.open.disabled = false;
      ui.main.style.display = 'none';
      ui.pick.style.display = '';
    }

    function buildUi(host) {
      var wrap = el('div', 'kex-wrap');
      wrap.appendChild(el('style', null, CSS));

      ui.main = el('div', 'kex-main');
      ui.main.appendChild(el('p', 'kex-desc', T.exportDesc));
      var open = el('button', 'kex-open', T.exportButton);
      open.addEventListener('click', showPick);
      ui.open = open;
      ui.main.appendChild(open);

      ui.pick = el('div');
      ui.pick.style.display = 'none';
      ui.pick.appendChild(el('h3', 'kex-title', T.exportChoose));
      var list = el('div', 'kex-list');
      ui.list = list;

      function row(label, cls) {
        var r = el('label', 'kex-row' + (cls ? ' ' + cls : ''));
        var input = el('input');
        input.type = 'checkbox';
        r.appendChild(input);
        r.appendChild(el('span', null, label));
        r.appendChild(el('small', null, T.exportNotFound));
        r.lastChild.style.display = 'none';
        return r;
      }

      var allRow = row(T.exportSelectAll, 'all');
      ui.all = allRow.querySelector('input');
      ui.allRow = allRow;
      ui.all.addEventListener('change', function () {
        ITEMS.forEach(function (it) {
          if (found[it.id]) {
            picked[it.id] = ui.all.checked;
            ui.rows[it.id].querySelector('input').checked = ui.all.checked;
          }
        });
      });
      list.appendChild(allRow);

      ui.rows = {};
      ITEMS.forEach(function (it) {
        var r = row(T[it.label] || it.id);
        r.querySelector('input').addEventListener('change', function (e) {
          picked[it.id] = e.target.checked;
          syncAll();
        });
        ui.rows[it.id] = r;
        list.appendChild(r);
      });
      ui.pick.appendChild(list);

      var bar = el('div', 'kex-bar');
      ui.cancel = el('button', 'kex-btn', T.exportCancel);
      ui.cancel.addEventListener('click', function () { if (!busy) showMain(); });
      ui.go = el('button', 'kex-btn main', T.exportConfirm);
      ui.go.addEventListener('click', function () { if (!busy) runExport(); });
      bar.appendChild(ui.cancel);
      bar.appendChild(ui.go);
      ui.pick.appendChild(bar);

      wrap.appendChild(ui.main);
      wrap.appendChild(ui.pick);
      host.appendChild(wrap);
    }

    class Export extends eruda.Tool {
      constructor() {
        super();
        this.name = 'Export';
      }
      init($el) {
        super.init($el);
        buildUi($el[0]);
      }
      show() {
        super.show();
        if (!busy) showMain();
        return this;
      }
    }

    eruda.add(new Export());

    try {
      var tab = eruda._devTools._tab, bar = tab.tabs;
      var q = function (id) { return bar.querySelector('[data-id="' + id + '"]'); };
      var self = q('Export');
      var anchor = q('Executions');
      if (anchor) bar.insertBefore(self, anchor.nextSibling);
      else bar.insertBefore(self, q('elements'));
      tab.updateSlider();
    } catch (e2) {}
  } catch (e) {}
})();
