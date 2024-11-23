(ns scrubadubbe.login
  (:require
   [clojure.java.jdbc :as jdbc]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer :all]
   [buddy.hashers :as hashers]
   [buddy.sign.jws :as jws]
   [buddy.sign.jwt :as jwt]))



;(hashers/derive "ScrubaDub172")

;(hashers/check "secretpassword" "bcrypt+sha512$269e47aa05fbf3fffd5892479a8ceaa1$12$cf9db4ab8fffa965f4ec66dc4999404a0e4913f444ed9a1c")

(defn create-token [userid role] 
  (jwt/sign {:userid userid :role role} "secret"))

(defn signin [userid clear encoded role]
  (if (hashers/check clear encoded)
    (create-token userid role)
    "Login Failed"))

(defn validate-token [token]
  (try
    (let [claims (jwt/unsign token "secret")]
      (if-let [userid (:userid claims)]
        {:valid true
         :userid userid
         :role (:role claims)}
        {:valid false
         :error "Invalid token format"}))
    (catch Exception e
      {:valid false
       :error "Invalid token"})))

;(validate-token "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyaWQiOiJqb3NldEBzY3J1YmFkdWIuY29tIiwicm9sZSI6Mn0.urNAvQZXZcO2ULWwKJahMdppq_n3mYr-x6wsd7FbD0I")

;(def data (jwt/sign {:userid 77} "secret"))
(create-token "cjflash" 2)
;(jwt/unsign data "secret")