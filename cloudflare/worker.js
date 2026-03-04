// Minimal Host-header rewrite worker.
// Each custom subdomain maps 1:1 to a Cloud Run service.
const ORIGINS = {
  'interview-hub.lcarera.dev': 'interview-hub-frontend-qeczodae3q-rj.a.run.app',
  'i-hub-be.lcarera.dev': 'interview-hub-backend-qeczodae3q-rj.a.run.app',
};

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const origin = ORIGINS[url.hostname];
    if (!origin) return new Response('Not found', { status: 404 });
    url.hostname = origin;
    return fetch(url.toString(), {
      method: request.method,
      headers: request.headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: 'manual',
    });
  },
};
