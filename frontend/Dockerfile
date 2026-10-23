FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS base
WORKDIR /app
RUN npm install --global pnpm@11.24.0
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --store-dir /tmp/pnpm-store
COPY . .

FROM base AS build
RUN pnpm test && pnpm build

FROM base AS dev
EXPOSE 5173
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 CMD node -e "fetch('http://127.0.0.1:5173/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["pnpm", "dev"]
