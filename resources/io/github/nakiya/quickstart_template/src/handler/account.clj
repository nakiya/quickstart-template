(ns {{top/ns}}.handler.account
  (:require [{{top/ns}}.workflow.account :as wf]))

(defn- error-response
  "Builds an error HTTP response from a workflow result."
  [status result]
  {:status status
   :body   {:error (:error result)}})

(defn- session-account
  "Returns the authenticated account from the request.
   Set by wrap-session middleware."
  [request]
  (:session-account request))

(defn setup-handler
  "POST /api/setup — one-time system initialization."
  [{:keys [ds body-params]}]
  (let [{:keys [name email password]} body-params
        result (wf/initialize-system!
                 ds name email password)]
    (if (:workflow/error result)
      (error-response 409 result)
      {:status 201
       :body   {:account (:account result)}})))

(defn create-account-handler
  "POST /api/accounts — admin creates a new account."
  [{:keys [ds body-params] :as request}]
  (let [acct (session-account request)]
    (if-not acct
      {:status 401
       :body   {:error "Not authenticated"}}
      (if (not= "admin" (:role acct))
        {:status 403
         :body   {:error "Forbidden"}}
        (let [{:keys [name email role password]} body-params
              result (wf/create-account!
                       ds (:id acct) name email role password)]
          (if (:workflow/error result)
            (error-response 400 result)
            {:status 201
             :body   {:account (:account result)}}))))))

(defn list-accounts-handler
  "GET /api/accounts — list all accounts (admin only)."
  [request]
  (let [acct (session-account request)]
    (if-not acct
      {:status 401
       :body   {:error "Not authenticated"}}
      (if (not= "admin" (:role acct))
        {:status 403
         :body   {:error "Forbidden"}}
        {:status 200
         :body   {:accounts (wf/list-accounts (:ds request))}}))))

(defn disable-account-handler
  "PUT /api/accounts/:id/disable"
  [{:keys [ds path-params] :as request}]
  (let [acct       (session-account request)
        account-id (parse-long (:id path-params))]
    (if-not acct
      {:status 401
       :body   {:error "Not authenticated"}}
      (if (not= "admin" (:role acct))
        {:status 403
         :body   {:error "Forbidden"}}
        (let [result (wf/disable-account!
                       ds (:id acct) account-id)]
          (if (:workflow/error result)
            (error-response 400 result)
            {:status 200
             :body   {:account (:account result)}}))))))

(defn enable-account-handler
  "PUT /api/accounts/:id/enable"
  [{:keys [ds path-params] :as request}]
  (let [acct       (session-account request)
        account-id (parse-long (:id path-params))]
    (if-not acct
      {:status 401
       :body   {:error "Not authenticated"}}
      (if (not= "admin" (:role acct))
        {:status 403
         :body   {:error "Forbidden"}}
        (let [result (wf/enable-account!
                       ds (:id acct) account-id)]
          (if (:workflow/error result)
            (error-response 400 result)
            {:status 200
             :body   {:account (:account result)}}))))))
