export const environment = {
  production: true,
  apiUrl: '' // empty = same-origin; nginx proxies /auth, /interviews, etc. to the backend container
};
