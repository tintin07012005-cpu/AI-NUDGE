/* Service worker: nhận thông báo đẩy kể cả khi đã tắt trang web. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('push', e => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (err) { d = {body: e.data ? e.data.text() : ''}; }
  e.waitUntil(self.registration.showNotification(d.title || 'AI-Nudge', {
    body: d.body || '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: d.tag || 'ai-nudge',
    renotify: true,
    vibrate: [400, 150, 400, 150, 400, 150, 900],
    requireInteraction: true,
    data: {url: d.url || '/student'}
  }));
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || '/student';
  e.waitUntil((async () => {
    const list = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
    for (const c of list) { if ('focus' in c) { await c.focus(); return; } }
    await self.clients.openWindow(url);
  })());
});
