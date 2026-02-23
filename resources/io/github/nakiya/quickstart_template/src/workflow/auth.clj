(ns {{top/ns}}.workflow.auth
  (:require [mycelium.core :as myc]
            [{{top/ns}}.cell.auth]
            [{{top/ns}}.db :as db]))

;; ----------------------------------------------------------
;; Workflow definitions
;; ----------------------------------------------------------

(def login-wf
  {:cells      {:start  :auth/validate-login
                :create :auth/create-session}
   :edges      {:start  {:valid :create :invalid :error}
                :create {:done :end}}
   :dispatches {:start  [[:valid   #(:account %)]
                         [:invalid (constantly true)]]
                :create [[:done (constantly true)]]}})

(def logout-wf
  {:cells      {:start :auth/revoke-sessions}
   :edges      {:start {:done :end}}
   :dispatches {:start [[:done (constantly true)]]}})

(def change-password-wf
  {:cells      {:start  :auth/validate-change-password
                :update :auth/update-password}
   :edges      {:start  {:valid :update :invalid :error}
                :update {:done :end}}
   :dispatches {:start  [[:valid   #(:credential %)]
                         [:invalid (constantly true)]]
                :update [[:done (constantly true)]]}})

(def reset-password-wf
  {:cells      {:start :auth/validate-reset-password
                :reset :auth/reset-password}
   :edges      {:start {:valid :reset :invalid :error}
                :reset {:done :end}}
   :dispatches {:start [[:valid   #(:account %)]
                        [:invalid (constantly true)]]
                :reset [[:done (constantly true)]]}})

;; ----------------------------------------------------------
;; Runner functions
;; ----------------------------------------------------------

(defn- on-error
  [_resources {:keys [data]}]
  (assoc data :workflow/error true))

(defn- run-wf [wf-def ds data]
  (myc/run-workflow
    wf-def {:db ds} data {:on-error on-error}))

(defn login!
  "Authenticates a user and creates a session."
  [ds email password]
  (run-wf login-wf ds {:email email :password password}))

(defn logout!
  "Revokes all active sessions for an account."
  [ds account-id]
  (run-wf logout-wf ds {:account-id account-id}))

(defn change-password!
  "Changes the authenticated user's password."
  [ds account-id current-password new-password]
  (run-wf change-password-wf ds
           {:account-id       account-id
            :current-password current-password
            :new-password     new-password}))

(defn reset-password!
  "Admin resets another account's password."
  [ds admin-id account-id new-password]
  (run-wf reset-password-wf ds
           {:admin-id     admin-id
            :account-id   account-id
            :new-password new-password}))

(defn session->account
  "Looks up the account for a session token. Returns nil
   if the token is invalid or expired."
  [ds token]
  (when-let [session (db/find-session-by-token ds token)]
    (let [expires (java.time.Instant/parse
                    (:expires-at session))]
      (when (.isAfter expires (java.time.Instant/now))
        (db/find-account-by-id
          ds (:account-id session))))))
