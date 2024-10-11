(ns scrubadubbe.core
  (:require [clojure.java.jdbc :as jdbc]
             [compojure.core :refer :all]
             [compojure.route :as route]
             [ring.middleware.json :as middleware]
             [ring.util.response :as response]
             [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :as defaults])
   (:import java.sql.DriverManager))
  
  (def db-spec
    {:classname "com.mysql.cj.jdbc.Driver"
     :subprotocol "mysql"
     :subname (str "//" (System/getenv "DB_HOST") ":" (System/getenv "DB_PORT") "/" (System/getenv "DB_NAME"))
     :user (System/getenv "DB_USER")
     :password (System/getenv "DB_PASS")
     })
  
   
  
  ; Example query function
  (defn get-items []
    (jdbc/query db-spec ["SELECT * FROM users WHERE username = 'joset@scrubadub.com'"]))
  
  (get-items)
  
  (defroutes app-routes
    (GET "/items" [] (response/response (get-items)))
    (route/not-found "Not Found"))
  
  (defn wrap-json-response [handler]
    (fn [request]
      (-> (handler request)
          (middleware/wrap-json-body)
          (middleware/wrap-json-response))))
  
  (def app
    (-> app-routes
        wrap-json-response
        (ring.middleware.defaults/wrap-defaults ring.middleware.defaults/site-defaults)))
  
  (defn -main [& args]
    (jetty/run-jetty app {:port (Integer/parseInt (or (System/getenv "PORT") "3000"))}))
