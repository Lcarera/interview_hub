# Interview Hub — Cloudflare Worker

Cloudflare Worker acting as an edge reverse proxy that routes requests between the GCP Cloud Run backend and frontend services.

## Why a Worker?

The Angular SPA and Spring Boot API share URL path prefixes (e.g. `/interviews` is both an Angular route and an API endpoint). A browser navigation to `/interviews` (F5 refresh, direct URL) must serve the SPA, while an XHR `GET /interviews` from Angular must reach the API. The Worker distinguishes between the two by checking for the `Authorization: Bearer` header.

## Routing Rules

Requests to `interview-hub.lcarera.dev/*` are intercepted by the Worker and forwarded to either the backend or frontend Cloud Run service.

### Always routed to backend

These paths have no SPA equivalent — they always go to the backend regardless of headers:

| Path Prefix     | Purpose                        |
|-----------------|--------------------------------|
| `/auth/google`  | Google OAuth redirect + callback |
| `/auth/token`   | Token exchange endpoint        |
| `/actuator`     | Health check                   |

### Conditionally routed (Bearer token detection)

These paths are shared between Angular routes and API endpoints:

| Path Prefix           | Has `Authorization: Bearer`? | Route    |
|-----------------------|------------------------------|----------|
| `/interviews`         | Yes (XHR from Angular)       | Backend  |
| `/interviews`         | No (browser navigation)      | Frontend |
| `/shadowing-requests` | Yes                          | Backend  |
| `/shadowing-requests` | No                           | Frontend |

### All other paths

Any path not matching the above prefixes routes to the frontend (Angular SPA).

## Request Flow Examples

| Scenario                         | Path               | Bearer? | Target   |
|----------------------------------|---------------------|---------|----------|
| User clicks "Sign in"           | `/auth/google`      | No      | Backend  |
| Angular fetches interview list  | `/api/interviews`   | Yes     | Backend  |
| User hits F5 on interview page  | `/interviews/abc`   | No      | Frontend |
| User opens app for first time   | `/`                 | No      | Frontend |
| Angular creates shadow request  | `/api/shadowing-requests/...` | Yes | Backend |
| Health check                    | `/actuator/health`  | No      | Backend  |

## Request Forwarding

The Worker preserves the original request:
- **Method:** forwarded as-is (GET, POST, PUT, DELETE, etc.)
- **Headers:** all headers forwarded (including Authorization)
- **Body:** forwarded for non-GET/HEAD requests
- **Query string:** preserved
- **Redirects:** handled manually (`redirect: 'manual'`) — browser follows redirects itself (important for the OAuth flow)

## Configuration

### `wrangler.toml`

```toml
name = "interview-hub"
main = "worker.js"
compatibility_date = "2024-01-01"
account_id = "..."

routes = [
  { pattern = "interview-hub.lcarera.dev/*", zone_id = "..." }
]
```

### Backend/Frontend URLs

The Cloud Run service URLs are hardcoded in `worker.js`:

```js
const BACKEND_URL = 'https://interview-hub-backend-....run.app';
const FRONTEND_URL = 'https://interview-hub-frontend-....run.app';
```

## Deployment

```bash
cd cloudflare

# Deploy to Cloudflare
npx wrangler deploy

# Local development
npx wrangler dev
```

## Files

| File           | Description                      |
|----------------|----------------------------------|
| `worker.js`    | Worker source code (routing logic) |
| `wrangler.toml`| Wrangler CLI configuration        |
