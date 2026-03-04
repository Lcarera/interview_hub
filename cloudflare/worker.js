const BACKEND_URL = 'https://interview-hub-backend-qeczodae3q-rj.a.run.app';
const FRONTEND_URL = 'https://interview-hub-frontend-qeczodae3q-rj.a.run.app';

const BACKEND_PREFIXES = [
  '/auth/google',
  '/auth/token',
  '/interviews',
  '/shadowing-requests',
  '/actuator',
];

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const isBackend = BACKEND_PREFIXES.some(
      (p) => url.pathname === p || url.pathname.startsWith(p + '/')
    );
    const target = (isBackend ? BACKEND_URL : FRONTEND_URL) + url.pathname + url.search;
    return fetch(target, {
      method: request.method,
      headers: request.headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
    });
  },
};
