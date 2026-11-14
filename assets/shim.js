/* 夜铃独立版 · 宿主桥（把插件的 BellHost 接到原生 App） */
(function () {
  function safe(fn, d) {
    try { return fn(); } catch (e) { return d; }
  }
  window.NB_LOCAL_STT = true;
  var HSEQ = 0, HMAP = {};
  window.__nbHttp = function (id, raw) {
    var f = HMAP[id];
    if (!f) return;
    delete HMAP[id];
    var o = null;
    try { o = JSON.parse(raw); } catch (e) { }
    f(o || { ok: false });
  };
  var H = {
    log: function (m) { safe(function () { NB.log(String(m)); }); },
    dataWrite: function (o) {
      o = o || {};
      return safe(function () { return NB.dataWrite(String(o.key || ''), String(o.val || '')); }, '{"ok":false}');
    },
    dataRead: function (o) {
      o = o || {};
      return safe(function () { return NB.dataRead(String(o.key || '')); }, '{"ok":false}');
    },
    ringShow: function (o) {
      o = o || {};
      safe(function () { NB.ring(String(o.name || ''), String(o.line || ''), Number(o.timeout || 40)); });
      return '';
    },
    ringResult: function () {
      return safe(function () { return NB.ringResult(); }, '');
    },
    chatWithAgent: function (p) {
      return safe(function () { return NB.ask(JSON.stringify(p || {})); }, '{"ok":false}');
    },
    poll: function () {
      return safe(function () { return NB.pollAsk(); }, '{"ok":true,"pending":true}');
    },
    speakText: function () { return '{"ok":false}'; },
    ttsPoll: function () { return '{"ok":false}'; },
    ttsStop: function () { },
    ttsStream: function () { return '{"ok":false}'; },
    asrBegin: function (o) { return safe(function () { return NB.asrBegin(JSON.stringify(o || {})); }, '{"ok":false}'); },
    asrEnd: function () { return safe(function () { return NB.asrEnd(); }, '{"ok":false}'); },
    asrCancel: function () { safe(function () { NB.asrCancel(); }); },
    asrAmp: function () { return safe(function () { return NB.asrAmp(); }, 0); },
    audioPoll: function () { return '{"ok":false}'; },
    httpGet: function (o) {
      o = o || {};
      var url = String(o.url || '');
      return new Promise(function (res) {
        if (!url) return res({ ok: false });
        var id = 'h' + (++HSEQ);
        HMAP[id] = function (v) { res(v); };
        try { NB.httpGet(url, id); } catch (e) { delete HMAP[id]; return res({ ok: false }); }
        setTimeout(function () {
          if (HMAP[id]) { delete HMAP[id]; res({ ok: false, error: 'timeout' }); }
        }, 12000);
      });
    },
    nativeProbe: function () { return ''; },
    probe: function () { return ''; },
    nativeReadBase64: function () { return ''; }
  };
  window.BellHost = H;

  /* 权限提示条：缺什么就提示，点了直接跳系统页 */
  function tip() {
    var st = {};
    try { st = JSON.parse(NB.state()); } catch (e) { st = {}; }
    var box = document.getElementById('nbPermTip');
    var need = [];
    if (!st.overlay) need.push('悬浮窗');
    if (!st.files) need.push('文件访问');
    if (!need.length) {
      if (box) box.style.display = 'none';
      return;
    }
    if (!box) {
      box = document.createElement('div');
      box.id = 'nbPermTip';
      box.style.cssText = 'position:fixed;left:14px;right:14px;top:12px;z-index:99999;padding:12px 16px;border-radius:16px;' +
        'background:rgba(20,20,26,.9);color:#f0f0f4;font-size:12.5px;letter-spacing:.4px;' +
        'display:flex;align-items:center;justify-content:space-between;gap:10px;' +
        'box-shadow:0 10px 30px rgba(0,0,0,.35);backdrop-filter:blur(12px);';
      box.innerHTML = '<span id="nbPermTxt"></span><b id="nbPermBtn" style="flex:0 0 auto;font-weight:600;font-size:12.5px;">去开</b>';
      document.body.appendChild(box);
      box.onclick = function () {
        var s2 = {};
        try { s2 = JSON.parse(NB.state()); } catch (e) { }
        if (!s2.overlay) NB.openOverlay(); else NB.openFiles();
      };
    }
    box.style.display = 'flex';
    document.getElementById('nbPermTxt').textContent = '还差' + need.join('、') + '权限，来电弹不出来';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { setTimeout(tip, 400); });
  } else {
    setTimeout(tip, 400);
  }
  setInterval(tip, 2500);
})();