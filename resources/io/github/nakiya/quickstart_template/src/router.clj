(ns {{top/ns}}.router
  (:require [clojure.java.io :as io]
            [{{top/ns}}.handler.account :as account]
            [{{top/ns}}.handler.auth :as auth]
            [{{top/ns}}.handler.health :as health]
            [{{top/ns}}.workflow.auth :as auth-wf]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters
             :as parameters]))

(defn index-html [_request]
  (if-let [res (io/resource "public/index.html")]
    {:status  200
     :headers {"content-type" "text/html; charset=utf-8"}
     :body    (slurp res)}
    {:status 404
     :body   {:error "index.html not found"}}))

(defn- wrap-ds
  "Middleware that assocs the datasource onto the request."
  [handler ds]
  (fn [request]
    (handler (assoc request :ds ds))))

(defn- wrap-session
  "Middleware that resolves a Bearer token to an account
   and assocs it as :session-account on the request."
  [handler ds]
  (fn [request]
    (let [auth-header (get-in request [:headers "authorization"])
          token       (when (and auth-header
                                 (.startsWith
                                   ^String auth-header
                                   "Bearer "))
                        (subs auth-header 7))
          account     (when token
                        (auth-wf/session->account ds token))]
      (handler (cond-> request
                 account (assoc :session-account account))))))

(defn routes []
  [["/" {:get {:handler index-html
               :no-doc  true}}]
   ["/index" {:get {:handler index-html
                    :no-doc  true}}]
   ["/api"
    ["/health" {:get {:handler health/status}}]
    ["/setup" {:post {:handler account/setup-handler}}]
    ["/auth"
     ["/login"
      {:post {:handler auth/login-handler}}]
     ["/logout"
      {:post {:handler auth/logout-handler}}]
     ["/session"
      {:get {:handler auth/session-handler}}]
     ["/change-password"
      {:post {:handler auth/change-password-handler}}]
     ["/reset-password"
      {:post {:handler auth/reset-password-handler}}]]
    ["/accounts"
     {:get  {:handler account/list-accounts-handler}
      :post {:handler account/create-account-handler}}]
    ["/accounts/:id/disable"
     {:put {:handler account/disable-account-handler}}]
    ["/accounts/:id/enable"
     {:put {:handler account/enable-account-handler}}]]])

(defn app
  ([] (app nil))
  ([ds]
   (ring/ring-handler
     (ring/router
       (routes)
       {:data {:muuntaja   m/instance
               :middleware
               [parameters/parameters-middleware
                muuntaja/format-middleware
                exception/exception-middleware]}})
     (ring/routes
       (ring/create-resource-handler {:path "/"})
       (ring/create-default-handler
         {:not-found
          (constantly {:status 404
                       :body   {:error "not found"}})}))
     {:middleware [[wrap-ds ds]
                   [wrap-session ds]]})))
