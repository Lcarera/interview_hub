# Stage 1 — build Angular app
FROM oven/bun:1.2-alpine AS build
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile
COPY . .
RUN bunx ng build --configuration production

# Stage 2 — serve with nginx
# Use nginx.prod.conf for Cloud Run (no proxy_pass); nginx.conf for local Docker Compose
ARG NGINX_CONF=nginx.conf
FROM nginx:1.27-alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
EXPOSE 80
