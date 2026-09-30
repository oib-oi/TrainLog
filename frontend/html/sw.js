const VERSION = 'v2';
const SHELL_CACHE = `shell-${VERSION}`;
const API_CACHE = `api-${VERSION}`;
const SHELL = [
    './',
    'index.html',
    'style.css',
    'app.js',
    'favicon.svg',
    'manifest.webmanifest',
    'icons/icon-192.png',
    'icons/icon-512.png',
    'icons/apple-touch-icon.png'
];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(SHELL_CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(key => ![SHELL_CACHE, API_CACHE].includes(key)).map(key => caches.delete(key))))
            .then(() => self.clients.claim()));
});

async function networkFirst(request, cacheName) {
    const cache = await caches.open(cacheName);
    try {
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
    } catch (e) {
        const cached = await cache.match(request);
        if (cached) return cached;
        throw e;
    }
}

self.addEventListener('fetch', event => {
    const {request} = event;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== location.origin) return;

    if (url.pathname.startsWith('/api/')) {
        event.respondWith(networkFirst(request, API_CACHE));
    } else if (request.mode === 'navigate') {
        event.respondWith(networkFirst(new Request('index.html'), SHELL_CACHE));
    } else {
        event.respondWith(networkFirst(request, SHELL_CACHE));
    }
});
