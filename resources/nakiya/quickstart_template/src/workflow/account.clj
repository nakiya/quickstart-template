(ns {{top/ns}}.workflow.account
  "Account management workflows built from Mycelium cells.
   Each workflow maps to an Allium surface action."
  (:require [mycelium.core :as myc]
            [{{top/ns}}.cell.account]
            [{{top/ns}}.db :as db]))

;; ----------------------------------------------------------
;; Workflow definitions
;; ----------------------------------------------------------

(def initialize-system-wf
  {:cells      {:start :account/validate-setup
                :init  :account/initialize-system}
   :edges      {:start {:valid :init :invalid :error}
                :init  {:done :end}}
   :dispatches {:start [[:valid   #(:can-setup %)]
                        [:invalid (constantly true)]]
                :init  [[:done (constantly true)]]}})

(def create-account-wf
  {:cells      {:start  :account/validate-create
                :create :account/create-account}
   :edges      {:start  {:valid :create :invalid :error}
                :create {:done :end}}
   :dispatches {:start  [[:valid   #(:admin %)]
                         [:invalid (constantly true)]]
                :create [[:done (constantly true)]]}})

(def disable-account-wf
  {:cells      {:start   :account/validate-disable
                :disable :account/disable-account}
   :edges      {:start   {:valid :disable :invalid :error}
                :disable {:done :end}}
   :dispatches {:start   [[:valid   #(:account %)]
                          [:invalid (constantly true)]]
                :disable [[:done (constantly true)]]}})

(def enable-account-wf
  {:cells      {:start  :account/validate-enable
                :enable :account/enable-account}
   :edges      {:start  {:valid :enable :invalid :error}
                :enable {:done :end}}
   :dispatches {:start  [[:valid   #(:account %)]
                         [:invalid (constantly true)]]
                :enable [[:done (constantly true)]]}})

;; ----------------------------------------------------------
;; Runner functions
;; ----------------------------------------------------------

(defn- on-error
  "Custom error handler: returns the data map with a
   :workflow/error flag instead of throwing."
  [_resources {:keys [data]}]
  (assoc data :workflow/error true))

(defn- run-wf [wf-def ds data]
  (myc/run-workflow
    wf-def {:db ds} data {:on-error on-error}))

(defn initialize-system!
  "One-time system setup: creates the admin account."
  [ds name email password]
  (run-wf initialize-system-wf ds
           {:name name :email email :password password}))

(defn create-account!
  "Admin creates a non-admin account."
  [ds admin-id name email role password]
  (run-wf create-account-wf ds
           {:admin-id admin-id
            :name     name
            :email    email
            :role     role
            :password password}))

(defn disable-account!
  "Admin disables an active non-admin account."
  [ds admin-id account-id]
  (run-wf disable-account-wf ds
           {:admin-id   admin-id
            :account-id account-id}))

(defn enable-account!
  "Admin re-enables a disabled account."
  [ds admin-id account-id]
  (run-wf enable-account-wf ds
           {:admin-id   admin-id
            :account-id account-id}))

(defn list-accounts
  "Returns all accounts."
  [ds]
  (db/list-accounts ds))
