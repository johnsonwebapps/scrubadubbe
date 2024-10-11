(defproject scrubadubbe "0.1.0-SNAPSHOT"
 :description "A Clojure web app with MySQL and RESTful API"
               :url "http://example.com/FIXME"
               :license {:name "EPL-2.0" :url "https://www.eclipse.org/legal/epl-2.0/"}
               :dependencies [[org.clojure/clojure "1.11.1"]
                              [org.clojure/java.jdbc "0.7.12"]
                              [mysql/mysql-connector-java "8.0.28"]
                              [compojure "1.6.2"] ; For routing
                              [ring/ring-json "0.5.1"] ; For JSON handling
                              [ring/ring-defaults "0.3.2"]
                              [ring/ring-jetty-adapter "1.8.2"]] ; Common middleware
               :main ^:skip-aot scrubadubbe.core
               :target-path "target/%s"
               :profiles {:uberjar {:aot :all
                                    :uberjar-name "scrubadubbe.jar"}})