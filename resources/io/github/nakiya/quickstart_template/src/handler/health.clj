(ns {{top/ns}}.handler.health
  (:require [{{top/ns}}.db :as db]))

(defn status [{:keys [ds]}]
  {:status 200
   :body   {:status      "ok"
            :initialized (if ds
                           (db/system-initialized? ds)
                           false)}})
