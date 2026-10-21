# 앞단 이미지. 프론트를 빌드해 Caddy에 넣는다. Caddy가 HTTPS 인증서를 받고 /api, /ws를 백엔드로 넘긴다
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
