(ns {{top/ns}}.ui.events
  (:require [{{top/ns}}.ui.db :as db]
            [re-frame.core :as rf]))

;; ----------------------------------------------------------
;; Helper: auth headers from db
;; ----------------------------------------------------------

(defn- auth-headers [db]
  (when-let [token (:session-token db)]
    {"Authorization" (str "Bearer " token)}))

;; ----------------------------------------------------------
;; HTTP effect handler
;; ----------------------------------------------------------

(rf/reg-fx
  :http
  (fn [{:keys [method url body on-success on-failure
               headers]}]
    (let [opts (cond-> {:method  (name method)
                        :headers (merge
                                   {"Content-Type"
                                    "application/json"
                                    "Accept"
                                    "application/json"}
                                   headers)}
                 body (assoc :body
                        (js/JSON.stringify
                          (clj->js body))))]
      (-> (js/fetch url (clj->js opts))
          (.then (fn [resp]
                   (.then (.json resp)
                          (fn [data]
                            (let [parsed (js->clj data
                                           :keywordize-keys
                                           true)]
                              (if (.-ok resp)
                                (rf/dispatch
                                  (conj on-success parsed))
                                (rf/dispatch
                                  (conj on-failure
                                    parsed))))))))
          (.catch (fn [err]
                    (rf/dispatch
                      (conj on-failure
                        {:error (.-message err)}))))))))

;; ----------------------------------------------------------
;; Navigation
;; ----------------------------------------------------------

(rf/reg-event-fx
  :navigate
  (fn [{:keys [db]} [_ page]]
    {:db (assoc db :page page
           :error nil :success nil)}))

;; ----------------------------------------------------------
;; Clear messages
;; ----------------------------------------------------------

(rf/reg-event-db
  :clear-messages
  (fn [db _]
    (assoc db :error nil :success nil)))

;; ----------------------------------------------------------
;; Initialize app-db
;; ----------------------------------------------------------

(rf/reg-event-fx
  :initialize
  (fn [_ _]
    {:db       db/default-db
     :dispatch [:check-session]}))

;; ----------------------------------------------------------
;; Auth: check existing session on startup
;; ----------------------------------------------------------

(rf/reg-event-fx
  :check-session
  (fn [{:keys [db]} _]
    (let [token (.getItem js/localStorage "session-token")]
      (if token
        {:db   (assoc db :session-token token)
         :http {:method     :get
                :url        "/api/auth/session"
                :headers    {"Authorization"
                             (str "Bearer " token)}
                :on-success [:check-session-success]
                :on-failure [:check-session-failure]}}
        {:dispatch [:check-system-ready]}))))

(rf/reg-event-fx
  :check-session-success
  (fn [{:keys [db]} [_ resp]]
    (let [user (:account resp)]
      {:db       (assoc db
                   :authenticated? true
                   :current-user   user
                   :system-ready?  true
                   :page           :accounts)
       :dispatch [:fetch-accounts]})))

(rf/reg-event-fx
  :check-session-failure
  (fn [{:keys [db]} _]
    (.removeItem js/localStorage "session-token")
    {:db       (assoc db
                 :session-token  nil
                 :authenticated? false
                 :current-user   nil)
     :dispatch [:check-system-ready]}))

(rf/reg-event-fx
  :check-system-ready
  (fn [_ _]
    {:http {:method     :get
            :url        "/api/health"
            :on-success [:check-system-ready-success]
            :on-failure [:check-system-ready-failure]}}))

(rf/reg-event-db
  :check-system-ready-success
  (fn [db [_ resp]]
    (let [ready? (boolean (:initialized resp))]
      (assoc db
        :system-ready? ready?
        :page          (if ready? :login :setup)))))

(rf/reg-event-db
  :check-system-ready-failure
  (fn [db _]
    (assoc db :page :setup :system-ready? false)))

;; ----------------------------------------------------------
;; Auth: login
;; ----------------------------------------------------------

(rf/reg-event-db
  :update-login-form
  (fn [db [_ field value]]
    (assoc-in db [:login-form field] value)))

(rf/reg-event-fx
  :submit-login
  (fn [{:keys [db]} _]
    (let [{:keys [email password]} (:login-form db)]
      {:db   (assoc db :loading? true :error nil)
       :http {:method     :post
              :url        "/api/auth/login"
              :body       {:email email :password password}
              :on-success [:login-success]
              :on-failure [:request-failure]}})))

(rf/reg-event-fx
  :login-success
  (fn [{:keys [db]} [_ resp]]
    (let [token (:token resp)
          user  (:account resp)]
      (.setItem js/localStorage "session-token" token)
      {:db       (assoc db
                   :loading?       false
                   :authenticated? true
                   :session-token  token
                   :current-user   user
                   :system-ready?  true
                   :login-form     {:email "" :password ""}
                   :page           :accounts)
       :dispatch [:fetch-accounts]})))

;; ----------------------------------------------------------
;; Auth: logout
;; ----------------------------------------------------------

(rf/reg-event-fx
  :logout
  (fn [{:keys [db]} _]
    {:db   (assoc db :loading? true)
     :http {:method     :post
            :url        "/api/auth/logout"
            :headers    (auth-headers db)
            :on-success [:logout-success]
            :on-failure [:logout-success]}}))

(rf/reg-event-db
  :logout-success
  (fn [db _]
    (.removeItem js/localStorage "session-token")
    (merge db db/default-db
           {:system-ready? true
            :page          :login})))

;; ----------------------------------------------------------
;; Setup
;; ----------------------------------------------------------

(rf/reg-event-db
  :update-setup-form
  (fn [db [_ field value]]
    (assoc-in db [:setup-form field] value)))

(rf/reg-event-fx
  :submit-setup
  (fn [{:keys [db]} _]
    (let [{:keys [name email password]} (:setup-form db)]
      {:db   (assoc db :loading? true :error nil)
       :http {:method     :post
              :url        "/api/setup"
              :body       {:name     name
                           :email    email
                           :password password}
              :on-success [:setup-success]
              :on-failure [:request-failure]}})))

(rf/reg-event-fx
  :setup-success
  (fn [{:keys [db]} [_ _resp]]
    {:db       (assoc db
                 :loading?      false
                 :system-ready? true
                 :setup-form    {:name "" :email ""
                                 :password ""}
                 :success       "System initialized"
                 :page          :login)
     :dispatch-n []}))

;; ----------------------------------------------------------
;; Fetch accounts
;; ----------------------------------------------------------

(rf/reg-event-fx
  :fetch-accounts
  (fn [{:keys [db]} _]
    {:db   (assoc db :loading? true)
     :http {:method     :get
            :url        "/api/accounts"
            :headers    (auth-headers db)
            :on-success [:fetch-accounts-success]
            :on-failure [:request-failure]}}))

(rf/reg-event-db
  :fetch-accounts-success
  (fn [db [_ resp]]
    (let [accounts (:accounts resp)]
      (assoc db
        :loading?      false
        :accounts      accounts
        :system-ready? (boolean (seq accounts))))))

;; ----------------------------------------------------------
;; Create account
;; ----------------------------------------------------------

(rf/reg-event-db
  :update-create-form
  (fn [db [_ field value]]
    (assoc-in db [:create-form field] value)))

(rf/reg-event-fx
  :submit-create-account
  (fn [{:keys [db]} _]
    (let [{:keys [name email role password]}
          (:create-form db)]
      {:db   (assoc db :loading? true :error nil)
       :http {:method     :post
              :url        "/api/accounts"
              :body       {:name     name
                           :email    email
                           :role     role
                           :password password}
              :headers    (auth-headers db)
              :on-success [:create-account-success]
              :on-failure [:request-failure]}})))

(rf/reg-event-fx
  :create-account-success
  (fn [{:keys [db]} [_ resp]]
    {:db       (assoc db
                 :loading?    false
                 :create-form {:name     ""
                               :email    ""
                               :role     "cashier"
                               :password ""}
                 :success     "Account created")
     :dispatch [:fetch-accounts]}))

;; ----------------------------------------------------------
;; Disable / Enable account
;; ----------------------------------------------------------

(rf/reg-event-fx
  :disable-account
  (fn [{:keys [db]} [_ id]]
    {:db   (assoc db :loading? true :error nil)
     :http {:method     :put
            :url        (str "/api/accounts/" id "/disable")
            :on-success [:toggle-account-success
                         "Account disabled"]
            :on-failure [:request-failure]
            :headers    (auth-headers db)}}))

(rf/reg-event-fx
  :enable-account
  (fn [{:keys [db]} [_ id]]
    {:db   (assoc db :loading? true :error nil)
     :http {:method     :put
            :url        (str "/api/accounts/" id "/enable")
            :on-success [:toggle-account-success
                         "Account enabled"]
            :on-failure [:request-failure]
            :headers    (auth-headers db)}}))

(rf/reg-event-fx
  :toggle-account-success
  (fn [{:keys [db]} [_ message _resp]]
    {:db       (assoc db :loading? false :success message)
     :dispatch [:fetch-accounts]}))

;; ----------------------------------------------------------
;; Generic failure
;; ----------------------------------------------------------

(rf/reg-event-db
  :request-failure
  (fn [db [_ resp]]
    (assoc db
      :loading? false
      :error    (or (:error resp) "Request failed"))))
