# =============================================================================
# Nexus OMS Frontend — Production Dockerfile
# React + Vite + TypeScript, multi-stage build served by nginx
# =============================================================================

# ---- Stage 1: Build ----
FROM node:20-alpine AS builder
WORKDIR /app

# Copy package files first for layer caching
COPY package.json package-lock.json* ./
RUN npm ci || npm install

# Copy source and build
COPY . .
RUN npm run build

# ---- Stage 2: Serve with nginx ----
FROM nginx:alpine

# Patch OS packages in the runtime layer.
# Trivy gates on HIGH/CRITICAL, and nginx:alpine ships Alpine packages that carry
# CVEs with upstream fixes (seen 2026-10: libexpat CVE-2026-93990 2.8.4-r0 -> 2.8.5-r0,
# pcre2 CVE-2026-103111 10.48-r0 -> 10.49-r0). `nginx:alpine` is a floating tag, so the
# fix arrives whenever the base is rebuilt -- but an explicit upgrade makes the build
# reproducible now instead of depending on when the tag was last republished.
RUN apk upgrade --no-cache \
 && rm -rf /var/cache/apk/*

# Copy the built SPA
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config (SPA routing + /api proxy to backend)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -sf http://localhost/ >/dev/null 2>&1 || exit 1
