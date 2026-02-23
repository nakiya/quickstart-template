(ns {{top/ns}}.server
  (:require [{{top/ns}}.db :as db]
            [{{top/ns}}.router :as router]
            [ring.adapter.jetty :as jetty]))

(defonce ^:private server (atom nil))

(defn- resolve-wrap-reload []
  (try
    (require 'ring.middleware.reload)
    (resolve 'ring.middleware.reload/wrap-reload)
    (catch Exception _ nil)))

(defn start!
  [{:keys [port dev? ds dbfile]
    :or   {port 3000 dev? false dbfile "{{top/ns}}.db"}}]
  (when @server
    (throw (ex-info "Server already running" {:port port})))
  (let [ds      (or ds (db/datasource dbfile))
        _       (db/migrate! ds)
        handler (cond-> (router/app ds)
                  dev?
                  (as-> h
                    (if-let [wrap (resolve-wrap-reload)]
                      (do (println "Hot reload enabled")
                          (wrap h {:dirs ["src"]}))
                      (do (println
                            (str "ring-devel not on classpath;"
                                 " hot reload disabled"))
                          h))))
        srv     (jetty/run-jetty handler
                  {:port  port
                   :join? false})]
    (println (str "Server started on port " port))
    (reset! server srv)
    srv))

(defn stop! []
  (when-let [srv @server]
    (.stop srv)
    (reset! server nil)
    (println "Server stopped")))
