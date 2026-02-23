(ns {{top/ns}}.core
  (:require [{{top/ns}}.server :as server]))

(defn -main [& args]
  (let [port (or (some-> (first args) parse-long) 3000)
        dev? (= "dev" (System/getProperty "{{top/ns}}.env"))]
    (server/start! {:port port :dev? dev?})
    (println (str "{{top/ns}} running "
                  (when dev? "(dev mode) ")
                  "at http://localhost:" port))))
