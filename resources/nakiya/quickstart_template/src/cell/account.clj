(ns {{top/ns}}.cell.account
  (:require [buddy.hashers :as hashers]
            [mycelium.cell :as cell]
            [{{top/ns}}.db :as db]))

;; ----------------------------------------------------------
;; Setup workflow cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :account/validate-setup [_]
  {:id      :account/validate-setup
   :handler (fn [{:keys [db]} data]
              (if (db/system-initialized? db)
                (assoc data :error "System already initialized")
                (assoc data :can-setup true)))
   :schema  {:input  [:map
                      [:name :string]
                      [:email :string]
                      [:password :string]]
             :output {:valid   [:map
                                [:name :string]
                                [:email :string]
                                [:password :string]
                                [:can-setup :boolean]]
                      :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :account/initialize-system [_]
  {:id      :account/initialize-system
   :handler (fn [{:keys [db]} {:keys [name email password]
                                :as data}]
              (let [account (db/insert-account!
                              db
                              {:name       name
                               :email      email
                               :role       "admin"
                               :created-by nil})
                    _       (db/mark-initialized! db)
                    _       (db/insert-credential!
                              db
                              {:account-id    (:id account)
                               :password-hash
                               (hashers/derive password)})]
                (assoc data :account account)))
   :schema  {:input  [:map
                      [:name :string]
                      [:email :string]
                      [:password :string]]
             :output [:map [:account :map]]}})

;; ----------------------------------------------------------
;; Create account workflow cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :account/validate-create [_]
  {:id      :account/validate-create
   :handler (fn [{:keys [db]} {:keys [admin-id role email]
                                :as   data}]
              (let [admin (db/find-account-by-id db admin-id)]
                (cond
                  (nil? admin)
                  (assoc data
                    :error "Admin account not found")

                  (not= "admin" (:role admin))
                  (assoc data
                    :error "Only admins can create accounts")

                  (= "admin" role)
                  (assoc data
                    :error "Cannot create admin accounts")

                  (db/find-account-by-email db email)
                  (assoc data
                    :error "Email already taken")

                  :else
                  (assoc data :admin admin))))
   :schema  {:input  [:map
                      [:admin-id :int]
                      [:name :string]
                      [:email :string]
                      [:role :string]
                      [:password :string]]
             :output {:valid   [:map
                                [:admin :map]
                                [:name :string]
                                [:email :string]
                                [:role :string]
                                [:password :string]]
                      :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :account/create-account [_]
  {:id      :account/create-account
   :handler (fn [{:keys [db]} {:keys [admin name email role
                                       password]
                                :as   data}]
              (let [account (db/insert-account!
                              db
                              {:name       name
                               :email      email
                               :role       role
                               :created-by (:id admin)})
                    _       (db/insert-credential!
                              db
                              {:account-id    (:id account)
                               :password-hash
                               (hashers/derive password)})]
                (assoc data :account account)))
   :schema  {:input  [:map
                      [:admin :map]
                      [:name :string]
                      [:email :string]
                      [:role :string]
                      [:password :string]]
             :output [:map [:account :map]]}})

;; ----------------------------------------------------------
;; Disable account workflow cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :account/validate-disable [_]
  {:id      :account/validate-disable
   :handler (fn [{:keys [db]} {:keys [admin-id account-id]
                                :as   data}]
              (let [admin   (db/find-account-by-id db admin-id)
                    account (db/find-account-by-id
                              db account-id)]
                (cond
                  (or (nil? admin)
                      (not= "admin" (:role admin)))
                  (assoc data
                    :error "Only admins can disable accounts")

                  (nil? account)
                  (assoc data
                    :error "Account not found")

                  (not= "active" (:status account))
                  (assoc data
                    :error "Account is not active")

                  (= admin-id account-id)
                  (assoc data
                    :error "Cannot disable your own account")

                  (= "admin" (:role account))
                  (assoc data
                    :error "Cannot disable admin accounts")

                  :else
                  (assoc data :account account))))
   :schema  {:input  [:map
                      [:admin-id :int]
                      [:account-id :int]]
             :output {:valid   [:map [:account :map]]
                      :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :account/disable-account [_]
  {:id      :account/disable-account
   :handler (fn [{:keys [db]} {:keys [account] :as data}]
              (let [updated (db/update-account-status!
                              db (:id account) "disabled")]
                (assoc data :account updated)))
   :schema  {:input  [:map [:account :map]]
             :output [:map [:account :map]]}})

;; ----------------------------------------------------------
;; Enable account workflow cells
;; ----------------------------------------------------------

(defmethod cell/cell-spec :account/validate-enable [_]
  {:id      :account/validate-enable
   :handler (fn [{:keys [db]} {:keys [admin-id account-id]
                                :as   data}]
              (let [admin   (db/find-account-by-id db admin-id)
                    account (db/find-account-by-id
                              db account-id)]
                (cond
                  (or (nil? admin)
                      (not= "admin" (:role admin)))
                  (assoc data
                    :error "Only admins can enable accounts")

                  (nil? account)
                  (assoc data
                    :error "Account not found")

                  (not= "disabled" (:status account))
                  (assoc data
                    :error "Account is not disabled")

                  :else
                  (assoc data :account account))))
   :schema  {:input  [:map
                      [:admin-id :int]
                      [:account-id :int]]
             :output {:valid   [:map [:account :map]]
                      :invalid [:map [:error :string]]}}})

(defmethod cell/cell-spec :account/enable-account [_]
  {:id      :account/enable-account
   :handler (fn [{:keys [db]} {:keys [account] :as data}]
              (let [updated (db/update-account-status!
                              db (:id account) "active")]
                (assoc data :account updated)))
   :schema  {:input  [:map [:account :map]]
             :output [:map [:account :map]]}})
