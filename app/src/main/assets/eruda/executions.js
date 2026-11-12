(function () {
  try {
    var api = window.ksu_exec_log;
    if (!api || !window.eruda || !eruda.Tool || window.__erudaExec) return;
    window.__erudaExec = true;

    var ORDER_KEY = 'ksu-exec-order';
    var MAX_ENTRIES = 200;
    var CSS = [
      '.kx-wrap{display:flex;flex-direction:column;height:100%;color:var(--foreground);font-size:12px}',
      '.kx-tools{display:flex;flex-wrap:wrap;gap:6px;padding:6px;border-bottom:1px solid var(--border);background:var(--darker-background)}',
      '.kx-search{flex:1 1 100%;box-sizing:border-box;padding:6px 8px;border:1px solid var(--border);border-radius:4px;background:var(--background);color:var(--foreground);font-size:12px}',
      '.kx-btn{padding:5px 10px;border:1px solid var(--border);border-radius:4px;background:var(--background);color:var(--foreground);font-size:12px}',
      '.kx-btn.on{border-color:var(--accent);color:var(--accent)}',
      '.kx-list{flex:1;min-height:0;overflow-y:auto}',
      '.kx-empty{padding:16px;text-align:center;opacity:.6}',
      '.kx-row{padding:6px 8px;border-bottom:1px solid var(--border)}',
      '.kx-top{cursor:pointer}',
      '.kx-head{display:flex;align-items:center;gap:6px}',
      '.kx-mark{width:14px;text-align:center;font-weight:bold}',
      '.kx-row.ok .kx-mark{color:#4caf50}',
      '.kx-row.fail .kx-mark{color:var(--console-error-foreground)}',
      '.kx-row.run .kx-mark{color:var(--accent)}',
      '.kx-kind{opacity:.6}',
      '.kx-cmd{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-family:monospace}',
      '.kx-meta{margin-top:2px;padding-left:20px;opacity:.6}',
      '.kx-body{margin-top:6px}',
      '.kx-bar{display:flex;gap:6px;margin-bottom:6px}',
      '.kx-label{margin:6px 0 2px;font-size:10px;text-transform:uppercase;opacity:.6}',
      '.kx-pre{margin:0;padding:6px;border:1px solid var(--border);border-radius:4px;background:var(--darker-background);font-family:monospace;font-size:11px;white-space:pre-wrap;word-break:break-word;user-select:text;-webkit-user-select:text}',
      '.kx-errp{color:var(--console-error-foreground)}',
      '.kx-note{margin-top:6px;opacity:.6}'
    ].join('');

    var entries = [];
    var byId = {};
    var lastSeq = 0;
    var newestFirst = true;
    var onlyFailed = false;
    var query = '';
    var timer = null;
    var searchTimer = null;
    var ui = {};

    try { newestFirst = localStorage.getItem(ORDER_KEY) !== 'oldest'; } catch (e) {}

    function el(tag, cls, text) {
      var n = document.createElement(tag);
      if (cls) n.className = cls;
      if (text != null) n.textContent = text;
      return n;
    }

    function btn(label, onClick) {
      var b = el('button', 'kx-btn', label);
      b.addEventListener('click', onClick);
      return b;
    }

    function pad(n) { return (n < 10 ? '0' : '') + n; }

    function fmtTime(ms) {
      var d = new Date(ms);
      return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    }

    function fmtDur(ms) {
      return ms < 1000 ? ms + ' ms' : (ms / 1000).toFixed(ms < 10000 ? 2 : 1) + ' s';
    }

    function copy(text) { window.prompt('__ksu_copy__', text); }

    function outputText(e) {
      var sep = e.out && e.err && !/\n$/.test(e.out) ? '\n' : '';
      return e.out + sep + e.err;
    }

    function isFailed(e) { return e.done && e.code !== null && e.code !== 0; }

    function matches(e) {
      if (onlyFailed && !isFailed(e)) return false;
      if (!query) return true;
      return e.cmd.toLowerCase().indexOf(query) >= 0 ||
        e.out.toLowerCase().indexOf(query) >= 0 ||
        e.err.toLowerCase().indexOf(query) >= 0;
    }

    function build(e) {
      e.el = el('div', 'kx-row');
      var top = el('div', 'kx-top');
      var head = el('div', 'kx-head');
      e.mark = el('span', 'kx-mark');
      head.appendChild(e.mark);
      head.appendChild(el('span', 'kx-kind', e.kind));
      head.appendChild(el('span', 'kx-cmd', e.cmd));
      e.metaEl = el('div', 'kx-meta');
      top.appendChild(head);
      top.appendChild(e.metaEl);
      top.addEventListener('click', function () { toggle(e); });
      e.el.appendChild(top);
    }

    function buildBody(e) {
      var b = el('div', 'kx-body');
      var bar = el('div', 'kx-bar');
      bar.appendChild(btn('Copy command', function () { copy(e.cmd); }));
      bar.appendChild(btn('Copy output', function () { copy(outputText(e)); }));
      b.appendChild(bar);
      b.appendChild(el('div', 'kx-label', 'command'));
      b.appendChild(el('pre', 'kx-pre', e.cmd));
      if (e.opts) {
        b.appendChild(el('div', 'kx-label', 'options'));
        b.appendChild(el('pre', 'kx-pre', e.opts));
      }
      e.outLabel = el('div', 'kx-label', 'stdout');
      e.outPre = el('pre', 'kx-pre');
      e.outNode = document.createTextNode(e.out);
      e.outPre.appendChild(e.outNode);
      e.errLabel = el('div', 'kx-label', 'stderr');
      e.errPre = el('pre', 'kx-pre kx-errp');
      e.errNode = document.createTextNode(e.err);
      e.errPre.appendChild(e.errNode);
      e.noteEl = el('div', 'kx-note');
      b.appendChild(e.outLabel);
      b.appendChild(e.outPre);
      b.appendChild(e.errLabel);
      b.appendChild(e.errPre);
      b.appendChild(e.noteEl);
      e.body = b;
      e.el.appendChild(b);
    }

    function syncBody(e) {
      var hasOut = !!e.out;
      var hasErr = !!e.err;
      e.outLabel.style.display = e.outPre.style.display = hasOut ? '' : 'none';
      e.errLabel.style.display = e.errPre.style.display = hasErr ? '' : 'none';
      e.noteEl.textContent = e.trunc ? 'Output limit reached, the rest was not recorded.' :
        (e.done && !hasOut && !hasErr ? 'No output.' : '');
      e.noteEl.style.display = e.noteEl.textContent ? '' : 'none';
    }

    function toggle(e) {
      e.open = !e.open;
      if (e.open && !e.body) buildBody(e);
      if (e.body) e.body.style.display = e.open ? '' : 'none';
      refresh(e);
    }

    function refresh(e) {
      var st = !e.done ? 'run' : (isFailed(e) ? 'fail' : 'ok');
      e.el.className = 'kx-row ' + st;
      e.mark.textContent = st === 'run' ? '\u2026' : (st === 'fail' ? '\u2717' : '\u2713');
      var parts = [fmtTime(e.at)];
      if (!e.done) {
        parts.push('running ' + fmtDur(Date.now() - e.at));
      } else {
        parts.push('exit ' + (e.code === null ? '\u2013' : e.code));
        parts.push(fmtDur(e.ms));
      }
      e.metaEl.textContent = parts.join(' \u00b7 ');
      if (e.open && e.body) syncBody(e);
    }

    function layout() {
      var rows = entries.filter(matches);
      if (newestFirst) rows.reverse();
      ui.list.textContent = '';
      rows.forEach(function (e) { ui.list.appendChild(e.el); });
      ui.empty.textContent = entries.length ? 'No matching calls' : 'No calls yet';
      ui.empty.style.display = rows.length ? 'none' : '';
    }

    function poll() {
      var evs;
      try { evs = JSON.parse(api.events(lastSeq)); } catch (err) { return; }
      var relayout = false;
      for (var i = 0; i < evs.length; i++) {
        var ev = evs[i];
        lastSeq = ev.s;
        if (ev.t === 'start') {
          var ne = {
            id: ev.id, kind: ev.k, cmd: ev.c, opts: ev.o || '', at: ev.at,
            out: '', err: '', code: null, ms: 0, done: false, trunc: false, open: false, dirty: true
          };
          byId[ev.id] = ne;
          entries.push(ne);
          build(ne);
          if (entries.length > MAX_ENTRIES) delete byId[entries.shift().id];
          relayout = true;
          continue;
        }
        var e = byId[ev.id];
        if (!e) continue;
        if (ev.t === 'out') {
          e.out += ev.d;
          if (e.outNode) e.outNode.appendData(ev.d);
        } else if (ev.t === 'err') {
          e.err += ev.d;
          if (e.errNode) e.errNode.appendData(ev.d);
        } else if (ev.t === 'trunc') {
          e.trunc = true;
        } else if (ev.t === 'end') {
          e.done = true;
          e.code = ev.code;
          e.ms = ev.ms;
        }
        e.dirty = true;
        if (query || (onlyFailed && ev.t === 'end')) relayout = true;
      }
      entries.forEach(function (e) {
        if (e.dirty || !e.done) {
          e.dirty = false;
          refresh(e);
        }
      });
      if (relayout) layout();
    }

    function startPolling() {
      if (!timer) timer = setInterval(poll, 600);
    }

    function stopPolling() {
      if (timer) clearInterval(timer);
      timer = null;
    }

    function setOrderLabel() {
      ui.order.textContent = newestFirst ? 'Newest first \u2193' : 'Oldest first \u2191';
    }

    function clearAll() {
      try { api.clear(); } catch (err) {}
      entries = [];
      byId = {};
      layout();
    }

    function exportLog() {
      if (!entries.length) return;
      var text = entries.map(function (e) {
        var s = '#' + e.id + ' ' + e.kind + ' ' + new Date(e.at).toISOString() + ' ' +
          (e.done ? 'exit ' + (e.code === null ? '-' : e.code) + ' ' + e.ms + 'ms' : 'running') + '\n$ ' + e.cmd + '\n';
        if (e.opts) s += 'options: ' + e.opts + '\n';
        if (e.out) s += '--- stdout ---\n' + e.out + (/\n$/.test(e.out) ? '' : '\n');
        if (e.err) s += '--- stderr ---\n' + e.err + (/\n$/.test(e.err) ? '' : '\n');
        if (e.trunc) s += '[output limit reached]\n';
        return s;
      }).join('\n');
      var d = new Date(), p = function (n) { return String(n).padStart(2, '0'); };
      var stamp = p(d.getDate()) + '-' + p(d.getMonth() + 1) + '-' + d.getFullYear() + '_' + p(d.getHours()) + '-' + p(d.getMinutes());
      var reader = new FileReader();
      reader.onload = function () {
        var b64 = String(reader.result).split(',')[1] || '';
        try { window.ksu_download.save(b64, 'KsuWebUI-SN_execution-' + stamp + '.txt', 'text/plain'); } catch (err) {}
      };
      reader.readAsDataURL(new Blob([text], { type: 'text/plain' }));
    }

    function buildUi(host) {
      var wrap = el('div', 'kx-wrap');
      wrap.appendChild(el('style', null, CSS));
      var tools = el('div', 'kx-tools');
      ui.search = el('input', 'kx-search');
      ui.search.type = 'search';
      ui.search.placeholder = 'Search';
      ui.search.addEventListener('input', function () {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
          query = ui.search.value.trim().toLowerCase();
          layout();
        }, 250);
      });
      ui.order = btn('', function () {
        newestFirst = !newestFirst;
        try { localStorage.setItem(ORDER_KEY, newestFirst ? 'newest' : 'oldest'); } catch (err) {}
        setOrderLabel();
        layout();
      });
      ui.filter = btn('All', function () {
        onlyFailed = !onlyFailed;
        ui.filter.textContent = onlyFailed ? 'Failed' : 'All';
        ui.filter.classList.toggle('on', onlyFailed);
        layout();
      });
      tools.appendChild(ui.search);
      tools.appendChild(ui.order);
      tools.appendChild(ui.filter);
      tools.appendChild(btn('Export', exportLog));
      tools.appendChild(btn('Clear', clearAll));
      ui.empty = el('div', 'kx-empty');
      ui.list = el('div', 'kx-list');
      wrap.appendChild(tools);
      wrap.appendChild(ui.empty);
      wrap.appendChild(ui.list);
      host.appendChild(wrap);
      setOrderLabel();
    }

    class Executions extends eruda.Tool {
      constructor() {
        super();
        this.name = 'Executions';
      }
      init($el) {
        super.init($el);
        buildUi($el[0]);
        poll();
        layout();
      }
      show() {
        super.show();
        poll();
        startPolling();
        return this;
      }
      hide() {
        super.hide();
        stopPolling();
        return this;
      }
      destroy() {
        stopPolling();
        super.destroy();
      }
    }

    eruda.add(new Executions());

    try {
      var tab = eruda._devTools._tab, bar = tab.tabs;
      var q = function (id) { return bar.querySelector('[data-id="' + id + '"]'); };
      bar.insertBefore(q('Executions'), q('elements'));
      bar.insertBefore(q('elements'), q('settings'));
      tab.updateSlider();
    } catch (e2) {}
  } catch (e) {}
})();
