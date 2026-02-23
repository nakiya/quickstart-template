(ns {{top/ns}}.ui.app
  (:require [reagent.dom.client :as rdc]
            [re-frame.core :as rf]
            [{{top/ns}}.ui.events]
            [{{top/ns}}.ui.subs]
            [{{top/ns}}.ui.views :as views]))

(defonce root (atom nil))

(defn ^:dev/after-load render []
  (when-let [rt @root]
    (rdc/render rt [views/root])))

(defn init []
  (rf/dispatch-sync [:initialize])
  (let [el (js/document.getElementById "app")]
    (reset! root (rdc/create-root el))
    (render)))
