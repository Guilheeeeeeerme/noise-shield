# Noise Shield

Local-first, privacy-first mobile app that reduces perceived environmental noise through adaptive masking.

## Monorepo Structure

```
apps/
  api/      NestJS REST API (PostgreSQL + Prisma)
  mobile/   Expo React Native app (iOS/Android)
packages/
  shared/           Zod schemas, sound catalog, copy
  audio-analysis/   Heuristic analyzer + AudioAnalysisPort
```

## Prerequisites

- Node.js 20+
- pnpm 9+ (`corepack enable && corepack prepare pnpm@9.12.0 --activate`)
- Docker (for local PostgreSQL)

## Quick Start

```bash
# Install dependencies
pnpm install

# Start database
docker compose up -d

# API setup
cp apps/api/.env.example apps/api/.env
pnpm --filter @noise-shield/api prisma:migrate
pnpm --filter @noise-shield/api prisma:seed
pnpm --filter @noise-shield/api dev

# Mobile (separate terminal)
cp apps/mobile/.env.example apps/mobile/.env
pnpm --filter @noise-shield/mobile start
```

## Build

```bash
pnpm build
```

## Specs

Feature specification and implementation plan: [`specs/001-noise-shield-mvp/`](specs/001-noise-shield-mvp/)
