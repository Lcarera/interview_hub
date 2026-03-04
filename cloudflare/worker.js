const BACKEND_URL = 'https://interview-hub-backend-qeczodae3q-rj.a.run.app';
const FRONTEND_URL = 'https://interview-hub-frontend-qeczodae3q-rj.a.run.app';

// These paths always go to the backend regardless of headers (no Angular SPA routes conflict).
const ALWAYS_BACKEND_PREFIXES = [
  '/auth/google',
  '/auth/token',
  '/actuator',
];

// These paths share the same prefix as Angular SPA routes (/interviews, /interviews/:id).
// Route to backend only when the request carries a Bearer token (XHR from Angular).
// Browser navigations (F5, direct URL) have no Bearer and must reach the frontend SPA.
const BEARER_BACKEND_PREFIXES = [
  '/interviews',
  '/shadowing-requests',
];

function matchesPrefix(pathname, prefixes) {
  return prefixes.some((p) => pathname === p || pathname.startsWith(p + '/'));
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const hasBearer = (request.headers.get('Authorization') || '').startsWith('Bearer ');
    const isBackend =
      matchesPrefix(url.pathname, ALWAYS_BACKEND_PREFIXES) ||
      (hasBearer && matchesPrefix(url.pathname, BEARER_BACKEND_PREFIXES));
    const target = (isBackend ? BACKEND_URL : FRONTEND_URL) + url.pathname + url.search;
    return fetch(target, {
      method: request.method,
      headers: request.headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: 'manual',
    });
  },
};
