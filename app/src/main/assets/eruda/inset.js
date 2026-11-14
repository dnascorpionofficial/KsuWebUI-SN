try {
  var __h = document.getElementById('eruda');
  var __r = __h && (__h.shadowRoot || __h);
  if (__r && !__r.querySelector('#ksu-eruda-inset')) {
    var __st = document.createElement('style');
    __st.id = 'ksu-eruda-inset';
    __st.textContent = '.eruda-dev-tools{bottom:var(--safe-area-inset-bottom,0px)!important;'
      + 'max-height:calc(100vh - var(--safe-area-inset-bottom,0px) - var(--safe-area-inset-top,0px))!important;}';
    __r.appendChild(__st);
  }
} catch (e) {}
