# ---- build stage ----
# Node 24: Angular 22's CLI requires >= 22.22.3 / 24.15 / 26, so the image is
# pinned above that floor regardless of the developer's local Node version.
FROM node:24-alpine AS build
WORKDIR /workspace

# Dependency layer first so source edits don't invalidate the npm cache
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
