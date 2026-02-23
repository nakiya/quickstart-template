(ns {{top/ns}}.e2e-server
  (:require [clojure.java.io :as io]
            [{{top/ns}}.server :as server]))

(defn -main [& args]
  (let [port   (or (some-> (first args) parse-long) 3001)
        dbfile (or (second args) "{{top/ns}}-e2e.db")]
    (io/delete-file dbfile true)
    (println (str "E2E server starting on port " port
               " with db " dbfile))
    (server/start! {:port port :dbfile dbfile})
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. ^Runnable server/stop!))
    @(promise)))
