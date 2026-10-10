# Gateway image: NGINX serving the built dashboard and load-balancing the APIs.
FROM node:24-alpine AS dashboard
WORKDIR /dashboard
COPY dashboard/package.json dashboard/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY dashboard/ ./
RUN npm run build

FROM nginx:1.29-alpine
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=dashboard /dashboard/dist /usr/share/nginx/html
