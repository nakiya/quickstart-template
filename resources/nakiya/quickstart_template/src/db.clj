(ns {{top/ns}}.db
  (:require [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn datasource
  "Creates a SQLite datasource for the given db file path."
  [dbfile]
  (jdbc/get-datasource
    {:dbtype "sqlite" :dbname dbfile}))

(defn migrate!
  "Runs pending migratus migrations."
  [ds]
  (migratus/migrate
    {:store         :database
     :migration-dir "migrations/"
     :db            {:datasource ds}}))

(def ^:private as-unqualified
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn system-initialized?
  "Returns true when the system row has initialized = 1."
  [ds]
  (let [row (jdbc/execute-one!
              ds
              ["SELECT initialized FROM system WHERE id = 1"]
              as-unqualified)]
    (= 1 (:initialized row))))

(defn mark-initialized!
  "Sets the system row to initialized."
  [ds]
  (jdbc/execute!
    ds
    ["UPDATE system SET initialized = 1 WHERE id = 1"]))

(defn find-account-by-id
  "Finds an account by its integer id."
  [ds id]
  (jdbc/execute-one!
    ds
    ["SELECT * FROM accounts WHERE id = ?" id]
    as-unqualified))

(defn find-account-by-email
  "Finds an account by email."
  [ds email]
  (jdbc/execute-one!
    ds
    ["SELECT * FROM accounts WHERE email = ?" email]
    as-unqualified))

(defn insert-account!
  "Inserts an account and returns the new row."
  [ds {:keys [name email role created-by]}]
  (let [now (.toString (java.time.Instant/now))
        res (jdbc/execute-one!
              ds
              ["INSERT INTO accounts
                  (name, email, role, status,
                   created_at, created_by)
                VALUES (?, ?, ?, 'active', ?, ?)"
               name email role now created-by]
              (merge as-unqualified
                     {:return-keys true}))
        id  (or (:id res)
                (:last-insert-rowid res)
                (get res (keyword "last-insert-rowid()")))]
    (find-account-by-id ds id)))

(defn update-account-status!
  "Sets the status of an account and returns the updated row."
  [ds id status]
  (jdbc/execute!
    ds
    ["UPDATE accounts SET status = ? WHERE id = ?"
     status id])
  (find-account-by-id ds id))

(defn list-accounts
  "Returns all accounts."
  [ds]
  (jdbc/execute!
    ds
    ["SELECT * FROM accounts ORDER BY id"]
    as-unqualified))

;; ----------------------------------------------------------
;; Credential queries
;; ----------------------------------------------------------

(defn find-credential-by-account
  "Finds a credential by account id."
  [ds account-id]
  (jdbc/execute-one!
    ds
    ["SELECT * FROM credentials WHERE account_id = ?"
     account-id]
    as-unqualified))

(defn find-credential-by-email
  "Finds a credential by joining on account email."
  [ds email]
  (jdbc/execute-one!
    ds
    ["SELECT c.* FROM credentials c
      JOIN accounts a ON a.id = c.account_id
      WHERE a.email = ?"
     email]
    as-unqualified))

(defn insert-credential!
  "Inserts a credential row."
  [ds {:keys [account-id password-hash]}]
  (jdbc/execute-one!
    ds
    ["INSERT INTO credentials
        (account_id, password_hash, failed_attempts)
      VALUES (?, ?, 0)"
     account-id password-hash]
    (merge as-unqualified {:return-keys true})))

(defn update-credential-failed-attempts!
  "Updates failed attempts and optionally locked_until."
  [ds account-id failed-attempts locked-until]
  (jdbc/execute!
    ds
    ["UPDATE credentials
      SET failed_attempts = ?, locked_until = ?
      WHERE account_id = ?"
     failed-attempts locked-until account-id]))

(defn reset-credential-lockout!
  "Clears lockout and resets failed attempts."
  [ds account-id]
  (jdbc/execute!
    ds
    ["UPDATE credentials
      SET failed_attempts = 0, locked_until = NULL
      WHERE account_id = ?"
     account-id]))

(defn update-credential-password!
  "Updates the password hash and resets lockout."
  [ds account-id password-hash]
  (jdbc/execute!
    ds
    ["UPDATE credentials
      SET password_hash = ?, failed_attempts = 0,
          locked_until = NULL
      WHERE account_id = ?"
     password-hash account-id]))

;; ----------------------------------------------------------
;; Session queries
;; ----------------------------------------------------------

(defn find-session-by-token
  "Finds an active session by token."
  [ds token]
  (jdbc/execute-one!
    ds
    ["SELECT * FROM sessions
      WHERE token = ? AND status = 'active'"
     token]
    as-unqualified))

(defn insert-session!
  "Inserts a session and returns the new row."
  [ds {:keys [account-id token expires-at]}]
  (let [now (.toString (java.time.Instant/now))
        res (jdbc/execute-one!
              ds
              ["INSERT INTO sessions
                  (account_id, token, status,
                   created_at, expires_at)
                VALUES (?, ?, 'active', ?, ?)"
               account-id token now expires-at]
              (merge as-unqualified
                     {:return-keys true}))
        id  (or (:id res)
                (:last-insert-rowid res)
                (get res (keyword "last-insert-rowid()")))]
    (jdbc/execute-one!
      ds
      ["SELECT * FROM sessions WHERE id = ?" id]
      as-unqualified)))

(defn revoke-sessions!
  "Revokes all active sessions for an account."
  [ds account-id]
  (jdbc/execute!
    ds
    ["UPDATE sessions SET status = 'revoked'
      WHERE account_id = ? AND status = 'active'"
     account-id]))
