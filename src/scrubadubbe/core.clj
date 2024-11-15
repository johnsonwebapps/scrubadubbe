(ns scrubadubbe.core
  (:require [clojure.java.jdbc :as jdbc]
             [compojure.core :refer :all]
             [compojure.route :as route]
             [ring.middleware.json :as middleware]
             [ring.util.response :as response]
             [ring.adapter.jetty :as jetty]
            [scrubadubbe.login :as login]
            [ring.middleware.defaults :as defaults])
   (:import java.sql.DriverManager))
  
  ;; (def db-spec
  ;;   {:classname "com.mysql.cj.jdbc.Driver"
  ;;    :subprotocol "mysql"
  ;;    :subname (str "//" (System/getenv "DB_HOST") ":" (System/getenv "DB_PORT") "/" (System/getenv "DB_NAME"))
  ;;    :user (System/getenv "DB_USER")
  ;;    :password (System/getenv "DB_PASS")
  ;;    })
  
    ;; (def db-spec
    ;;  {:classname "com.mysql.cj.jdbc.Driver"
    ;;   :subprotocol "mysql"
    ;;   :subname (str "//" "208.109.67.238" ":" "3306" "/" "db_partner_2024");(str "//" (System/getenv "DB_HOST") ":" (System/getenv "DB_PORT") "/" (System/getenv "DB_NAME"))
    ;;   :user "dba_partner_2020";(System/getenv "DB_USER")
    ;;   :password "ScrubaDub172";(System/getenv "DB_PASS")
    ;;   })
    
    (def db-spec
      {:classname "com.mysql.cj.jdbc.Driver"
       :subprotocol "mysql"
       :subname (str "//" "localhost" ":" "8889" "/" "db_partner_2020");(str "//" (System/getenv "DB_HOST") ":" (System/getenv "DB_PORT") "/" (System/getenv "DB_NAME"))
       :user "dev";(System/getenv "DB_USER")
       :password "admin";(System/getenv "DB_PASS")
       })
  
  ; Example query function
  (defn signin [username password]
    (let [rslt (jdbc/query db-spec ["SELECT * FROM users WHERE username = ?" username])
          pw (get (first rslt) :password)
          userid (get (first rslt) :id)
          roletslt (jdbc/query db-spec ["SELECT role_id FROM roles_users WHERE user_id = ?" userid])
          role (get (first roletslt) :role_id)
          ] 
      (login/signin username password pw role)) 
    )


(defn get-partners []
  (jdbc/query db-spec ["
    SELECT dealers.*, 
           COUNT(redemptions.dealer_id) as coupon_count,
           (SELECT COUNT(*) FROM `codes` WHERE dealer_id = dealers.id AND used = 0) as code_balance,
           (SELECT COUNT(*) FROM `redemptions` WHERE dealer_id = dealers.id AND sitewatch_saleid IS NOT NULL) as redeemed_count
    FROM dealers 
    LEFT JOIN redemptions ON (dealers.id = redemptions.dealer_id)
    GROUP BY dealers.id
  "]))
  
 (defn update-partner [name email website id]
  (jdbc/execute! db-spec ["UPDATE dealers SET name = ?, email = ?, website = ?  WHERE id = ?", name, email, website, id]))    


  (defn update-pw [userid pw]
    (jdbc/execute! db-spec ["UPDATE users SET password = ? WHERE username = ?", pw, userid]))

 ; (update-pw "joset@scrubadub.com" "bcrypt+sha512$adb56a6bcaed67f417a595e59db2442a$12$83a5e20f98baf0972d7979bd692ec0f68fae908abad3a464")


 (defroutes app-routes
   (GET "/login/:username/:password" [username password]
     (let [rslt (signin username password)]
       (if (= rslt "Login Failed")
         (-> (response/response {:error "Unauthorized"})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*"))
         (-> (response/response {:success true
                                 :token rslt})
             (response/status 200)
             (response/header "Access-Control-Allow-Origin" "*")))))

   (GET "/partners/:token" [token]
     (let [tokenvalid (login/validate-token token)]
       (if (and (get tokenvalid :valid) (= 2 (get tokenvalid :role)))
         (-> (response/response {:partners (get-partners)})
             (response/status 200)
             (response/header "Access-Control-Allow-Origin" "*")
             (response/header "Content-Type" "application/json; charset=utf-8")
             (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
             (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization"))
         (-> (response/response {:error "Invalid Token"})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))))

   (PUT "/partners/update" {params :body :as request}
     (let [auth-header (get-in request [:headers "Authorization"]) 
           token       (and auth-header (second (clojure.string/split auth-header #" ")))
           tokenvalid  (when token (login/validate-token token))
           id          (get params :id)
           name        (get params :name)
           email       (get params :email)
           website     (get params :website)] 
       (if (and tokenvalid (= 2 (get tokenvalid :role)))
         (-> (response/response {:partners (update-partner id name email website)})
             (response/status 200)
             (response/header "Access-Control-Allow-Origin" "*")
             (response/header "Content-Type" "application/json; charset=utf-8")
             (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
             (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization"))
         (-> (response/response {:error "Invalid Token"})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))))


   (route/not-found "Not Found"))
 
 ; Notice that wrap-json-response and wrap-json-body have been moved onto app-routes.
 (def app
   (-> app-routes
       (middleware/wrap-json-body {:keywords? true
                                    :bigdecimals? false
                                    :strict true
                                    :handler (fn [request ex]
                                               {:status 400
                                                :headers {"Content-Type" "application/json"}
                                                :body {:message "Invalid JSON"}})})
       (middleware/wrap-json-response {:pretty true
                                      :escape-slash false
                                      :escape-non-ascii true})
       (defaults/wrap-defaults defaults/site-defaults)))
 
 (defn -main [& args]
   (jetty/run-jetty app {:port (Integer/parseInt (or (System/getenv "PORT") "80"))}))
