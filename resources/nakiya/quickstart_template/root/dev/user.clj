(ns user
  (:require [{{top/ns}}.dev :as dev]))

(defn start
  "Start all dev services (backend + shadow-cljs + test watcher)."
  ([] (dev/start!))
  ([port] (dev/start! {:port port})))

(defn stop
  "Stop all dev services."
  [] (dev/stop!))

(defn restart
  "Restart all dev services."
  ([] (dev/restart!))
  ([port] (dev/restart! {:port port})))
