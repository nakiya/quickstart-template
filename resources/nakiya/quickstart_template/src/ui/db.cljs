(ns {{top/ns}}.ui.db)

(def default-db
  {:page           :login
   :loading?       false
   :error          nil
   :success        nil
   :system-ready?  false
   :authenticated? false
   :session-token  nil
   :current-user   nil
   :accounts       []
   :login-form     {:email "" :password ""}
   :setup-form     {:name "" :email "" :password ""}
   :create-form    {:name "" :email "" :role "cashier"
                    :password ""}})
