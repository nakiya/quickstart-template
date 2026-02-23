(ns {{top/ns}}.dev
  (:require [{{top/ns}}.server :as server]
            [shadow.cljs.devtools.server :as shadow-server]
            [shadow.cljs.devtools.api :as shadow]
            [clojure.java.io :as io]
            [clojure.test :as test]))

(defonce ^:private state (atom {}))

;; -----------------------------------------------------------
;; Test runner
;; -----------------------------------------------------------

(defn- find-test-nses []
  (let [test-dir (io/file "test")]
    (when (.isDirectory test-dir)
      (->> (file-seq test-dir)
           (filter #(and (.isFile %)
                         (.endsWith (.getName %) ".clj")))
           (map (fn [f]
                  (-> (.getPath f)
                      (.replace "test/" "")
                      (.replace "/" ".")
                      (.replace "_" "-")
                      (.replace ".clj" "")
                      symbol)))))))

(defn run-tests []
  (let [nses (find-test-nses)]
    (doseq [ns nses]
      (require ns :reload))
    (apply test/run-tests nses)))

;; -----------------------------------------------------------
;; CSS watcher (Tailwind CLI)
;; -----------------------------------------------------------

(defn- start-css-watcher! []
  (let [out-dir (io/file "resources/public/css")]
    (.mkdirs out-dir)
    (let [proc (-> (ProcessBuilder.
                     ["npx" "@tailwindcss/cli"
                      "-i" "src/app.css"
                      "-o" "resources/public/css/app.css"
                      "--watch"])
                   (.inheritIO)
                   (.start))]
      proc)))

(defn- stop-css-watcher! [^Process proc]
  (when (and proc (.isAlive proc))
    (.destroyForcibly proc)))

;; -----------------------------------------------------------
;; Lifecycle
;; -----------------------------------------------------------

(defn start!
  "Start backend server, CSS watcher, and shadow-cljs watch."
  ([] (start! {}))
  ([{:keys [port] :or {port 3000}}]
   (println "=== {{top/ns}} dev ===")
   (println)

   ;; 1. Backend server (fast)
   (println "[1/3] Starting backend server...")
   (server/start! {:port port :dev? true})
   (println (str "      http://localhost:" port))
   (println)

   ;; 2. Tailwind CSS watcher (background process)
   (println "[2/3] Starting Tailwind CSS watcher...")
   (let [proc (start-css-watcher!)]
     (swap! state assoc :css-watcher proc))
   (println "      Watching src/app.css → resources/public/css/app.css")
   (println)

   ;; 3. Shadow-cljs (slow — runs in background)
   (println "[3/3] Starting shadow-cljs watch (background)...")
   (future
     (try
       (shadow-server/start!)
       (shadow/watch :app)
       (println)
       (println "      shadow-cljs ready — CLJS hot reload active")
       (catch Exception e
         (println "      shadow-cljs failed:" (.getMessage e)))))
   (println)

   (println "=== ready ===")
   (println (str "  Backend:  http://localhost:" port))
   (println "  CSS:      Tailwind watcher running")
   (println "  Frontend: shadow-cljs starting in background...")
   (println)
   (println "  ({{top/ns}}.dev/stop!)    - shut down everything")
   (println "  ({{top/ns}}.dev/restart!) - restart everything")
   :started))

(defn stop!
  "Stop all dev services."
  []
  (println "\nShutting down...")

  ;; CSS watcher
  (when-let [proc (:css-watcher @state)]
    (stop-css-watcher! proc)
    (println "  CSS watcher stopped"))

  ;; Shadow-cljs
  (try
    (shadow/stop-worker :app)
    (shadow-server/stop!)
    (println "  shadow-cljs stopped")
    (catch Exception _
      (println "  shadow-cljs was not running")))

  ;; Backend
  (try
    (server/stop!)
    (println "  Backend stopped")
    (catch Exception _
      (println "  Backend was not running")))

  (reset! state {})
  (println "All stopped.")
  :stopped)

(defn restart!
  "Restart all dev services."
  ([] (restart! {}))
  ([opts]
   (stop!)
   (start! opts)))

;; -----------------------------------------------------------
;; -main entry point for `clojure -M:dev`
;; -----------------------------------------------------------

(defn -main [& args]
  (let [port (or (some-> (first args) parse-long) 3000)]
    (start! {:port port})
    ;; Block the main thread so the process stays alive
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. ^Runnable stop!))
    @(promise)))
