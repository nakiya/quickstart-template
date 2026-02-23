(ns {{top/ns}}.handler.auth-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing
                                  use-fixtures]]
            [{{top/ns}}.db :as db]
            [{{top/ns}}.handler.account :as account]
            [{{top/ns}}.handler.auth :as handler]))

;; ----- temp-file SQLite fixture -----

(def ds-atom (atom nil))

(defn- tmp-db-fixture [f]
  (let [tmp  (java.io.File/createTempFile
               "{{top/ns}}-auth-test" ".db")
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

;; ----- helpers -----

(defn- admin-account []
  (db/find-account-by-id @ds-atom 1))

(defn- setup! []
  (account/setup-handler
    {:ds          @ds-atom
     :body-params {:name     "Admin"
                   :email    "admin@test.com"
                   :password "admin123pw"}}))

(defn- login! [email password]
  (handler/login-handler
    {:ds          @ds-atom
     :body-params {:email email :password password}}))

(defn- login-token! [email password]
  (get-in (login! email password) [:body :token]))

;; ==========================================================
;; POST /api/auth/login
;; ==========================================================

(deftest login-success
  (testing "correct credentials return 200 with token"
    (setup!)
    (let [resp (login! "admin@test.com" "admin123pw")]
      (is (= 200 (:status resp)))
      (is (some? (get-in resp [:body :token])))
      (is (= "admin"
             (get-in resp [:body :account :role]))))))

(deftest login-wrong-password
  (testing "wrong password returns 401"
    (setup!)
    (let [resp (login! "admin@test.com" "wrongpass")]
      (is (= 401 (:status resp))))))

(deftest login-unknown-email
  (testing "unknown email returns 401"
    (setup!)
    (let [resp (login! "nobody@test.com" "admin123pw")]
      (is (= 401 (:status resp))))))

(deftest login-lockout-after-failures
  (testing "account locks after 5 failed attempts"
    (setup!)
    (dotimes [_ 5]
      (login! "admin@test.com" "wrongpass"))
    (let [resp (login! "admin@test.com" "admin123pw")]
      (is (= 401 (:status resp)))
      (is (= "Account is locked"
             (get-in resp [:body :error]))))))

;; ==========================================================
;; GET /api/auth/session
;; ==========================================================

(deftest session-success
  (testing "valid token returns account info"
    (setup!)
    (let [token (login-token! "admin@test.com" "admin123pw")
          resp  (handler/session-handler
                  {:session-account (admin-account)})]
      (is (= 200 (:status resp)))
      (is (= "admin@test.com"
             (get-in resp [:body :account :email]))))))

(deftest session-no-auth
  (testing "no session returns 401"
    (let [resp (handler/session-handler {})]
      (is (= 401 (:status resp))))))

;; ==========================================================
;; POST /api/auth/logout
;; ==========================================================

(deftest logout-success
  (testing "logout revokes sessions"
    (setup!)
    (let [token (login-token! "admin@test.com" "admin123pw")
          resp  (handler/logout-handler
                  {:ds              @ds-atom
                   :session-account (admin-account)})]
      (is (= 200 (:status resp)))
      (is (true? (get-in resp [:body :logged-out])))
      ;; token should be invalid now
      (is (nil? (db/find-session-by-token @ds-atom token))))))

(deftest logout-no-auth
  (testing "logout without session returns 401"
    (let [resp (handler/logout-handler
                 {:ds @ds-atom})]
      (is (= 401 (:status resp))))))

;; ==========================================================
;; POST /api/auth/change-password
;; ==========================================================

(deftest change-password-success
  (testing "changes password with correct current password"
    (setup!)
    (let [resp (handler/change-password-handler
                 {:ds              @ds-atom
                  :body-params     {:current-password
                                    "admin123pw"
                                    :new-password
                                    "newpass123"}
                  :session-account (admin-account)})]
      (is (= 200 (:status resp)))
      ;; can login with new password
      (let [login-resp (login! "admin@test.com" "newpass123")]
        (is (= 200 (:status login-resp)))))))

(deftest change-password-wrong-current
  (testing "rejects wrong current password"
    (setup!)
    (let [resp (handler/change-password-handler
                 {:ds              @ds-atom
                  :body-params     {:current-password "wrong"
                                    :new-password "newpass123"}
                  :session-account (admin-account)})]
      (is (= 400 (:status resp))))))

(deftest change-password-too-short
  (testing "rejects password shorter than 8 chars"
    (setup!)
    (let [resp (handler/change-password-handler
                 {:ds              @ds-atom
                  :body-params     {:current-password
                                    "admin123pw"
                                    :new-password "short"}
                  :session-account (admin-account)})]
      (is (= 400 (:status resp))))))

;; ==========================================================
;; POST /api/auth/reset-password
;; ==========================================================

(deftest reset-password-success
  (testing "admin resets another account's password"
    (setup!)
    (account/create-account-handler
      {:ds              @ds-atom
       :body-params     {:name     "Cashier"
                         :email    "cash@test.com"
                         :role     "cashier"
                         :password "cashier1pw"}
       :session-account (admin-account)})
    (let [resp (handler/reset-password-handler
                 {:ds              @ds-atom
                  :body-params     {:account-id   2
                                    :new-password "reset1234"}
                  :session-account (admin-account)})]
      (is (= 200 (:status resp)))
      ;; cashier can login with new password
      (let [login-resp (login! "cash@test.com" "reset1234")]
        (is (= 200 (:status login-resp)))))))

(deftest reset-password-non-admin-rejected
  (testing "non-admin cannot reset passwords"
    (setup!)
    (account/create-account-handler
      {:ds              @ds-atom
       :body-params     {:name     "Cashier"
                         :email    "cash@test.com"
                         :role     "cashier"
                         :password "cashier1pw"}
       :session-account (admin-account)})
    (let [cashier (db/find-account-by-id @ds-atom 2)
          resp    (handler/reset-password-handler
                    {:ds              @ds-atom
                     :body-params     {:account-id   1
                                       :new-password "hackit12"}
                     :session-account cashier})]
      (is (= 400 (:status resp))))))
