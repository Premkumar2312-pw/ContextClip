# =============================================================================
# ContextClip Frontend — Multi-stage Docker Build
# =============================================================================
# Stage 1: Node builder — installs dependencies and runs `npm run build`
# Stage 2: nginx runtime — serves the compiled static files
#
# IMPORTANT — Production API Routing (Phase 9B note):
# ----------------------------------------------------
# In development, Vite's dev server proxies /api requests to localhost:8080
# via vite.config.ts. The compiled production bundle makes absolute /api
# fetch calls (e.g. GET /api/clipboard) without a base URL prefix.
#
# In production (this image), nginx must proxy /api/* to the backend service.
# The nginx config below includes this reverse proxy rule using the
# BACKEND_URL environment variable (defaults to http://backend:8080).
#
# This means the frontend container itself handles the /api routing —
# no changes to React source code are needed.
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Node Builder
# ---------------------------------------------------------------------------
# Use Node 20 LTS Alpine — small image, LTS stability.
FROM node:20-alpine AS builder

WORKDIR /build

# Copy package manifests first for dependency layer caching.
COPY package.json package-lock.json* ./

# Install all dependencies (including devDependencies needed for Vite build).
# --frozen-lockfile prevents accidental lockfile mutations.
RUN npm ci --frozen-lockfile

# Copy source files
COPY . .

# Build the production bundle (tsc + vite build as defined in package.json)
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: nginx Runtime
# ---------------------------------------------------------------------------
# nginx:1.27-alpine — official stable Alpine image; ~25 MB.
FROM nginx:1.27-alpine AS runtime

# Metadata
LABEL org.opencontainers.image.title="contextclip-frontend"
LABEL org.opencontainers.image.description="ContextClip React/Vite Frontend served by nginx"

# Remove the default nginx site configuration
RUN rm /etc/nginx/conf.d/default.conf

# Install the custom nginx configuration that:
#   1. Serves the React SPA (with fallback to index.html for client-side routing)
#   2. Reverse-proxies /api/* to the backend service
COPY nginx.conf /etc/nginx/conf.d/contextclip.conf

# Copy the compiled production build from the builder stage
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

# nginx runs as root by default in the official image;
# the worker processes drop privileges automatically.
CMD ["nginx", "-g", "daemon off;"]
