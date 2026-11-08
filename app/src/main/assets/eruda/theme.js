try {
  var __dt = eruda._devTools;
  if (__dt && __dt.config && __dt.config.get('theme') === 'System preference') {
    __dt._setTheme(__ksuDark ? 'Dark' : 'Light');
  }
} catch (e) {}
