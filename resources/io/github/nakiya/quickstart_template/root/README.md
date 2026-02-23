# {{top/ns}}

A fullstack Clojure/ClojureScript {{top/ns}} template with
authentication, role-based access control, and account
management baked in.

**Stack:** Ring/Jetty, Reitit, Muuntaja, SQLite (next.jdbc),
Mycelium workflows, Reagent/re-frame, Tailwind v4 + DaisyUI v5,
Playwright E2E tests.

## Prerequisites

- Java 11+ (JDK)
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Node.js + npm

## Quick start

```bash
npm install
clojure -M:dev
```

Opens **http://localhost:3000**. First visit shows a setup page
to create the initial admin account. Starts:

- Backend with hot reload
- shadow-cljs watch (ClojureScript hot reload)
- Tailwind CSS watcher

## What's included

- **System setup** — one-time admin account creation
- **Auth** — login, logout, session persistence (Bearer tokens)
- **Account management** — create, list, disable/enable (admin only)
- **RBAC** — admin, cashier, manager, inventory_clerk, accountant roles
- **Mycelium workflows** — cell-based business logic orchestration
- **Malli validation** — schema-driven input validation in cells
- **SQLite migrations** — via Migratus, auto-run on startup

## Project structure

```
src/
  {{top/ns}}/
    core.clj                 # -main entry point (prod)
    server.clj               # Jetty start/stop + migrations
    router.clj               # Reitit routes + middleware
    db.clj                   # SQL queries (next.jdbc)
    cell/
      account.clj            # Account validation & action cells
      auth.clj               # Auth cells (login, session)
    workflow/
      account.clj            # Account workflows (Mycelium DAGs)
      auth.clj               # Auth workflows
    handler/
      health.clj             # GET /api/health
      account.clj            # Account HTTP handlers
      auth.clj               # Auth HTTP handlers
    ui/
      app.cljs               # ClojureScript entry point
      db.cljs                # re-frame app-db schema
      events.cljs            # re-frame events + HTTP effect
      subs.cljs              # re-frame subscriptions
      views.cljs             # Reagent components
  app.css                    # Tailwind + DaisyUI source
dev/
  user.clj                   # REPL helpers (start/stop/restart)
  {{top/ns}}/dev.clj               # Dev lifecycle
test/
  {{top/ns}}/
    handler/
      account_test.clj       # Account handler tests
      auth_test.clj          # Auth handler tests
    workflow/
      account_test.clj       # Account workflow tests
    router_test.clj          # Route tests
    core_test.clj            # Basic smoke test
    e2e_server.clj           # Isolated E2E test server
  e2e/
    app.spec.js              # Playwright E2E tests
resources/
  migrations/                # SQL migrations (Migratus)
  public/
    index.html               # SPA shell
spec/
  account.allium             # Account domain spec
  auth.allium                # Auth domain spec
docs/
  clojure-style.md           # Coding style guide
```

## Architecture

Each feature flows through these layers:

```
UI view → re-frame event → HTTP → handler → workflow → cell → db → SQLite
```

| Layer    | File pattern                      | Responsibility       |
|----------|-----------------------------------|----------------------|
| SQL      | `resources/migrations/`           | Schema & constraints |
| DB       | `src/{{top/ns}}/db.clj`                 | Queries & inserts    |
| Cell     | `src/{{top/ns}}/cell/<domain>.clj`      | Single compute step  |
| Workflow | `src/{{top/ns}}/workflow/<domain>.clj`  | Orchestrate cells    |
| Handler  | `src/{{top/ns}}/handler/<domain>.clj`   | HTTP ↔ workflow      |
| Route    | `src/{{top/ns}}/router.clj`             | URL → handler map    |
| Event    | `src/{{top/ns}}/ui/events.cljs`         | HTTP calls & state   |
| View     | `src/{{top/ns}}/ui/views.cljs`          | Reagent components   |

## API endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | — | SPA shell |
| GET | `/api/health` | — | Status + initialization check |
| POST | `/api/setup` | — | Create initial admin (once) |
| POST | `/api/auth/login` | — | Login, returns Bearer token |
| POST | `/api/auth/logout` | Bearer | End session |
| GET | `/api/auth/session` | Bearer | Validate session |
| POST | `/api/auth/change-password` | Bearer | Change own password |
| POST | `/api/auth/reset-password` | Bearer | Admin resets another user's password |
| GET | `/api/accounts` | Admin | List all accounts |
| POST | `/api/accounts` | Admin | Create account |
| PUT | `/api/accounts/:id/disable` | Admin | Disable account |
| PUT | `/api/accounts/:id/enable` | Admin | Re-enable account |

## Testing

```bash
# Backend tests (43 tests)
clojure -M:test

# E2E tests (17 tests, Playwright)
npm run e2e

# E2E with visible browser
npm run e2e:headed
```

## Available aliases

| Alias | Command | Purpose |
|-------|---------|---------|
| `:dev` | `clojure -M:dev` | Start all dev services |
| `:test` | `clojure -M:test` | Run backend tests |
| `:e2e` | `clojure -M:e2e` | Start isolated E2E server |
| `:nrepl` | `clojure -M:nrepl` | Start nREPL server |

## REPL usage

Connect via nREPL (`:nrepl` alias or editor) to land in the
`user` namespace:

```clojure
(start)            ;; start all dev services
(start 4000)       ;; start on custom port
(stop)             ;; stop everything
(restart)          ;; restart everything
```

## Creating a new project from this template

```bash
# 1. Clone and rename
cp -r {{top/ns}} ~/projects/myproject
cd ~/projects/myproject

# 2. Clean build artifacts
rm -rf .cpcache .shadow-cljs .lsp .clj-kondo node_modules \
       resources/public/js resources/public/css/app.css \
       .nrepl-port {{top/ns}}.db {{top/ns}}-e2e.db

# 3. Rename directories
mv src/{{top/ns}} src/myproject
mv dev/{{top/ns}} dev/myproject
mv test/{{top/ns}} test/myproject

# 4. Find-and-replace namespace prefix
grep -rl '{{top/ns}}' src/ dev/ test/ spec/ shadow-cljs.edn deps.edn | \
  xargs sed -i '' 's/{{top/ns}}/myproject/g'

# 5. Fresh git repo
rm -rf .git
git init && git add -A && git commit -m "Initial commit from {{top/ns}}"

# 6. Install and start
npm install
clojure -M:dev
```

### Namespace rename checklist

After find-and-replace, verify these files reference your new namespace:

- `src/myproject/core.clj`
- `src/myproject/server.clj`
- `src/myproject/router.clj`
- `src/myproject/db.clj`
- `src/myproject/ui/app.cljs`
- `dev/user.clj`
- `dev/myproject/dev.clj`
- `test/myproject/e2e_server.clj`
- `shadow-cljs.edn` — `:init-fn`
- `deps.edn` — `:e2e` alias main-opts
