# Noise Shield

Local-first, privacy-first mobile app that reduces perceived environmental noise through adaptive masking.

## Monorepo structure

```
apps/
  api/      NestJS REST API (PostgreSQL + Prisma)
  mobile/   Expo React Native app (iOS/Android)
packages/
  shared/           Zod schemas, sound catalog, copy
  audio-analysis/   Heuristic analyzer + AudioAnalysisPort
```

## Prerequisites

Install these before running the project locally.

| Tool | Version | Notes |
|------|---------|-------|
| Node.js | 20 LTS+ | `node -v` |
| pnpm | 9+ | Enable via Corepack (see step 1) |
| Docker | latest | Runs local PostgreSQL |
| Git | any | Clone the repo |

For the **mobile app on a device or emulator**, you also need one of:

- **Android**: Android Studio + SDK 34+, emulator or USB device
- **iOS** (macOS only): Xcode 15+, simulator or device

> **Important:** This app uses native modules (`react-native-track-player`, `react-native-mmkv`). You need a **development build** — Expo Go alone is not enough. Use `native:prebuild` + `expo run:android` / `expo run:ios` (steps below).

---

## Development setup (step by step)

Run all commands from the **repository root** unless noted.

### Step 1 — Enable pnpm

```bash
corepack enable
corepack prepare pnpm@9.12.0 --activate
```

Verify: `pnpm -v`

### Step 2 — Install dependencies

```bash
pnpm install
```

This installs all workspace packages (`apps/api`, `apps/mobile`, `packages/*`).

### Step 3 — Start PostgreSQL

```bash
docker compose up -d
```

Wait until the database is healthy:

```bash
docker compose ps
```

You should see `noise-shield-db` with status `healthy` (or `running`).

Default connection (already in `.env.example`):

```
postgresql://noise_shield:noise_shield_dev@localhost:5432/noise_shield
```

### Step 4 — Configure the API

```bash
cp apps/api/.env.example apps/api/.env
```

The defaults work for local development. Optional overrides in `apps/api/.env`:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DATABASE_URL` | Docker Postgres URL | DB connection |
| `JWT_SECRET` | `change-me-in-production` | Token signing |
| `PORT` | `3000` | API listen port |
| `NODE_ENV` | `development` | Enables dev auth fallback |

OAuth client IDs (`GOOGLE_CLIENT_ID`, etc.) are **optional in development**. The API accepts dev tokens when `NODE_ENV=development`.

### Step 5 — Prepare the database

```bash
pnpm --filter @noise-shield/api prisma:generate
pnpm --filter @noise-shield/api prisma:migrate
pnpm --filter @noise-shield/api prisma:seed
```

What each command does:

1. `prisma:generate` — generates the Prisma client
2. `prisma:migrate` — applies schema migrations (creates tables)
3. `prisma:seed` — inserts default remote-config thresholds

### Step 6 — Start the API (terminal 1)

```bash
pnpm --filter @noise-shield/api dev
```

API listens at `http://localhost:3000`. Routes are prefixed with `/v1`.

Verify it is running:

```bash
curl http://localhost:3000/v1/health
```

Expected response:

```json
{"status":"ok"}
```

Leave this terminal open.

### Step 7 — Configure the mobile app

```bash
cp apps/mobile/.env.example apps/mobile/.env
```

Set `EXPO_PUBLIC_API_URL` in `apps/mobile/.env` based on **where the app runs**:

| Where the app runs | `EXPO_PUBLIC_API_URL` |
|--------------------|------------------------|
| iOS Simulator (macOS) | `http://localhost:3000/v1` |
| Android Emulator | `http://10.0.2.2:3000/v1` |
| Physical device (same Wi‑Fi as dev machine) | `http://<your-lan-ip>:3000/v1` |
| WSL2 → Windows Android Emulator | `http://<windows-host-ip>:3000/v1` |

Find your LAN IP:

```bash
# Linux / WSL
hostname -I | awk '{print $1}'

# macOS
ipconfig getifaddr en0
```

> After changing `.env`, restart Expo (`Ctrl+C` then `pnpm --filter @noise-shield/mobile start` again).

### Step 8 — Generate native projects (first time only)

Required for `react-native-track-player` and other native modules:

```bash
pnpm --filter @noise-shield/mobile native:prebuild
```

This creates `apps/mobile/ios` and `apps/mobile/android`. Re-run after changing native plugins in `app.json`.

### Step 9 — Start the mobile dev server (terminal 2)

```bash
pnpm --filter @noise-shield/mobile start
```

Expo Dev Tools opens in the terminal. From there you can press:

- `a` — open Android emulator (after step 10)
- `i` — open iOS simulator (macOS only)

Or run the native build directly (terminal 3):

```bash
# Android
pnpm --filter @noise-shield/mobile android

# iOS (macOS only)
pnpm --filter @noise-shield/mobile ios
```

### Step 10 — Sign in and test (development)

1. Open the app on emulator/device.
2. Tap any sign-in button (Google, Apple, or Facebook).
3. In development, the app sends a dev token and the API accepts it.
4. Complete onboarding → grant or deny microphone → start a masking session.

---

## Daily development workflow

Once initial setup is done, you only need three terminals:

```bash
# Terminal 1 — database (if not already running)
docker compose up -d

# Terminal 2 — API with hot reload
pnpm --filter @noise-shield/api dev

# Terminal 3 — mobile
pnpm --filter @noise-shield/mobile start
```

Rebuild native app only when native deps or `app.json` plugins change:

```bash
pnpm --filter @noise-shield/mobile android   # or ios
```

---

## Build all packages

```bash
pnpm --filter @noise-shield/shared build
pnpm --filter @noise-shield/api build
pnpm --filter @noise-shield/audio-analysis build
pnpm --filter @noise-shield/mobile typecheck
```

Or from root (if turbo tasks are configured for each package):

```bash
pnpm build
```

---

## Troubleshooting

### API cannot connect to Postgres

```bash
docker compose ps          # is noise-shield-db running?
docker compose logs postgres
```

Ensure `DATABASE_URL` in `apps/api/.env` matches `docker-compose.yml` credentials.

### Mobile app shows network errors on sign-in

- Confirm API health: `curl http://localhost:3000/v1/health`
- Check `EXPO_PUBLIC_API_URL` matches your emulator/device (see step 7)
- On physical device, ensure phone and computer are on the same network

### `prisma migrate` fails

```bash
pnpm --filter @noise-shield/api prisma:generate
pnpm --filter @noise-shield/api prisma:migrate
```

### Expo Go does not work

Use a development build (`native:prebuild` + `expo run:android` / `expo run:ios`). Native modules are not supported in Expo Go.

### WSL2 + Android emulator

Run the API inside WSL and point the emulator at the Windows host IP, or use `10.0.2.2` if the emulator runs on the same machine as the API.

---

## Specs

Feature specification and implementation plan: [`specs/001-noise-shield-mvp/`](specs/001-noise-shield-mvp/)

Validation scenarios: [`specs/001-noise-shield-mvp/quickstart.md`](specs/001-noise-shield-mvp/quickstart.md)
