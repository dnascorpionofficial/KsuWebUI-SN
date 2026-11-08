var __ksuPos = null;
try {
  var __raw = localStorage.getItem('eruda-entry-button');
  if (__raw) {
    var __o = JSON.parse(__raw);
    if (__o && __o.pos && typeof __o.pos.x === 'number' && typeof __o.pos.y === 'number') __ksuPos = __o.pos;
  }
} catch (e) {}
