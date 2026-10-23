const $ = id => document.getElementById(id);
const chatEl = $('chat'), optsEl = $('opts'), timeRow = $('timeRow'), textRow = $('textRow');
const sleep = ms => new Promise(r => setTimeout(r, ms));

/* ---------- gọi máy chủ ---------- */
let pendingCalls = 0;
let progressTimer = null;
let visualProgress = 0;
function showLoad(value, message = 'Đang tải dữ liệu') {
  const holder = $('loadProgress'), bar = $('loadBar'), label = $('loadPercent');
  if (!holder || !bar || !label) return;
  holder.hidden = false;
  visualProgress = Math.max(0, Math.min(100, value));
  bar.style.width = visualProgress + '%'; label.textContent = message + ' · ' + Math.round(visualProgress) + '%';
}
function beginLoad() {
  if (++pendingCalls !== 1) return;
  showLoad(0);
  if (progressTimer) clearInterval(progressTimer);
  progressTimer = setInterval(() => {
    if (pendingCalls > 0 && visualProgress < 90) showLoad(Math.min(90, visualProgress + 5));
  }, 180);
}
function endLoad(success = true) {
  pendingCalls = Math.max(0, pendingCalls - 1);
  if (pendingCalls) return;
  clearInterval(progressTimer); progressTimer = null;
  if (success) showLoad(100, 'Đã tải');
  else {const el = $('loadPercent'); if (el) el.textContent = 'Không tải được dữ liệu. Kiểm tra kết nối và thử lại.';}
  setTimeout(() => { if (pendingCalls === 0 && $('loadProgress')) $('loadProgress').hidden = true; }, 600);
}
/* Tiến độ 0–100% của thao tác gọi dữ liệu; không phải % bài tập đã làm. */
async function api(url, params) {
  const track = !url.includes('/poll');
  if (track) beginLoad();
  const opt = params
    ? {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams(params)}
    : {};
  let apiSuccess = false;
  try {
    const r = await fetch(url, opt);
    if (r.status === 401) { location.href = '/'; return null; }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const result = await r.json(); apiSuccess = true; return result;
  } catch (e) {
    console.error(e);
    const label = $('loadPercent');
    if (label && track) label.textContent = 'Không tải được. Kiểm tra kết nối và thử lại.';
    return null;
  } finally { if (track) endLoad(apiSuccess); }
}

/* ---------- hàng đợi: trợ lý "gõ chữ" từng câu một ---------- */
let queue = Promise.resolve();
const enqueue = fn => (queue = queue.then(fn).catch(console.error));

function scrollDown() { chatEl.scrollTop = chatEl.scrollHeight; }

function addMsg(m) {
  const d = document.createElement('div');
  const me = m.sender === 'STUDENT';
  d.className = 'msg ' + (me ? 'me' : 'bot') + (m.kind && m.kind !== 'normal' ? ' ' + m.kind : '');
  d.textContent = (m.kind === 'nudge' && !me ? '💡 ' : (m.kind === 'boost' && !me ? '✨ ' : '')) + m.text;
  chatEl.appendChild(d);
  scrollDown();
}

async function botSay(m) {
  const t = document.createElement('div');
  t.className = 'msg bot typing'; t.innerHTML = '<i></i><i></i><i></i>';
  chatEl.appendChild(t); scrollDown();
  await sleep(Math.min(1600, 450 + m.text.length * 12));
  t.remove(); addMsg(m);
  if (m.kind === 'cheer') confetti();
  if (m.kind === 'nudge') notify(m.text);
}

/* logo từng nguồn: có ảnh thì dùng ảnh; nguồn mới chưa có logo thì khai { text, color } để hiện huy hiệu chữ */
const LOGOS = {
  vj: { img: '/logo-vietjack.png' },
  yt: { img: '/logo-youtube.png' },
  lgh: { img: '/logo-loigiaihay.png' },
  olm: { img: '/logo-olm.png' },
  h247: { img: '/logo-hoc247.png' },
};
const MAIN = new Set(['start', 'done', 'yes', '0', 'refresh', 'restart']);
function setOpts(list) {
  optsEl.innerHTML = '';
  const links = list.filter(o => o.value.startsWith('url:'));
  const rest = list.filter(o => !o.value.startsWith('url:'));
  if (links.length) {
    const row = document.createElement('div');
    row.className = 'linkrow';
    const cap = document.createElement('span');
    cap.className = 'muted small';
    cap.textContent = 'Cần xem bài giảng, bài làm mẫu? Bấm để mở ở tab mới:';
    row.appendChild(cap);
    links.forEach(o => {
      const m = /^url:(\w+):(https:\/\/.+)$/.exec(o.value);
      if (!m || !LOGOS[m[1]]) return;
      const a = document.createElement('a');
      a.className = 'linkbtn';
      a.href = m[2]; a.target = '_blank'; a.rel = 'noopener noreferrer';
      const logo = LOGOS[m[1]];
      if (logo.img) {
        const im = document.createElement('img');
        im.src = logo.img; im.alt = '';
        a.appendChild(im);
      } else {
        const bd = document.createElement('span');
        bd.className = 'linkbadge'; bd.style.background = logo.color; bd.textContent = logo.text;
        a.appendChild(bd);
      }
      a.appendChild(document.createTextNode(o.label));
      row.appendChild(a);
    });
    optsEl.appendChild(row);
  }
  rest.forEach(o => {
    const b = document.createElement('button');
    b.className = 'opt' + (MAIN.has(o.value) ? ' main' : '');
    b.textContent = o.label;
    b.onclick = () => choose(o.label, o.value);
    optsEl.appendChild(b);
  });
  scrollDown();
}

/* ---------- hiển thị câu trả lời của máy chủ ---------- */
function render(reply) {
  if (!reply) return;
  (reply.history || []).forEach(addMsg);
  enqueue(async () => {
    for (const m of reply.messages || []) await botSay(m);
    setOpts(reply.options || []);
    timeRow.hidden = reply.input !== 'time';
    textRow.hidden = reply.input !== 'text';
    photoRow.hidden = reply.input !== 'photo';
    if (!textRow.hidden) {
      $('textInput').placeholder = reply.input === 'text' ? 'Gõ vài chữ rồi bấm Gửi...' : '';
      $('textInput').focus();
    }
    refreshTasks();
  });
}

/* ---------- học sinh trả lời ---------- */
async function choose(label, value) {
  setOpts([]); timeRow.hidden = true; textRow.hidden = true; photoRow.hidden = true;
  addMsg({sender: 'STUDENT', text: label, kind: 'normal'});
  render(await api('/student/api/answer', {value, label}));
}
$('timeOk').onclick = () => {
  const v = $('timeInput').value;
  if (v) choose(v, v);
};
function sendText() {
  const v = $('textInput').value.trim();
  if (!v) return;
  $('textInput').value = '';
  choose(v, v);
}
$('textOk').onclick = sendText;
$('textInput').addEventListener('keydown', e => { if (e.key === 'Enter') sendText(); });

/* ---------- thanh nhiệm vụ hôm nay ---------- */
const ICON = {TODO: '', PLANNED: '⏰ ', STARTED: '✍️ ', DONE: '✔ ', CONFIRMED: '✅ '};
async function refreshTasks() {
  const list = await api('/student/api/tasks');
  if (!list) return;
  const bar = $('taskbar');
  bar.innerHTML = '';
  list.forEach(t => {
    const d = document.createElement('span');
    d.className = 'tchip ' + t.status;
    d.title = 'Hạn nộp ' + t.deadline + ' (' + t.left + ')';
    d.textContent = (ICON[t.status] || '') + t.label;
    const sm = document.createElement('small');
    sm.textContent = t.left;
    d.appendChild(sm);
    if (t.images && t.images.length) d.classList.add('has-img');
    d.onclick = () => openTask(t);
    bar.appendChild(d);
  });
}

/* ---------- bấm vào ô nhiệm vụ: xem hạn nộp và ảnh đề bài do ban cán sự gửi ---------- */
async function openTask(t) {
  $('tmTitle').textContent = t.label;
  $('tmInfo').textContent = 'Hạn nộp ' + t.deadline + ' (' + t.left + ')'
    + (t.images && t.images.length ? ' · Mở ảnh hoặc tệp Word bên dưới.' : ' · Chưa có đề bài đính kèm.');
  const box = $('tmImgs'); box.innerHTML = '';
  $('taskModal').hidden = false;
  for (const id of (t.images || [])) {
    const url = '/task-image/' + id + '/file';
    const a = document.createElement('a');
    a.href = url; a.target = '_blank'; a.rel = 'noopener noreferrer';
    try {
      const head = await fetch(url, {method:'HEAD'});
      if (!head.ok) throw new Error('Không lấy được thông tin tệp');
      const type = head.headers.get('content-type') || '';
      if (type.includes('wordprocessingml')) {
        a.textContent = '📄 Mở tệp Word (.docx)'; a.className = 'word-link';
        a.download = 'de-bai-' + id + '.docx';
      } else {
        const im = document.createElement('img'); im.src = url; im.alt = 'Ảnh đề bài';
        a.appendChild(im);
      }
    } catch(err) {a.textContent = '📎 Mở tệp đính kèm'; a.className = 'word-link';}
    box.appendChild(a);
  }
}
$('taskModalClose').onclick = () => { $('taskModal').hidden = true; };
$('taskModal').addEventListener('click', e => { if (e.target.id === 'taskModal') $('taskModal').hidden = true; });

/* ---------- âm thanh nhắc nhở (luôn bật) ---------- */
let audioCtx = null;
function unlockAudio() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume();
  } catch (e) {}
}
['click', 'touchstart', 'keydown'].forEach(ev => document.addEventListener(ev, unlockAudio, {passive: true}));

/* một tiếng chuông: nốt chính + các họa âm, ngân dài dần tắt */
function bellTone(t, f, dur, vol) {
  [[1, 1], [2.01, 0.45], [3.02, 0.22], [4.2, 0.1]].forEach(([m, a]) => {
    const o = audioCtx.createOscillator(), g = audioCtx.createGain();
    o.type = 'sine'; o.frequency.value = f * m;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol * a, t + 0.015);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(audioCtx.destination);
    o.start(t); o.stop(t + dur + 0.05);
  });
}
/* một vòng ~4 giây: lên dây nhanh rồi "ding - dong - ding - dong" */
function playCycle() {
  if (!audioCtx || audioCtx.state !== 'running') return;
  const t0 = audioCtx.currentTime + 0.05;
  [[523, 0], [659, .16], [784, .32], [1047, .48], [1047, 1.0], [784, 1.5], [1047, 2.0], [1319, 2.5]]
    .forEach(([f, d], i) => bellTone(t0 + d, f, i < 4 ? 0.7 : 1.5, i < 4 ? 0.22 : 0.34));
  try { if (navigator.vibrate) navigator.vibrate([400, 150, 400, 150, 400, 150, 900]); } catch (e) {}
}

let alarmTimer = null, alarmStop = null, titleTimer = null;
const baseTitle = document.title;
function startAlarm(title) {
  stopAlarm();
  unlockAudio();
  playCycle();
  alarmTimer = setInterval(playCycle, 4000);
  alarmStop = setTimeout(stopAlarm, 45000);               // kêu tối đa 45 giây nếu chưa bấm
  let on = false;
  titleTimer = setInterval(() => { on = !on; document.title = on ? '🔔 ' + title : baseTitle; }, 800);
}
function stopAlarm() {
  clearInterval(alarmTimer); clearTimeout(alarmStop); clearInterval(titleTimer);
  alarmTimer = alarmStop = titleTimer = null;
  document.title = baseTitle;
  try { if (navigator.vibrate) navigator.vibrate(0); } catch (e) {}
}

/* màn hình nhắc nhở: chuông lắc, vòng sóng lan, nút to */
function showAlarm(reply) {
  const msgs = (reply.messages || []).filter(m => m.kind === 'nudge');
  if (!msgs.length) { return; }
  const canStart = (reply.options || []).some(o => o.value === 'start');
  const resume = canStart && reply.options.some(o => o.value === 'start' && /tiếp/i.test(o.label));   // hết giờ tạm dừng
  const title = resume ? 'Hết giờ nghỉ rồi!' : canStart ? 'Đến giờ làm bài rồi!' : 'Sắp đến hạn nộp!';
  $('alarmTitle').textContent = (resume ? '▶️ ' : canStart ? '⏰ ' : '⏳ ') + title;
  $('alarmText').textContent = msgs[0].text;
  const box = $('alarmBtns');
  box.innerHTML = '';
  const close = () => { $('alarm').hidden = true; stopAlarm(); };
  if (canStart) {
    reply.options.forEach((o, i) => {
      const b = document.createElement('button');
      b.className = 'btn ' + (i === 0 ? 'primary big' : 'big');
      b.textContent = (i === 0 ? (resume ? '▶️ ' : '🚀 ') : o.value === 'help' ? '🙋 ' : '⏰ ') + o.label;
      b.onclick = () => { close(); choose(o.label, o.value); };
      box.appendChild(b);
    });
  } else {
    const b = document.createElement('button');
    b.className = 'btn primary big';
    b.textContent = '👍 Mình biết rồi, làm ngay đây';
    b.onclick = close;
    box.appendChild(b);
  }
  $('alarm').hidden = false;
  startAlarm(title);
}

/* ---------- thông báo đẩy: luôn bật, tự xin quyền ở lần chạm đầu tiên ---------- */
const isIOS = /iPhone|iPad|iPod/i.test(navigator.userAgent);
const isStandalone = window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const canPush = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
let pushOn = false;

function b64ToU8(s) {
  const pad = '='.repeat((4 - s.length % 4) % 4);
  const raw = atob((s + pad).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(raw, c => c.charCodeAt(0));
}
async function subscribePush() {
  const reg = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;
  const k = await api('/student/api/push/key');
  if (!k || !k.key) return false;
  let sub = await reg.pushManager.getSubscription();
  if (!sub) sub = await reg.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: b64ToU8(k.key)});
  const j = sub.toJSON();
  pushOn = !!(await api('/student/api/push/subscribe', {endpoint: j.endpoint, p256dh: j.keys.p256dh, auth: j.keys.auth}));
  return pushOn;
}
function updateBanner() {
  const bar = $('notifBanner'), txt = $('notifText'), btn = $('notifBtn');
  btn.hidden = true;
  if (!canPush) {
    if (isIOS && !isStandalone) {
      txt.textContent = '📱 iPhone: bấm nút Chia sẻ (ô vuông có mũi tên) → "Thêm vào Màn hình chính", rồi mở AI-Nudge từ biểu tượng đó để nhận thông báo.';
      bar.hidden = false;
    } else bar.hidden = true;
    return;
  }
  if (Notification.permission === 'granted') { bar.hidden = true; return; }
  if (Notification.permission === 'denied') {
    txt.textContent = '🔕 Thông báo đang bị chặn. Bấm biểu tượng ổ khóa cạnh địa chỉ web → Thông báo → Cho phép, rồi tải lại trang.';
  } else {
    txt.textContent = '🔔 Cho phép thông báo để Nudge nhắc bạn đúng giờ, kể cả khi bạn tắt trang này.';
    btn.hidden = false;
  }
  bar.hidden = false;
}
async function enablePush() {
  unlockAudio();
  if (!canPush) return;
  try {
    if (Notification.permission === 'default') await Notification.requestPermission();
    if (Notification.permission === 'granted') await subscribePush();
  } catch (e) { console.error(e); }
  updateBanner();
}
$('notifBtn').onclick = enablePush;
document.addEventListener('pointerdown', () => { if (canPush && Notification.permission === 'default') enablePush(); }, {once: true});
(async () => {
  if (canPush && Notification.permission === 'granted') { try { await subscribePush(); } catch (e) { console.error(e); } }
  updateBanner();
})();

/* ---------- thêm email để nhận nhắc qua email ---------- */
if ($('emailOk')) {
  $('emailOk').onclick = async () => {
    const v = $('emailInput').value.trim();
    const msg = $('emailMsg');
    if (!v) { msg.textContent = 'Bạn nhập email nhé.'; return; }
    const r = await api('/student/api/email', {email: v});
    if (r && r.ok) {
      $('emailBar').innerHTML = '<span>✅ Đã lưu email. Mình vừa gửi một thư chào, bạn xem hộp thư (cả mục Spam) nhé.</span>';
      setTimeout(() => { const b = $('emailBar'); if (b) b.hidden = true; }, 9000);
    } else msg.textContent = (r && r.error) || 'Chưa lưu được, bạn thử lại nhé.';
  };
}

function notify(text) {
  if (!pushOn && 'Notification' in window && Notification.permission === 'granted' && document.hidden) {
    try { new Notification('AI-Nudge', {body: text}); } catch (e) {}
  }
}
function confetti() {
  const cols = ['#6c5ce7', '#ec4899', '#06b6d4', '#10b981', '#f59e0b'];
  for (let i = 0; i < 40; i++) {
    const d = document.createElement('div'); d.className = 'cf';
    d.style.left = Math.random() * 100 + 'vw'; d.style.background = cols[i % cols.length];
    d.style.animationDelay = Math.random() * 0.8 + 's'; d.style.animationDuration = 2.2 + Math.random() * 1.6 + 's';
    document.body.appendChild(d); setTimeout(() => d.remove(), 4500);
  }
}

/* ---------- khởi động: trợ lý chủ động mở lời ---------- */
(async () => {
  render(await api('/student/api/state'));
  // Cứ 10 giây hỏi máy chủ: đã đến giờ nhắc chưa?
  setInterval(async () => {
    const r = await api('/student/api/poll');
    if (r && r.messages && r.messages.length) { showAlarm(r); render(r); }
  }, 10000);
})();

/* ---------- gửi ảnh minh chứng ngay trong khung chat ---------- */
const photoRow = $('photoRow'), photoFiles = $('photoFiles'), photoInfo = $('photoInfo'), photoOk = $('photoOk'), photoPrev = $('photoPrev');

/* thu nhỏ ảnh chụp từ điện thoại cho gửi nhanh (nếu lỗi thì gửi ảnh gốc) */
function shrink(file) {
  return new Promise(res => {
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return res(file);
    const img = new Image(), url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const k = Math.min(1, 1600 / Math.max(img.width, img.height));
      if (k === 1 && file.size < 1500000) return res(file);
      const c = document.createElement('canvas');
      c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
      const g = c.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height);
      g.drawImage(img, 0, 0, c.width, c.height);
      c.toBlob(b => res(b && b.size < file.size
        ? new File([b], file.name.replace(/\.\w+$/, '') + '.jpg', {type: 'image/jpeg'}) : file), 'image/jpeg', 0.82);
    };
    img.onerror = () => { URL.revokeObjectURL(url); res(file); };
    img.src = url;
  });
}

photoFiles.onchange = () => {
  const files = [...photoFiles.files];
  photoPrev.innerHTML = '';
  files.forEach(f => {
    const w = document.createElement('div'); w.className = 'it';
    const im = document.createElement('img'); im.src = URL.createObjectURL(f); im.onload = () => URL.revokeObjectURL(im.src);
    w.appendChild(im); photoPrev.appendChild(w);
  });
  photoInfo.textContent = files.length ? 'Đã chọn ' + files.length + ' ảnh' : 'Chọn một hoặc nhiều ảnh';
  photoOk.disabled = !files.length;
  scrollDown();
};

photoOk.onclick = async () => {
  const files = [...photoFiles.files];
  if (!files.length) return;
  photoOk.disabled = true; photoInfo.textContent = 'Đang gửi ' + files.length + ' ảnh...';
  try {
    const fd = new FormData();
    for (const f of files) { const s = await shrink(f); fd.append('files', s, s.name); }
    const r = await fetch('/student/api/photo', {method: 'POST', body: fd});
    if (r.status === 401) { location.href = '/'; return; }
    let j = null; try { j = await r.json(); } catch (e) {}
    if (!j || !j.messages) { photoInfo.textContent = (j && j.error) || 'Gửi không được, bạn thử lại nhé.'; photoOk.disabled = false; return; }
    photoFiles.value = ''; photoPrev.innerHTML = '';
    setOpts([]); photoRow.hidden = true;
    render(j);
  } catch (e) { photoInfo.textContent = 'Có lỗi mạng, bạn thử lại nhé.'; photoOk.disabled = false; }
};
