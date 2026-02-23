(ns {{top/ns}}.ui.views
  (:require [re-frame.core :as rf]))

;; ----------------------------------------------------------
;; Shared components
;; ----------------------------------------------------------

(defn messages []
  (let [error   @(rf/subscribe [:error])
        success @(rf/subscribe [:success])]
    [:div
     (when error
       [:div.alert.alert-error
        {:role "alert"}
        [:span error]
        [:button.btn.btn-ghost.btn-xs
         {:on-click #(rf/dispatch [:clear-messages])}
         "\u00d7"]])
     (when success
       [:div.alert.alert-success
        {:role "alert"}
        [:span success]
        [:button.btn.btn-ghost.btn-xs
         {:on-click #(rf/dispatch [:clear-messages])}
         "\u00d7"]])]))

(defn nav []
  (let [page   @(rf/subscribe [:page])
        ready? @(rf/subscribe [:system-ready?])
        auth?  @(rf/subscribe [:authenticated?])
        user   @(rf/subscribe [:current-user])]
    [:nav.navbar.bg-base-300
     [:div.navbar-start
      [:span.text-xl.font-bold.mr-8 "POS1"]
      [:div.flex.gap-1
       (when-not ready?
         [:button.btn.btn-ghost.btn-sm.nav-btn
          {:class    (when (= page :setup) "btn-active")
           :on-click #(rf/dispatch [:navigate :setup])}
          "Setup"])
       (when (and ready? auth? (= "admin" (:role user)))
         [:button.btn.btn-ghost.btn-sm.nav-btn
          {:class    (when (= page :accounts) "btn-active")
           :on-click #(rf/dispatch [:navigate :accounts])}
          "Accounts"])]]
     (when auth?
       [:div.navbar-end.flex.items-center.gap-3
        [:span.text-sm.opacity-70.subtitle (:name user)]
        [:button.btn.btn-sm.btn-outline
         {:on-click #(rf/dispatch [:logout])}
         "Logout"]])]))

;; ----------------------------------------------------------
;; Setup page
;; ----------------------------------------------------------

(defn setup-page []
  (let [form     @(rf/subscribe [:setup-form])
        loading? @(rf/subscribe [:loading?])]
    [:div.page
     [:h2.text-2xl.font-bold.mb-2 "System Setup"]
     [:p.text-sm.opacity-60.mb-6 "Create the initial admin account"]
     [:form.max-w-sm
      {:on-submit (fn [e]
                    (.preventDefault e)
                    (rf/dispatch [:submit-setup]))}
      [:fieldset.fieldset
       [:label.fieldset-label "Name"]
       [:input.input.input-bordered.w-full
        {:type      "text"
         :value     (:name form)
         :on-change #(rf/dispatch
                       [:update-setup-form
                        :name
                        (.. % -target -value)])
         :required  true}]]
      [:fieldset.fieldset
       [:label.fieldset-label "Email"]
       [:input.input.input-bordered.w-full
        {:type      "email"
         :value     (:email form)
         :on-change #(rf/dispatch
                       [:update-setup-form
                        :email
                        (.. % -target -value)])
         :required  true}]]
      [:fieldset.fieldset
       [:label.fieldset-label "Password"]
       [:input.input.input-bordered.w-full
        {:type      "password"
         :value     (:password form)
         :on-change #(rf/dispatch
                       [:update-setup-form
                        :password
                        (.. % -target -value)])
         :min-length 8
         :required  true}]]
      [:div.mt-4
       [:button.btn.btn-primary
        {:type     "submit"
         :disabled loading?}
        (if loading? "Initializing..." "Initialize System")]]]]))

;; ----------------------------------------------------------
;; Login page
;; ----------------------------------------------------------

(defn login-page []
  (let [form     @(rf/subscribe [:login-form])
        loading? @(rf/subscribe [:loading?])]
    [:div.page
     [:h2.text-2xl.font-bold.mb-2 "Login"]
     [:p.text-sm.opacity-60.mb-6 "Sign in to your account"]
     [:form.max-w-sm
      {:on-submit (fn [e]
                    (.preventDefault e)
                    (rf/dispatch [:submit-login]))}
      [:fieldset.fieldset
       [:label.fieldset-label "Email"]
       [:input.input.input-bordered.w-full
        {:type      "email"
         :value     (:email form)
         :on-change #(rf/dispatch
                       [:update-login-form
                        :email
                        (.. % -target -value)])
         :required  true}]]
      [:fieldset.fieldset
       [:label.fieldset-label "Password"]
       [:input.input.input-bordered.w-full
        {:type      "password"
         :value     (:password form)
         :on-change #(rf/dispatch
                       [:update-login-form
                        :password
                        (.. % -target -value)])
         :required  true}]]
      [:div.mt-4
       [:button.btn.btn-primary
        {:type     "submit"
         :disabled loading?}
        (if loading? "Signing in..." "Sign In")]]]]))

;; ----------------------------------------------------------
;; Account list page
;; ----------------------------------------------------------

(defn account-row [{:keys [id name email role status]}]
  (let [active? (= status "active")
        admin?  (= role "admin")]
    [:tr {:key id}
     [:td id]
     [:td name]
     [:td email]
     [:td.role.capitalize role]
     [:td [:span.badge
           {:class (if active?
                     "badge-success"
                     "badge-error")}
           status]]
     [:td
      (when-not admin?
        (if active?
          [:button.btn.btn-error.btn-sm
           {:on-click #(rf/dispatch
                         [:disable-account id])}
           "Disable"]
          [:button.btn.btn-success.btn-sm
           {:on-click #(rf/dispatch
                         [:enable-account id])}
           "Enable"]))]]))

(defn accounts-page []
  (let [accounts @(rf/subscribe [:accounts])
        loading? @(rf/subscribe [:loading?])]
    [:div.page
     [:div.flex.items-center.justify-between.mb-4
      [:h2.text-2xl.font-bold "Accounts"]
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [:navigate :create])}
       "New Account"]]
     (if (and loading? (empty? accounts))
       [:p "Loading..."]
       [:table.table.table-zebra
        [:thead
         [:tr
          [:th "ID"]
          [:th "Name"]
          [:th "Email"]
          [:th "Role"]
          [:th "Status"]
          [:th "Actions"]]]
        [:tbody
         (for [acct accounts]
           ^{:key (:id acct)}
           [account-row acct])]])]))

;; ----------------------------------------------------------
;; Create account page
;; ----------------------------------------------------------

(defn create-account-page []
  (let [form     @(rf/subscribe [:create-form])
        loading? @(rf/subscribe [:loading?])]
    [:div.page
     [:h2.text-2xl.font-bold.mb-4 "Create Account"]
     [:form.max-w-sm
      {:on-submit (fn [e]
                    (.preventDefault e)
                    (rf/dispatch [:submit-create-account]))}
      [:fieldset.fieldset
       [:label.fieldset-label "Name"]
       [:input.input.input-bordered.w-full
        {:type      "text"
         :value     (:name form)
         :on-change #(rf/dispatch
                       [:update-create-form
                        :name
                        (.. % -target -value)])
         :required  true}]]
      [:fieldset.fieldset
       [:label.fieldset-label "Email"]
       [:input.input.input-bordered.w-full
        {:type      "email"
         :value     (:email form)
         :on-change #(rf/dispatch
                       [:update-create-form
                        :email
                        (.. % -target -value)])
         :required  true}]]
      [:fieldset.fieldset
       [:label.fieldset-label "Role"]
       [:select.select.select-bordered.w-full
        {:value     (:role form)
         :on-change #(rf/dispatch
                       [:update-create-form
                        :role
                        (.. % -target -value)])}
        [:option {:value "cashier"} "Cashier"]
        [:option {:value "manager"} "Manager"]
        [:option {:value "inventory_clerk"}
         "Inventory Clerk"]
        [:option {:value "accountant"} "Accountant"]]]
      [:fieldset.fieldset
       [:label.fieldset-label "Password"]
       [:input.input.input-bordered.w-full
        {:type      "password"
         :value     (:password form)
         :on-change #(rf/dispatch
                       [:update-create-form
                        :password
                        (.. % -target -value)])
         :min-length 8
         :required  true}]]
      [:div.mt-4
       [:button.btn.btn-primary
        {:type     "submit"
         :disabled loading?}
        (if loading? "Creating..." "Create Account")]]]]))

;; ----------------------------------------------------------
;; Root
;; ----------------------------------------------------------

(defn root []
  (let [page @(rf/subscribe [:page])]
    [:div#root.min-h-screen.bg-base-200
     [nav]
     [messages]
     [:main.max-w-4xl.mx-auto.p-8
      (case page
        :setup    [setup-page]
        :login    [login-page]
        :accounts [accounts-page]
        :create   [create-account-page]
        [login-page])]]))
