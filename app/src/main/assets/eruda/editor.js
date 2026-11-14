(function () {
  try {
    if (!window.eruda || !eruda.Tool || window.ksuEditorTab) return;
    window.ksuEditorTab = true;
    var text = window.ksuText || {};

    var CSS = [
      '.ksu-editor{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;box-sizing:border-box;padding:24px;gap:16px;text-align:center;color:var(--foreground)}',
      '.ksu-editor-hint{max-width:320px;font-size:13px;line-height:1.5;opacity:.7}',
      '.ksu-editor-open{padding:10px 22px;border:1px solid var(--accent);border-radius:22px;background:var(--background);color:var(--accent);font-size:14px;font-weight:bold}'
    ].join('');

    class Editor extends eruda.Tool {
      constructor() {
        super();
        this.name = 'Editor';
      }
      init($el) {
        super.init($el);
        var root = $el[0];
        var style = document.createElement('style');
        style.textContent = CSS;
        root.appendChild(style);
        var wrap = document.createElement('div');
        wrap.className = 'ksu-editor';
        var hint = document.createElement('div');
        hint.className = 'ksu-editor-hint';
        hint.textContent = text.editorHint || '';
        var open = document.createElement('button');
        open.className = 'ksu-editor-open';
        open.textContent = text.openEditor || 'Open Editor';
        open.addEventListener('click', function () {
          window.prompt('__ksu_editor__', '');
        });
        wrap.appendChild(hint);
        wrap.appendChild(open);
        root.appendChild(wrap);
      }
    }

    eruda.add(new Editor());

    try {
      var dev = eruda._devTools;
      var tab = dev._tab, bar = tab.tabs;
      var q = function (id) { return bar.querySelector('[data-id="' + id + '"]'); };
      bar.insertBefore(q('Editor'), q('network'));
      tab.updateSlider();
    } catch (e2) {}
  } catch (e) {}
})();
