(ns {{top/ns}}.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [{{top/ns}}.router :as router]))

(deftest app-returns-handler-test
  (testing "app creates a valid ring handler"
    (is (fn? (router/app)))))

(deftest health-endpoint-test
  (testing "GET /api/health returns 200 with status ok"
    (let [handler  (router/app)
          response (handler {:request-method :get
                             :uri            "/api/health"
                             :headers        {"accept"
                                              "application/json"}})]
      (is (= 200 (:status response))))))

(deftest root-serves-index-test
  (testing "GET / returns 200 with HTML"
    (let [handler  (router/app)
          response (handler {:request-method :get
                             :uri            "/"})]
      (is (= 200 (:status response))))))

(deftest not-found-test
  (testing "unknown route returns 404"
    (let [handler  (router/app)
          response (handler {:request-method :get
                             :uri            "/no-such-route"})]
      (is (= 404 (:status response))))))
