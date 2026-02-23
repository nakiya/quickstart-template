# quickstart-template

A [deps-new](https://github.com/seancorfield/deps-new) template
for full-stack Clojure/ClojureScript projects with authentication,
role-based access control, and account management.

## What you get

- Ring/Jetty backend with Reitit routing and Muuntaja
- SQLite database with Migratus migrations
- Mycelium workflow orchestration with Malli validation
- Reagent/re-frame frontend via shadow-cljs
- Tailwind CSS v4 + DaisyUI v5
- Auth system (login, logout, sessions, Bearer tokens)
- Account management (CRUD, disable/enable, RBAC)
- Backend tests (clojure.test) + E2E tests (Playwright)
- CLAUDE.md with development guidelines

## Prerequisites

- Java 11+
- [Clojure CLI](https://clojure.org/guides/install_clojure) 1.11.1.1149+
- Node.js + npm
- [deps-new](https://github.com/seancorfield/deps-new) installed as a tool

## Install deps-new (one-time)

```bash
clojure -Ttools install-latest :lib io.github.seancorfield/deps-new :as new
```

## Create a new project

```bash
clojure -Tnew create \
  :template io.github.nakiya/quickstart-template \
  :name myapp
```

Or with a target directory:

```bash
clojure -Tnew create \
  :template io.github.nakiya/quickstart-template \
  :name myapp \
  :target-dir myapp
```

## Start developing

```bash
cd myapp
npm install
clojure -M:dev
```

Opens http://localhost:3000. First visit shows a setup page
to create the initial admin account.

## Run tests

```bash
clojure -M:test        # backend tests
npm run e2e            # Playwright E2E tests
```

## Local development of this template

To test the template locally before pushing:

```bash
clojure -Sdeps '{:deps {nakiya/quickstart-template {:local/root "."}}}' \
  -Tnew create :template nakiya/quickstart-template \
  :name testapp :target-dir '"testapp"'

cd testapp
npm install
clojure -M:test
npm run e2e
```
