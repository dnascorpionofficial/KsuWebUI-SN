try {
  var __eb = eruda._entryBtn;
  if (__eb && !__eb.__ksuFixed) {
    __eb.__ksuFixed = true;
    __eb.setPos = function(p) {
      var d = this._getDefPos();
      var x = Math.min(Math.max(p.x, 0), Math.max(d.x, 0));
      var y = Math.min(Math.max(p.y, 0), Math.max(d.y, 0));
      this._$el.css({ left: x, top: y });
      if (p.x >= 0 && p.x <= d.x + 10 && p.y >= 0 && p.y <= d.y + 10) this.config.set('pos', p);
    };
    __eb._resetPos = function() {
      var p = this.config.get('rememberPos') ? this.config.get('pos') : this._getDefPos();
      this.setPos(p);
    };
    if (__ksuRestore && __ksuPos) {
      __eb.config.set('pos', __ksuPos);
      __eb.setPos(__ksuPos);
    }
  }
} catch (e) {}
