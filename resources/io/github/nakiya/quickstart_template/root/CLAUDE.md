# Claude Guidelines for this Project


## Feature Development Workflow

The primary flow for adding features:

1. **Spec** — Run `/allium <feature>` to produce an Allium
   spec in `spec/<domain>.allium`. This defines entities,
   surfaces, and rules that capture intent precisely.
2. **Implement** — Use the spec to drive implementation
   through the architecture layers below, using Mycelium
   workflows to orchestrate the business logic.

Allium specs are the source of truth for what a feature does.
Read the existing spec (`spec/account.allium`) to understand
the notation. Every entity, rule, and surface constraint in
a spec maps directly to cells, workflows, and validations
in the code.

## REPL-Driven Development (clojure-mcp)

The REPL is the primary verification method. Use `clojure_eval`
to test code at every layer before moving on:

- **DB function** — eval a query against the dev datasource
- **Cell** — call `cell/cell-spec` with sample input, check
  the returned `:handler` result
- **Workflow** — run the workflow end-to-end, inspect the
  result map for `:workflow/error`
- **Handler** — eval a Ring request map through the handler fn
- Always `(require '[ns] :reload)` after editing a file

Other clojure-mcp tools:
- `clojure_edit` — fallback when the standard Edit tool fails
  on Clojure files
- `paren_repair` — fix unbalanced delimiters after edits
- `deps_list`, `deps_grep`, `deps_read` — explore dependency
  source code and find patterns in libraries

## Coding Style

Follow `docs/clojure-style.md` for naming, formatting, and idioms.

## Development — Single Command

```bash
clojure -M:dev
```

Starts everything: backend on :3000 (hot reload), Tailwind CSS
watcher, and shadow-cljs watch.

### CSS (Tailwind v4 + DaisyUI v5)

- Source: `src/app.css` (imports Tailwind + DaisyUI plugin)
- Output: `resources/public/css/app.css` (generated, gitignored)
- The dev server runs the Tailwind CLI watcher automatically
- For standalone builds: `npm run css:build`
- UI components use DaisyUI classes (`btn`, `badge`, `table`,
  `alert`, `navbar`, `input`, `select`, `fieldset`)

**Never stop the server to run tests, add deps, or reload code.**

## Testing

- Run backend and E2E tests in parallel (background tasks)
- When implementing a feature, write backend tests and E2E
  tests in parallel since they are independent
- Run tests: `clojure -M:test`
- Tests live in `test/` mirroring `src/` structure
- Test handlers by calling them directly with request maps, not by binding to a port
- Use `clojure.test` with `deftest` and `is`; use fixtures sparingly

### E2E (Playwright)

Runs against an isolated server (port 3001, `{{top/ns}}-e2e.db`).
DB wiped automatically. Dev server never touched.

```bash
npm run e2e          # headless
npm run e2e:headed   # visible browser
```

## Dependencies

- Add to `deps.edn` under `:deps` (runtime) or the appropriate alias
- Load into running REPL with `clojure.repl.deps/add-libs` (Clojure 1.12+)
- Use only standard Clojure tooling — no third-party dep loaders

## Architecture: Spec to Implementation

Allium specs map to implementation layers as follows:
- **entity** fields/constraints → SQL migration + DB functions
- **rule** requires/ensures → Cell validation + action logic
- **surface** provides/exposes → Workflow orchestration, Handlers, UI

Work through these layers in order. Each has a single
responsibility:

| Layer    | File pattern                          | Responsibility        |
|----------|---------------------------------------|-----------------------|
| SQL      | `resources/migrations/`               | Schema & constraints  |
| DB       | `src/{{top/ns}}/db.clj`                     | Queries & inserts     |
| Cell     | `src/{{top/ns}}/cell/<domain>.clj`          | Single compute step   |
| Workflow | `src/{{top/ns}}/workflow/<domain>.clj`      | Orchestrate cells     |
| Handler  | `src/{{top/ns}}/handler/<domain>.clj`       | HTTP <-> workflow     |
| Route    | `src/{{top/ns}}/router.clj`                 | URL -> handler map    |
| Event    | `src/{{top/ns}}/ui/events.cljs`             | HTTP calls & state    |
| View     | `src/{{top/ns}}/ui/views.cljs`              | Reagent components    |
| Test     | `test/{{top/ns}}/<layer>/<domain>_test.clj` | Per-layer tests       |

```
UI view -> re-frame event -> HTTP POST /api/<resource>
  -> handler (extract headers/body)
    -> workflow (validate -> act)
      -> cell (DB read/write)
        -> db function (next.jdbc)
          -> SQLite
```

## Error Recovery

**Port already in use** — "Address already in use" on startup
means a prior process is still bound to :3000. Find it with
`lsof -ti:3000` and kill it, or choose another port.

**Migration failure** — "table already exists" or constraint
errors usually mean the SQLite file is out of sync with the
migration set. Delete `{{top/ns}}.db` and restart — it will be
recreated from scratch. Never hand-edit the DB file.

**shadow-cljs compilation error** — Check the terminal for
the CLJS namespace and line number. Common causes: missing
`require`, mismatched parens, or a JS interop typo. Fix the
source file; shadow-cljs will recompile automatically.

**Workflow returns `:workflow/error true`** — The `on-error`
callback caught an exception inside a cell. The error message
is in the `:error` key of the result map. Check the cell's
handler function, not the workflow wiring.

**Unbalanced parens after edit** — Run `paren_repair` (MCP)
on the file. If MCP is unavailable, re-read the file and
redo the edit with more surrounding context.

## Long Tasks: Checkpointing

When implementing a feature across multiple layers, use the
todo list to track which layers are done. Verify at each
checkpoint before moving on:

1. **SQL + DB + Cell + Workflow** — run `clojure -M:test`
   (or let the watcher catch it). All backend logic should
   pass before writing any HTTP or UI code.
2. **Handler + Route** — verify with `clojure_eval` or curl:
   `curl -X POST http://localhost:3000/api/<resource> ...`
3. **Event + View** — check the browser.
4. **E2E tests** — Write Playwright tests in `test/e2e/` covering
   the feature's user-facing flows. Run `npm run e2e` and
   ensure all tests pass before considering the feature done.
   Every feature must have E2E coverage — this is not optional.

If context is getting long, write progress notes to a
scratch file (e.g. `notes/<feature>.md`) listing completed
layers, open questions, and next steps. Read it back at the
start of a resumed session.

## Canonical Example: "Account" Feature

The account + auth features are the reference implementation.
Read these files in order to understand the pattern for each
layer:

1. **SQL** — `resources/migrations/20260222000000-initial-schema.up.sql`
   Schema with CHECK constraints, REFERENCES, defaults.

2. **DB** — `src/{{top/ns}}/db.clj` (`insert-account!`, `find-account-by-id`)
   Thin wrappers around `jdbc/execute-one!`. Inserts return full rows.

3. **Cells** — `src/{{top/ns}}/cell/account.clj`
   Each cell is a `defmethod` on `cell/cell-spec` with `:id`,
   `:handler`, and `:schema` (Malli). Validation cells check
   preconditions and enrich data; action cells perform writes.

4. **Workflow** — `src/{{top/ns}}/workflow/account.clj`
   Mycelium DAG wiring cells with edges and dispatch predicates.
   `on-error` adds `:workflow/error true` instead of throwing.

5. **Handler** — `src/{{top/ns}}/handler/account.clj`
   Extract headers/body, call workflow, translate to Ring response.

6. **Route** — `src/{{top/ns}}/router.clj`
   Datasource injected by `wrap-ds` middleware.
   JSON via Muuntaja middleware.

7. **UI Event** — `src/{{top/ns}}/ui/events.cljs`
   re-frame `reg-event-fx` with custom `:http` effect handler.

8. **UI View** — `src/{{top/ns}}/ui/views.cljs`
   Reagent components subscribing to re-frame state.

9. **Tests**
   - Handler: `test/{{top/ns}}/handler/account_test.clj` — call handler with request map
   - Workflow: `test/{{top/ns}}/workflow/account_test.clj` — call workflow with datasource
