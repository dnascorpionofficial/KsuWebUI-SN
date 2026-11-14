(function() {
  try {
    if (window.ksuNetworkBody) return;
    window.ksuNetworkBody = true;
    var host = document.getElementById('eruda');
    var root = host && (host.shadowRoot || host);
    if (!root) return;
    var style = document.createElement('style');
    style.textContent = '.eruda-network .eruda-detail .eruda-control { padding-right: 72px !important; }';
    root.appendChild(style);
    function bodyText() {
      var network = window.eruda && eruda.get && eruda.get('network');
      var data = network && network._detail && network._detail._detailData;
      if (!data || data.resTxt === undefined || data.resTxt === null) return '';
      if (typeof data.resTxt === 'object') return JSON.stringify(data.resTxt, null, 2);
      return String(data.resTxt);
    }
    function ensure(container) {
      var control = container.querySelector('.eruda-control');
      var copyRes = control && control.querySelector('.eruda-copy-res');
      if (!copyRes || control.querySelector('.eruda-copy-body')) return;
      var button = document.createElement('span');
      button.className = 'eruda-copy-body';
      button.title = window.ksuText.copyBody;
      button.textContent = '{ }';
      button.style.cssText = 'position:absolute;top:0;right:36px;padding:10px;cursor:pointer;font-size:14px;font-weight:700;line-height:20px;font-family:monospace;';
      button.addEventListener('click', function() {
        var text = bodyText();
        if (text) window.prompt('__ksu_copy__', text);
      });
      copyRes.insertAdjacentElement('beforebegin', button);
    }
    var tries = 0;
    var timer = setInterval(function() {
      var container = root.querySelector('.eruda-network .eruda-detail');
      if (container) {
        clearInterval(timer);
        new MutationObserver(function() { ensure(container); }).observe(container, { childList: true });
        ensure(container);
      } else if (++tries > 40) {
        clearInterval(timer);
      }
    }, 250);
  } catch (e) {}
})();
