package io.github.a13e300.ksuwebui

import android.webkit.JavascriptInterface

class SystemBarsInterface(private val activity: WebUIActivity) {
    @JavascriptInterface
    fun report(rgb: Int) = activity.onPageColor(rgb)
}

const val SYSTEM_BARS_JS = """
(function () {
  if (window.__ksuBars) return;
  window.__ksuBars = true;
  var ctx = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
  var last, queued;
  function rgba(el) {
    if (!el) return null;
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = getComputedStyle(el).backgroundColor;
    ctx.fillRect(0, 0, 1, 1);
    var d = ctx.getImageData(0, 0, 1, 1).data;
    return d[3] > 200 ? d : null;
  }
  function report() {
    queued = false;
    var c = rgba(document.body) || rgba(document.documentElement);
    var v = c ? (c[0] << 16 | c[1] << 8 | c[2]) : -1;
    if (v === last) return;
    last = v;
    ksu_bars.report(v);
  }
  function schedule() {
    if (!queued) { queued = true; requestAnimationFrame(report); }
  }
  var mo = new MutationObserver(schedule);
  mo.observe(document.documentElement, { attributes: true });
  document.addEventListener('DOMContentLoaded', function () {
    if (document.body) mo.observe(document.body, { attributes: true });
    schedule();
  });
  document.addEventListener('transitionend', schedule, true);
  window.addEventListener('load', schedule);
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', schedule);
  schedule();
})();
"""
