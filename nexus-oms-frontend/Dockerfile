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

# Copy the built SPA
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config (SPA routing + /api proxy to backend)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -sf http://localhost/ >/dev/null 2>&1 || exit 1
