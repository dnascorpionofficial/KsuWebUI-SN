try {
  eruda.position({ x: 99999, y: 99999 });
  var p = eruda.position();
  var s = window.innerHeight - p.y - 10;
  eruda.position({ x: p.x, y: Math.round((window.innerHeight - s) / 2) });
} catch (e) {}
