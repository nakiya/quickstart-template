(ns {{top/ns}}.cell.auth
  (:require [buddy.hashers :as hashers]
            [mycelium.cell :as cell]
            [{{top/ns}}.db :as db]))

;; ----------------------------------------------------------
;; Login cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :auth/validate-login [_]
  {:id      :auth/validate-login
   :handler (fn [{:keys [db]} {:keys [email password]
                                :as   data}]
              (let [account    (db/find-account-by-email
                                 db email)
                    credential (when account
                                 (db/find-credential-by-account
                                   db (:id account)))]
                (cond
                  (nil? account)
                  (assoc data :error "Invalid credentials")

                  (not= "active" (:status account))
                  (assoc data :error "Account is disabled")

                  (nil? credential)
                  (assoc data :error "Invalid credentials")

                  (and (:locked-until credential)
                       (.isAfter
                         (java.time.Instant/parse
                           (:locked-until credential))
                         (java.time.Instant/now)))
                  (assoc data :error "Account is locked")

                  (not (:valid
                         (hashers/verify
                           password
                           (:password-hash credential))))
                  (let [attempts (inc (:failed-attempts
                                        credential))
                        locked?  (>= attempts 5)
                        locked   (when locked?
                                   (.toString
                                     (.plusSeconds
                                       (java.time.Instant/now)
                                       900)))]
                    (db/update-credential-failed-attempts!
                      db (:id account) attempts locked)
                    (assoc data :error
                      (if locked?
                        "Account is locked"
                        "Invalid credentials")))

                  :else
                  (do
                    (when (pos? (:failed-attempts credential))
                      (db/reset-credential-lockout!
                        db (:id account)))
                    (assoc data
                      :account    account
                      :credential credential)))))
   :schema  {:input  [:map
                       [:email :string]
                       [:password :string]]
              :output {:valid   [:map
                                 [:account :map]
                                 [:credential :map]]
                       :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :auth/create-session [_]
  {:id      :auth/create-session
   :handler (fn [{:keys [db]} {:keys [account] :as data}]
              (let [token      (str (java.util.UUID/randomUUID))
                    expires-at (.toString
                                 (.plusSeconds
                                   (java.time.Instant/now)
                                   86400))
                    session    (db/insert-session!
                                 db
                                 {:account-id (:id account)
                                  :token      token
                                  :expires-at expires-at})]
                (assoc data :session session)))
   :schema  {:input  [:map [:account :map]]
              :output [:map [:session :map]]}})

;; ----------------------------------------------------------
;; Logout cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :auth/revoke-sessions [_]
  {:id      :auth/revoke-sessions
   :handler (fn [{:keys [db]} {:keys [account-id] :as data}]
              (db/revoke-sessions! db account-id)
              (assoc data :logged-out true))
   :schema  {:input  [:map [:account-id :int]]
              :output [:map [:logged-out :boolean]]}})

;; ----------------------------------------------------------
;; Change password cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :auth/validate-change-password [_]
  {:id      :auth/validate-change-password
   :handler (fn [{:keys [db]} {:keys [account-id
                                       current-password
                                       new-password]
                                :as   data}]
              (let [credential (db/find-credential-by-account
                                 db account-id)]
                (cond
                  (nil? credential)
                  (assoc data :error "Credential not found")

                  (not (:valid
                         (hashers/verify
                           current-password
                           (:password-hash credential))))
                  (assoc data
                    :error "Current password is incorrect")

                  (< (count new-password) 8)
                  (assoc data
                    :error
                    "Password must be at least 8 characters")

                  :else
                  (assoc data :credential credential))))
   :schema  {:input  [:map
                       [:account-id :int]
                       [:current-password :string]
                       [:new-password :string]]
              :output {:valid   [:map [:credential :map]]
                       :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :auth/update-password [_]
  {:id      :auth/update-password
   :handler (fn [{:keys [db]} {:keys [account-id
                                       new-password]
                                :as   data}]
              (db/update-credential-password!
                db account-id
                (hashers/derive new-password))
              (assoc data :password-changed true))
   :schema  {:input  [:map
                       [:account-id :int]
                       [:new-password :string]]
              :output [:map
                       [:password-changed :boolean]]}})

;; ----------------------------------------------------------
;; Admin reset password cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :auth/validate-reset-password [_]
  {:id      :auth/validate-reset-password
   :handler (fn [{:keys [db]} {:keys [admin-id account-id
                                       new-password]
                                :as   data}]
              (let [admin   (db/find-account-by-id
                              db admin-id)
                    account (db/find-account-by-id
                              db account-id)]
                (cond
                  (or (nil? admin)
                      (not= "admin" (:role admin)))
                  (assoc data
                    :error "Only admins can reset passwords")

                  (= admin-id account-id)
                  (assoc data
                    :error
                    "Use change password for your own account")

                  (nil? account)
                  (assoc data :error "Account not found")

                  (< (count new-password) 8)
                  (assoc data
                    :error
                    "Password must be at least 8 characters")

                  :else
                  (assoc data :account account))))
   :schema  {:input  [:map
                       [:admin-id :int]
                       [:account-id :int]
                       [:new-password :string]]
              :output {:valid   [:map [:account :map]]
                       :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :auth/reset-password [_]
  {:id      :auth/reset-password
   :handler (fn [{:keys [db]} {:keys [account-id
                                       new-password]
                                :as   data}]
              (db/update-credential-password!
                db account-id
                (hashers/derive new-password))
              (db/revoke-sessions! db account-id)
              (assoc data :password-reset true))
   :schema  {:input  [:map
                       [:account-id :int]
                       [:new-password :string]]
              :output [:map
                       [:password-reset :boolean]]}})
