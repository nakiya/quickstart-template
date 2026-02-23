(ns {{top/ns}}.ui.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :page
  (fn [db _] (:page db)))

(rf/reg-sub :loading?
  (fn [db _] (:loading? db)))

(rf/reg-sub :error
  (fn [db _] (:error db)))

(rf/reg-sub :success
  (fn [db _] (:success db)))

(rf/reg-sub :system-ready?
  (fn [db _] (:system-ready? db)))

(rf/reg-sub :authenticated?
  (fn [db _] (:authenticated? db)))

(rf/reg-sub :current-user
  (fn [db _] (:current-user db)))

(rf/reg-sub :login-form
  (fn [db _] (:login-form db)))

(rf/reg-sub :accounts
  (fn [db _] (:accounts db)))

(rf/reg-sub :setup-form
  (fn [db _] (:setup-form db)))

(rf/reg-sub :create-form
  (fn [db _] (:create-form db)))
