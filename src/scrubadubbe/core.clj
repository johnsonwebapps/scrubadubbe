(ns scrubadubbe.core
  (:require [clojure.java.jdbc :as jdbc]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [scrubadubbe.login :as login]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.anti-forgery :as antif]
            [miner.ftp :as ftp]
            ;[clj-time.format :as f]
            [clojure.data.csv :as csv]
            ;[clj-ssh.ssh.sftp :as sftp]
            )
  (:import (java.nio.file Files StandardCopyOption)
           (java.sql DriverManager)
           (java.io StringWriter)))
  
 ;removed database configuration
    
    (import [java.time.format DateTimeFormatter])
    
 (defn format-date [dt-key]
   (let [date-format (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
     (.format dt-key date-format)))
  
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

(defn get-coupon-batch [dealer_id coupon_id]
  (jdbc/query db-spec ["WITH RankedCodes AS (
                          SELECT codes.id, codes.dealer_id, codes.coupon_id, codes.batch, codes.human, codes.barcode, codes.used,
                                 ROW_NUMBER() OVER (PARTITION BY batch ORDER BY id) AS rn,
                                 COUNT(*) OVER (PARTITION BY batch) AS total_count,
                                 SUM(CASE WHEN used != 1 THEN 1 ELSE 0 END) OVER (PARTITION BY batch) AS available_amount
                          FROM codes
                          WHERE dealer_id = ? AND coupon_id = ?)
                        SELECT *
                        FROM RankedCodes
                        WHERE rn = 1;"
                       dealer_id coupon_id]))
  
(defn get-coupon [dealer_id]
  (jdbc/query db-spec ["SELECT coupons.id, coupons.name, coupons.description, coupons.fields, coupons.expiration_date, coupons.expiration_days, coupons.rate, coupons.customer_redeemable, coupons.filename, coupons.active,  COUNT(redemptions.coupon_id) as coupon_count, (SELECT COUNT(*) FROM `codes` WHERE dealer_id = ? AND coupon_id = coupons.id AND used = 0) as code_balance, (SELECT COUNT(*) FROM `redemptions` WHERE dealer_id = ? AND coupon_id = coupons.id AND sitewatch_saleid IS NOT NULL) as redeemed_count FROM dealers LEFT JOIN redemptions ON (dealers.id = redemptions.dealer_id) LEFT JOIN coupons ON (coupons.id = redemptions.coupon_id) WHERE dealers.id = ? GROUP BY coupons.id;"dealer_id dealer_id dealer_id]))

(defn get-couponcodes [dealer_id coupon_id batch]
  (jdbc/query db-spec ["SELECT codes.barcode, codes.human, codes.used FROM codes WHERE dealer_id = ? AND coupon_id = ? AND batch = ?;" dealer_id, coupon_id batch]))

;(get-couponcodes 1 1 1)

(defn coupons-to-csv [coupons]
  (let [writer (StringWriter.)]
    (csv/write-csv writer [["Barcode" "Human" "Used"]]
                   :separator \,)
    (csv/write-csv writer (map (juxt :barcode :human :used) coupons)
                   :separator \,)
    (.toString writer)))

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
  
 (defn update-partner [name email website bcc_email logo reminder sms active id]
  (jdbc/execute! db-spec ["UPDATE dealers SET name = ?, email = ?, website = ?, bcc_email = ?, logo = ?, reminder = ?, sms = ?, active = ?  WHERE id = ?", name, email, website, bcc_email, logo, reminder, sms, active, id]))    


;; (defn update-coupon [name description filename expiration_date expiration_days rate fields active customer_redeemable id]
;;   (jdbc/execute! db-spec ["UPDATE coupons SET name = ?, description = ?, filename = ?, expiration_date = ?, expiration_days = ?, rate = ?, fields = ?, active = ?, customer_redeemable = ?,  WHERE id = ?", name, 
;;                           description, filename, expiration_date, expiration_days, rate, fields, active, customer_redeemable, id]))    

(defn update-coupon [name description filename expiration_date expiration_days rate fields active customer_redeemable id]
  (jdbc/execute! db-spec ["UPDATE coupons SET name = ?, description = ?, filename = ?, expiration_date =?, expiration_days = ?, rate = ?, fields = ?, active = ?, customer_redeemable = ?  WHERE id = ?", name, description, filename, expiration_date, expiration_days, rate, fields, active, customer_redeemable, id]))  


  (defn update-pw [userid pw]
    (jdbc/execute! db-spec ["UPDATE users SET password = ? WHERE username = ?", pw, userid]))

 ; (update-pw "joset@scrubadub.com" "bcrypt+sha512$adb56a6bcaed67f417a595e59db2442a$12$83a5e20f98baf0972d7979bd692ec0f68fae908abad3a464")

;SSH SFTP Testing

;; (ftp/with-ftp [client "ftp://scrubadubhq.com/uploads"
;;                :username "cjohnson@scrubadubhq.com"
;;                :password "1J@hns0n1"
;;                :file-type :binary]
;;   (ftp/client-put client "Mathworks.png" "Mathworks.png"))
  
(defn upload-file [{:keys [username password] :as request}]
  (let [{:keys [filename tempfile]} (get-in request [:params :file-upload])]
    (println "Filename: " filename)
    (println "Tempfile: " tempfile)
    ;(try
    (ftp/with-ftp [client "ftp://scrubadubhq.com/uploads"
                   :username "cjohnson@scrubadubhq.com"
                   :password "1J@hns0n1"
                   :file-type :binary]
      (ftp/client-put client tempfile filename))   ;; Use tempfile here
    (response/response {:success true
                        :message "File uploaded successfully!"
                        :file-name filename})
      ;; (catch Exception e
      ;;   (do
      ;;     (response/response {:error "Internal Server Error"
      ;;                                      :exception (.getMessage e)})))
     ; )
    ))



 (defroutes app-routes
  (GET "/csrftoken" [request]
       ;; Remove the signin function as we don't need to sign in for CSRF token.
    (let [csrf-token (get-in request [:session ::antif/anti-forgery-token])
          ]
      (println (str request))
      
         ;; Since we're only interested in csrf-token, we don't need to check whether `signin` returned "Login Failed".
      (-> (response/response {:success true}) ;; Return csrf-token in the response body
          (response/status 200)
          (response/header "Content-Type" "application/json; charset=utf-8")
          (response/header "Access-Control-Allow-Origin" "*")
          (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
          (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization, x-csrf-token"))))
   
    (POST "/upload" [] (wrap-multipart-params upload-file)) 
   
   (GET "/partners/coupon/id/:couponid" [couponid :as request]
     (let [auth-header (get-in request [:headers "authorization"])
           token       (when auth-header (second (clojure.string/split auth-header #" ")))
           tokenvalid  (when token (login/validate-token token))]
       (if (and tokenvalid (= 2 (get tokenvalid :role)))
   
   
         (let [coupon-info (get-coupon couponid)
               formatted-coupon-info (map #(update % :updated_ts format-date) coupon-info)
               ]
           (-> (response/response {:coupon coupon-info})
               (response/status 200)
             (response/header "Access-Control-Allow-Origin" "*")
             (response/header "Content-Type" "application/json; charset=utf-8")
             (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
             (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization,  x-csrf-token"))
          )
            (-> (response/response {:error (str "Invalid Token")})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))) 
     )
   
   (GET "/couponbatch/:dealerid/:couponid" [dealerid couponid :as request]
     (let [auth-header (get-in request [:headers "authorization"])
           token       (when auth-header (second (clojure.string/split auth-header #" ")))
           tokenvalid  (when token (login/validate-token token))]
       (if (and tokenvalid (= 2 (get tokenvalid :role)))
   
   
         (let [couponbatch-info (get-coupon-batch dealerid couponid)
               ]
           (-> (response/response {:couponbatch couponbatch-info})
               (response/status 200)
               (response/header "Access-Control-Allow-Origin" "*")
               (response/header "Content-Type" "application/json; charset=utf-8")
               (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
               (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization,  x-csrf-token")))
         (-> (response/response {:error (str "Invalid Token")})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))))
   
    (GET "/download-codes/:dealerid/:couponid/:batch" [dealerid couponid batch :as request]
     (let [auth-header (get-in request [:headers "authorization"])
           token       (when auth-header (second (clojure.string/split auth-header #" ")))
           tokenvalid  (when token (login/validate-token token))]
       (if (and tokenvalid (= 2 (get tokenvalid :role)))
   
   
         (let [coupons (get-couponcodes dealerid couponid batch)
                csv-data (coupons-to-csv coupons)]
           (-> (response/response csv-data)
               (response/status 200)
               (response/header "Access-Control-Allow-Origin" "*")
               (response/header "Content-Type" "text/csv")
               (response/header "Content-Disposition" "attachment; filename=codes.csv")
               (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
               (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization,  x-csrf-token")))
         (-> (response/response {:error (str "Invalid Token")})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))))


   (GET "/login/:username/:password" [username password :as request]
     (let [rslt (signin username password)
           ;csrf-token (get-in request [:session ::antif/anti-forgery-token])
           ];; Extract token from session
       (if (= rslt "Login Failed")
         (-> (response/response {:error "Unauthorized"})
             (response/status 401)
             (response/header "Content-Type" "application/json; charset=utf-8")
             (response/header "Access-Control-Allow-Origin" "*"))
         (-> (response/response {:success true
                                 :token rslt
                                 });; Use the extracted token
             (response/status 200)
              (response/header "Access-Control-Allow-Origin" "*")
              (response/header "Content-Type" "application/json; charset=utf-8")
              (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
              (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization")
            ))))
   


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
    (let [auth-header (get-in request [:headers "authorization"])
          token       (when auth-header (second (clojure.string/split auth-header #" ")))
          tokenvalid  (when token (login/validate-token token))
          id          (get params :id)
          name        (get params :name)
          email       (get params :email)
          website     (get params :website) 
          bcc_email (get params :bcc)
          logo (get params :logo)
          reminder (get params :reminder)
          sms (get params :sms)
          active (get params :active)

          
          ]
      (if (and tokenvalid (= 2 (get tokenvalid :role)))
        (do
          (update-partner name email website bcc_email logo reminder sms active id)
          (-> (response/response {:partners {:message "Partner updated successfully"
                                            }})
              (response/status 200)
              (response/header "Access-Control-Allow-Origin" "*")
              (response/header "Content-Type" "application/json; charset=utf-8")
              (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
              (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization,  x-csrf-token")))
        (-> (response/response {:error (str "Invalid Token" )})
            (response/status 401)
            (response/header "Access-Control-Allow-Origin" "*")))))
   
    (PUT "/coupon/update" {params :body :as request}
     (let [auth-header (get-in request [:headers "authorization"])
           token       (when auth-header (second (clojure.string/split auth-header #" ")))
           tokenvalid  (when token (login/validate-token token))
           id          (get params :id)
           name        (get params :name)
           description       (get params :description)
           filename     (get params :filename)
           expiration_date (get params :expiration_date)
           expiration_days (get params :expiration_days)
           rate (get params :rate)
           fields (get params :fields)
           active (get params :active)
           customer_redeemable (get params :customer_redeemable)]
       (if (and tokenvalid (= 2 (get tokenvalid :role)))
         (do
           (update-coupon name description filename expiration_date expiration_days rate fields active customer_redeemable id)
           (-> (response/response {:coupon {:message "Coupon updated successfully"}})
               (response/status 200)
               (response/header "Access-Control-Allow-Origin" "*")
               (response/header "Content-Type" "application/json; charset=utf-8")
               (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
               (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization,  x-csrf-token")))
         (-> (response/response {:error (str "Invalid Token")})
             (response/status 401)
             (response/header "Access-Control-Allow-Origin" "*")))))


   (OPTIONS "*" []
      (-> (response/response "")
          (response/header "Access-Control-Allow-Origin" "*")
          (response/header "Content-Type" "application/json; charset=utf-8")
          (response/header "Access-Control-Allow-Methods" "POST, GET, PUT, DELETE, OPTIONS")
          (response/header "Access-Control-Allow-Headers" "Content-Type, Authorization, x-csrf-token")))
   
    (route/not-found "Not Found")
   )
 
 ; Notice that wrap-json-response and wrap-json-body have been moved onto app-routes.
(def app
  (-> app-routes
      ;wrap-session ;; Add this line
      (defaults/wrap-defaults (assoc-in defaults/site-defaults [:security :anti-forgery] false))
      
      (middleware/wrap-json-body {:keywords? true
                                  :bigdecimals? false
                                  :strict false
                                  :handler (fn [request ex]
                                             {:status 400
                                              :headers {"Content-Type" "application/json"}
                                              :body {:message "Invalid JSON"}})})
      (middleware/wrap-json-response {:pretty true
                                      :escape-slash false
                                      :escape-non-ascii true})))
 
 (defn -main [& args]
   (jetty/run-jetty app {:port (Integer/parseInt (or (System/getenv "PORT") "80"))}))
