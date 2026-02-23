(ns {{top/ns}}.handler.auth
  (:require [{{top/ns}}.workflow.auth :as wf]))

(defn- error-response [status result]
  {:status status
   :body   {:error (:error result)}})

(defn- account-id-from-session
  "Extracts the authenticated account id from the request.
   Set by wrap-session middleware."
  [request]
  (get-in request [:session-account :id]))

(defn login-handler
  "POST /api/auth/login"
  [{:keys [ds body-params]}]
  (let [{:keys [email password]} body-params
        result (wf/login! ds email password)]
    (if (:workflow/error result)
      (error-response 401 result)
      {:status 200
       :body   {:token   (get-in result [:session :token])
                :account (:account result)}})))

(defn logout-handler
  "POST /api/auth/logout"
  [{:keys [ds] :as request}]
  (let [aid (account-id-from-session request)]
    (if-not aid
      {:status 401
       :body   {:error "Not authenticated"}}
      (do (wf/logout! ds aid)
          {:status 200
           :body   {:logged-out true}}))))

(defn session-handler
  "GET /api/auth/session — returns current user info."
  [request]
  (if-let [account (:session-account request)]
    {:status 200
     :body   {:account (select-keys
                         account
                         [:id :name :email :role
                          :status])}}
    {:status 401
     :body   {:error "Not authenticated"}}))

(defn change-password-handler
  "POST /api/auth/change-password"
  [{:keys [ds body-params] :as request}]
  (let [aid (account-id-from-session request)]
    (if-not aid
      {:status 401
       :body   {:error "Not authenticated"}}
      (let [{:keys [current-password new-password]}
            body-params
            result (wf/change-password!
                     ds aid current-password new-password)]
        (if (:workflow/error result)
          (error-response 400 result)
          {:status 200
           :body   {:password-changed true}})))))

(defn reset-password-handler
  "POST /api/auth/reset-password"
  [{:keys [ds body-params] :as request}]
  (let [aid (account-id-from-session request)]
    (if-not aid
      {:status 401
       :body   {:error "Not authenticated"}}
      (let [{:keys [account-id new-password]} body-params
            result (wf/reset-password!
                     ds aid account-id new-password)]
        (if (:workflow/error result)
          (error-response 400 result)
          {:status 200
           :body   {:password-reset true}})))))
