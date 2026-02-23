# Clojure Coding Style

Reference guide for Clojure conventions in this project.
Consult when writing or reviewing Clojure code.

## Naming
- `kebab-case` for functions, vars, and namespaces
- `PascalCase` for protocols and records
- Predicates end with `?` (e.g. `valid?`, `empty?`)
- Unsafe or mutating functions end with `!` (e.g. `reset!`, `swap!`)
- Private vars use `defn-` or `^:private` metadata

## Functions
- Prefer small, pure functions with a single responsibility
- Use destructuring in function arguments rather than repeated `get` calls
- Avoid deeply nested expressions; use `let` to name intermediate values
- Prefer `->` and `->>` threading macros over deeply nested calls
- Use `juxt`, `comp`, `partial` for functional composition

## Data
- Prefer plain maps and vectors over custom types unless protocols are needed
- Use keywords as map keys
- Use `defrecord` or `deftype` only when you need polymorphism via protocols
- Avoid mutable state; use atoms only at the boundaries of your system

## Namespaces
- One namespace per file; file path must match namespace name
- Require namespaces with meaningful aliases (e.g. `[clojure.string :as str]`)
- Avoid `:use` and `(use ...)` — always use `:require` with aliases
- Keep the `ns` form tidy; sort requires alphabetically

## Error Handling
- Use `ex-info` with a descriptive message and a data map for exceptions
- Prefer returning error values (e.g. `{:error ...}`) over throwing at internal boundaries
- Only catch exceptions at system boundaries (HTTP handlers, main entry points)

## Formatting
- Limit line length to ~80 characters
- Two-space indentation (no tabs)
- One blank line between top-level forms
- No trailing whitespace
- Use `comment` blocks for exploratory REPL expressions, not `#_`
