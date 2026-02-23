(ns {{top/ns}}.handler.account-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing
                                  use-fixtures]]
            [{{top/ns}}.db :as db]
            [{{top/ns}}.handler.account :as handler]))

;; ----- temp-file SQLite fixture -----

(def ds-atom (atom nil))

(defn- tmp-db-fixture [f]
  (let [tmp  (java.io.File/createTempFile "{{top/ns}}-test" ".db")
        path (.getAbsolutePath tmp)
        ds   (db/datasource path)]
    (try
      (db/migrate! ds)
      (reset! ds-atom ds)
      (f)
      (finally
        (reset! ds-atom nil)
        (io/delete-file tmp true)))))

(use-fixtures :each tmp-db-fixture)

;; ----- request helpers -----

(defn- admin-account []
  (db/find-account-by-id @ds-atom 1))

(defn- req
  "Builds a minimal Ring request map."
  ([body]
   {:ds @ds-atom :body-params body})
  ([body session-account]
   {:ds              @ds-atom
    :body-params     body
    :session-account session-account})
  ([body session-account path-params]
   {:ds              @ds-atom
    :body-params     body
    :session-account session-account
    :path-params     path-params}))

(defn- setup! []
  (handler/setup-handler
    (req {:name     "Admin"
          :email    "admin@test.com"
          :password "admin123pw"})))

;; ==========================================================
;; POST /api/setup
;; ==========================================================

(deftest setup-creates-admin
  (testing "returns 201 with admin account"
    (let [resp (setup!)]
      (is (= 201 (:status resp)))
      (is (= "admin"
             (get-in resp [:body :account :role])))
      (is (= "admin@test.com"
             (get-in resp [:body :account :email]))))))

(deftest setup-twice-returns-409
  (testing "second setup returns 409"
    (setup!)
    (let [resp (handler/setup-handler
                 (req {:name     "Admin2"
                       :email    "admin2@test.com"
                       :password "admin234pw"}))]
      (is (= 409 (:status resp))))))

;; ==========================================================
;; POST /api/accounts
;; ==========================================================

(deftest create-account-success
  (testing "admin creates cashier, returns 201"
    (setup!)
    (let [resp (handler/create-account-handler
                 (req {:name     "Cashier"
                       :email    "cash@test.com"
                       :role     "cashier"
                       :password "cashier1pw"}
                      (admin-account)))]
      (is (= 201 (:status resp)))
      (is (= "cashier"
             (get-in resp [:body :account :role]))))))

(deftest create-account-not-authenticated
  (testing "returns 401 without session"
    (setup!)
    (let [resp (handler/create-account-handler
                 (req {:name     "Cashier"
                       :email    "cash@test.com"
                       :role     "cashier"
                       :password "cashier1pw"}))]
      (is (= 401 (:status resp))))))

(deftest create-account-admin-role-rejected
  (testing "cannot create another admin"
    (setup!)
    (let [resp (handler/create-account-handler
                 (req {:name     "Admin2"
                       :email    "admin2@test.com"
                       :role     "admin"
                       :password "admin234pw"}
                      (admin-account)))]
      (is (= 400 (:status resp))))))

(deftest create-account-duplicate-email
  (testing "duplicate email returns 400"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "cash@test.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (let [resp (handler/create-account-handler
                 (req {:name     "Cashier2"
                       :email    "cash@test.com"
                       :role     "manager"
                       :password "manager1pw"}
                      (admin-account)))]
      (is (= 400 (:status resp))))))

;; ==========================================================
;; GET /api/accounts
;; ==========================================================

(deftest list-accounts-returns-all
  (testing "lists admin + created accounts"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "c@t.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (let [resp (handler/list-accounts-handler
                 (req {} (admin-account)))]
      (is (= 200 (:status resp)))
      (is (= 2 (count
                  (get-in resp [:body :accounts])))))))

(deftest list-accounts-not-authenticated
  (testing "returns 401 without session"
    (setup!)
    (let [resp (handler/list-accounts-handler
                 {:ds @ds-atom})]
      (is (= 401 (:status resp))))))

(deftest list-accounts-non-admin-rejected
  (testing "cashier cannot list accounts"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "c@t.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (let [cashier (db/find-account-by-id @ds-atom 2)
          resp    (handler/list-accounts-handler
                    (req {} cashier))]
      (is (= 403 (:status resp))))))

;; ==========================================================
;; PUT /api/accounts/:id/disable
;; ==========================================================

(deftest disable-account-success
  (testing "admin disables cashier, returns 200"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "c@t.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (let [resp (handler/disable-account-handler
                 (req {} (admin-account) {:id "2"}))]
      (is (= 200 (:status resp)))
      (is (= "disabled"
             (get-in resp [:body :account :status]))))))

(deftest disable-self-rejected
  (testing "admin cannot disable their own account"
    (setup!)
    (let [resp (handler/disable-account-handler
                 (req {} (admin-account) {:id "1"}))]
      (is (= 400 (:status resp)))
      (is (= "Cannot disable your own account"
             (get-in resp [:body :error]))))))

;; ==========================================================
;; PUT /api/accounts/:id/enable
;; ==========================================================

(deftest enable-account-success
  (testing "admin re-enables disabled account"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "c@t.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (handler/disable-account-handler
      (req {} (admin-account) {:id "2"}))
    (let [resp (handler/enable-account-handler
                 (req {} (admin-account) {:id "2"}))]
      (is (= 200 (:status resp)))
      (is (= "active"
             (get-in resp [:body :account :status]))))))

(deftest enable-already-active-returns-400
  (testing "enabling active account returns 400"
    (setup!)
    (handler/create-account-handler
      (req {:name     "Cashier"
            :email    "c@t.com"
            :role     "cashier"
            :password "cashier1pw"}
           (admin-account)))
    (let [resp (handler/enable-account-handler
                 (req {} (admin-account) {:id "2"}))]
      (is (= 400 (:status resp))))))
