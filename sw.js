/* ============================================================
   SERVICE WORKER — App Nonô v29
   Cache-first para assets, Network-first para Firebase
   ============================================================ */

const CACHE_NAME = 'appnono-v29';
const CACHE_VERSION = '29.0';

// Assets para cache imediato (shell da app)
const SHELL_ASSETS = [
    '/',
    '/index.html',
    '/manifest.json',
    '/icon-192.png',
    '/icon-512.png',
];

// Domínios externos a fazer cache (fonts, firebase SDKs)
const CACHE_EXTERNAL = [
    'fonts.googleapis.com',
    'fonts.gstatic.com',
    'www.gstatic.com',  // Firebase SDKs
];

// Domínios a NUNCA fazer cache (Firestore/Auth - dados em tempo real)
const NO_CACHE_HOSTS = [
    'firestore.googleapis.com',
    'identitytoolkit.googleapis.com',
    'securetoken.googleapis.com',
];

// ── INSTALL ──────────────────────────────────────────────────
self.addEventListener('install', (event) => {
    console.log('[SW] Instalar v' + CACHE_VERSION);
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('[SW] Cache shell assets');
            return cache.addAll(SHELL_ASSETS);
        }).then(() => {
            // Ativar imediatamente sem esperar que os tabs antigos fechem
            return self.skipWaiting();
        })
    );
});

// ── ACTIVATE ─────────────────────────────────────────────────
self.addEventListener('activate', (event) => {
    console.log('[SW] Ativar v' + CACHE_VERSION);
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames
                    .filter((name) => name !== CACHE_NAME)
                    .map((name) => {
                        console.log('[SW] Apagar cache antiga:', name);
                        return caches.delete(name);
                    })
            );
        }).then(() => self.clients.claim())
    );
});

// ── FETCH ────────────────────────────────────────────────────
self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);

    // 1. Nunca fazer cache de Firestore / Auth (dados em tempo real)
    if (NO_CACHE_HOSTS.some(h => url.hostname.includes(h))) {
        return; // Passa direto para a rede
    }

    // 2. POST requests — sempre rede
    if (event.request.method !== 'GET') {
        return;
    }

    // 3. Firebase Firestore REST — sempre rede
    if (url.pathname.includes('/google.firestore') || 
        url.pathname.includes('/firestore/') ||
        url.hostname.includes('firebase')) {
        return;
    }

    // 4. Shell da app (index.html, manifest, icons) — Cache First
    if (url.hostname === self.location.hostname || 
        url.hostname === 'localhost' ||
        url.protocol === 'file:') {
        event.respondWith(
            caches.match(event.request).then((cached) => {
                if (cached) {
                    // Servir do cache E atualizar em background
                    const fetchPromise = fetch(event.request)
                        .then((response) => {
                            if (response && response.status === 200) {
                                const clone = response.clone();
                                caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
                            }
                            return response;
                        }).catch(() => null);
                    return cached;
                }
                // Não está em cache — buscar da rede e guardar
                return fetch(event.request).then((response) => {
                    if (!response || response.status !== 200) return response;
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
                    return response;
                }).catch(() => {
                    // Offline e não em cache — retorna index.html para SPA
                    if (event.request.mode === 'navigate') {
                        return caches.match('/index.html');
                    }
                });
            })
        );
        return;
    }

    // 5. Google Fonts & Firebase SDKs — Cache First (mudam raramente)
    if (CACHE_EXTERNAL.some(h => url.hostname.includes(h))) {
        event.respondWith(
            caches.match(event.request).then((cached) => {
                if (cached) return cached;
                return fetch(event.request).then((response) => {
                    if (!response || response.status !== 200) return response;
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
                    return response;
                }).catch(() => null);
            })
        );
        return;
    }

    // 6. Resto — Network first, cache fallback
    event.respondWith(
        fetch(event.request)
            .then((response) => {
                if (response && response.status === 200) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
                }
                return response;
            })
            .catch(() => caches.match(event.request))
    );
});

// ── BACKGROUND SYNC (futuro — para guardar dados quando voltar online) ──
self.addEventListener('sync', (event) => {
    if (event.tag === 'sync-offline-data') {
        console.log('[SW] Background sync — sincronizar dados offline');
        // Aqui poderíamos sincronizar localStorage para Firestore
    }
});

// ── PUSH NOTIFICATIONS ───────────────────────────────────────
self.addEventListener('push', (event) => {
    if (!event.data) return;
    
    let data = {};
    try { data = event.data.json(); } 
    catch(e) { data = { title: event.data.text(), body: '' }; }

    const options = {
        body: data.body || '',
        icon: '/icon-192.png',
        badge: '/icon-192.png',
        vibrate: [100, 50, 100],
        data: { url: data.url || '/' },
        actions: [
            { action: 'open', title: 'Abrir App' },
            { action: 'close', title: 'Fechar' }
        ]
    };

    event.waitUntil(
        self.registration.showNotification(
            data.title || '🦄 App Nonô', 
            options
        )
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    if (event.action === 'close') return;
    const url = event.notification.data?.url || '/';
    event.waitUntil(
        clients.matchAll({ type: 'window' }).then((clientList) => {
            for (const client of clientList) {
                if (client.url === url && 'focus' in client) return client.focus();
            }
            if (clients.openWindow) return clients.openWindow(url);
        })
    );
});
