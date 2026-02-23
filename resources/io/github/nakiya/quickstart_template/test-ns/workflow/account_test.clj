(ns {{top/ns}}.workflow.account-test
  (:require [clojure.test :refer [deftest is testing]]
            [{{top/ns}}.db :as db]
            [{{top/ns}}.workflow.account :as wf]))

;; ----- in-memory store -----

(def ^:private state (atom nil))

(defn- reset-state! []
  (reset! state {:initialized  false
                 :accounts     {}
                 :credentials  {}
                 :next-id      1}))

;; ----- db mocks -----

(defn- mock-system-initialized? [_ds]
  (:initialized @state))

(defn- mock-mark-initialized! [_ds]
  (swap! state assoc :initialized true))

(defn- mock-find-account-by-id [_ds id]
  (get-in @state [:accounts id]))

(defn- mock-find-account-by-email [_ds email]
  (->> (:accounts @state)
       vals
       (filter #(= email (:email %)))
       first))

(defn- mock-insert-account! [_ds {:keys [name email role
                                          created-by]}]
  (let [id  (:next-id @state)
        now (.toString (java.time.Instant/now))
        acc {:id         id
             :name       name
             :email      email
             :role       role
             :status     "active"
             :created-at now
             :created-by created-by}]
    (swap! state #(-> %
                      (assoc-in [:accounts id] acc)
                      (update :next-id inc)))
    acc))

(defn- mock-update-account-status! [_ds id status]
  (swap! state assoc-in [:accounts id :status] status)
  (get-in @state [:accounts id]))

(defn- mock-list-accounts [_ds]
  (sort-by :id (vals (:accounts @state))))

(defn- mock-insert-credential! [_ds {:keys [account-id
                                             password-hash]}]
  (swap! state assoc-in [:credentials account-id]
         {:account-id    account-id
          :password-hash password-hash
          :failed-attempts 0
          :locked-until    nil})
  (get-in @state [:credentials account-id]))

(defmacro with-mock-db [& body]
  `(do
     (reset-state!)
     (with-redefs
       [db/system-initialized?    mock-system-initialized?
        db/mark-initialized!      mock-mark-initialized!
        db/find-account-by-id     mock-find-account-by-id
        db/find-account-by-email  mock-find-account-by-email
        db/insert-account!        mock-insert-account!
        db/update-account-status! mock-update-account-status!
        db/list-accounts          mock-list-accounts
        db/insert-credential!     mock-insert-credential!]
       ~@body)))

;; ----- helpers -----

(def ^:private ds :mock-ds)

(defn- setup! []
  (wf/initialize-system! ds "Admin" "admin@test.com"
                          "admin123pw"))

(defn- error? [result]
  (:workflow/error result))

;; ==========================================================
;; InitializeSystem
;; ==========================================================

(deftest initialize-system-success
  (with-mock-db
    (testing "creates admin account when system not initialized"
      (let [result (setup!)]
        (is (not (error? result)))
        (is (= "admin" (get-in result [:account :role])))
        (is (= "Admin" (get-in result [:account :name])))
        (is (= "admin@test.com"
               (get-in result [:account :email])))
        (is (true? (:initialized @state)))
        (is (some? (get-in @state [:credentials 1])))))))

(deftest initialize-system-already-initialized
  (with-mock-db
    (testing "fails when system already initialized"
      (setup!)
      (let [result (wf/initialize-system!
                     ds "Admin2" "admin2@test.com"
                     "admin234pw")]
        (is (error? result))
        (is (= "System already initialized"
               (:error result)))))))

;; ==========================================================
;; CreateAccount
;; ==========================================================

(deftest create-account-success
  (with-mock-db
    (testing "admin creates a cashier account"
      (setup!)
      (let [result (wf/create-account!
                     ds 1 "Cashier" "cashier@test.com"
                     "cashier" "cashier1pw")]
        (is (not (error? result)))
        (is (= "cashier" (get-in result [:account :role])))
        (is (= "active" (get-in result [:account :status])))
        (is (= 1 (get-in result [:account :created-by])))
        (is (some? (get-in @state [:credentials 2])))))))

(deftest create-account-admin-role-rejected
  (with-mock-db
    (testing "cannot create account with admin role"
      (setup!)
      (let [result (wf/create-account!
                     ds 1 "Admin2" "admin2@test.com"
                     "admin" "admin234pw")]
        (is (error? result))
        (is (= "Cannot create admin accounts"
               (:error result)))))))

(deftest create-account-email-taken
  (with-mock-db
    (testing "cannot create account with duplicate email"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (let [result (wf/create-account!
                     ds 1 "Cashier2" "cashier@test.com"
                     "cashier" "cashier2pw")]
        (is (error? result))
        (is (= "Email already taken" (:error result)))))))

(deftest create-account-non-admin-rejected
  (with-mock-db
    (testing "non-admin cannot create accounts"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (let [result (wf/create-account!
                     ds 2 "Other" "other@test.com"
                     "manager" "manager1pw")]
        (is (error? result))
        (is (= "Only admins can create accounts"
               (:error result)))))))

;; ==========================================================
;; DisableAccount
;; ==========================================================

(deftest disable-account-success
  (with-mock-db
    (testing "admin disables an active non-admin account"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (let [result (wf/disable-account! ds 1 2)]
        (is (not (error? result)))
        (is (= "disabled"
               (get-in result [:account :status])))))))

(deftest disable-account-already-disabled
  (with-mock-db
    (testing "cannot disable an already-disabled account"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (wf/disable-account! ds 1 2)
      (let [result (wf/disable-account! ds 1 2)]
        (is (error? result))
        (is (= "Account is not active"
               (:error result)))))))

(deftest disable-self-rejected
  (with-mock-db
    (testing "admin cannot disable their own account"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      ;; admin (id=1) tries to disable themselves
      (let [result (wf/disable-account! ds 1 1)]
        (is (error? result))
        (is (= "Cannot disable your own account"
               (:error result)))))))

(deftest disable-admin-rejected
  (with-mock-db
    (testing "cannot disable admin account"
      (setup!)
      ;; Use mock to create a second admin-role account for
      ;; testing the role guard independently of self-disable
      (swap! state assoc-in [:accounts 99]
             {:id 99 :name "Admin2" :email "admin2@test.com"
              :role "admin" :status "active"
              :created-at "2026-01-01T00:00:00Z"
              :created-by nil})
      (let [result (wf/disable-account! ds 1 99)]
        (is (error? result))
        (is (= "Cannot disable admin accounts"
               (:error result)))))))

;; ==========================================================
;; EnableAccount
;; ==========================================================

(deftest enable-account-success
  (with-mock-db
    (testing "admin enables a disabled account"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (wf/disable-account! ds 1 2)
      (let [result (wf/enable-account! ds 1 2)]
        (is (not (error? result)))
        (is (= "active"
               (get-in result [:account :status])))))))

(deftest enable-account-already-active
  (with-mock-db
    (testing "cannot enable an already-active account"
      (setup!)
      (wf/create-account!
        ds 1 "Cashier" "cashier@test.com"
        "cashier" "cashier1pw")
      (let [result (wf/enable-account! ds 1 2)]
        (is (error? result))
        (is (= "Account is not disabled"
               (:error result)))))))
