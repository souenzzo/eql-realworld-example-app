(ns conduit.server
  (:require [io.pedestal.http :as http]
            [hiccup2.core :as h]
            [cognitect.transit :as t]
            [com.wsscode.pathom.core :as p]
            [conduit.connect.realworld :as connect.realworld]
            [com.wsscode.pathom.diplomat.http :as pd.http]
            [com.wsscode.pathom.diplomat.http.clj-http :as pd.clj-http]
            [com.wsscode.pathom.connect :as pc]
            [ring.util.mime-type :as mime]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.data.json :as json]))

(defn ui-head
  [req]
  [:head
   [:meta {:charset "utf-8"}]
   [:title "Conduit"]
   [:link {:href "data:image/svg+xml" :rel "icon"}]
   [:link {:href        "//code.ionicframework.com/ionicons/2.0.1/css/ionicons.min.css"
           :rel         "stylesheet"
           :integrity   "sha384-4r9SMzlCiUSd92w9v1wROFY7DlBc5sDYaEBhcCJR7Pm2nuzIIGKVRtYWlf6w+GG4"
           :crossorigin "anonymous"
           :type        "text/css"}]
   [:link {:href        "//fonts.googleapis.com/css?family=Titillium+Web:700|Source+Serif+Pro:400,700|Merriweather+Sans:400,700|Source+Sans+Pro:400,300,600,700,300italic,400italic,600italic,700italic"
           :rel         "stylesheet"
           :integrity   "sha256-e7a6pIeT2/bvA4upH/sSZ2wo1eD6CSskP9cW3ePy3IY="
           :crossorigin "anonymous"
           :type        "text/css"}]
   [:link {:rel  "stylesheet"
           :href "//demo.productionready.io/main.css"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1.0"}]
   [:meta {:name    "Description"
           :content "A place to share your knowledge."}]
   [:meta {:name    "description"
           :content "A place to share your knowledge."}]
   [:meta {:name    "theme-color"
           :content "#5cb85c"}]
   [:link {:rel  "manifest"
           :href "/manifest.webmanifest"}]])

(defn spa
  [req]
  {:body    (->> [:html {:lang "en-US"}
                  (ui-head req)
                  [:body {:onload "conduit.client.main('conduit')"}
                   [:div {:id "conduit"}]
                   [:script {:src "/conduit/main.js"}]]]
                 (h/html {:mode :html}
                         (h/raw "<!DOCTYPE html>\n"))
                 str)
   :headers {"Content-Security-Policy" ""
             "Content-Type"            (mime/default-mime-types "html")}
   :status  200})

(defn spa-proxy
  [req]
  {:body    (->> [:html {:lang "en-US"}
                  (ui-head req)
                  [:body {:onload "conduit.eql_client.main('conduit', '/realworld-api')"}
                   [:div {:id "conduit"}]
                   [:script {:src "/conduit/main.js"}]]]
                 (h/html {:mode :html}
                         (h/raw "<!DOCTYPE html>\n"))
                 str)
   :headers {"Content-Security-Policy" ""
             "Content-Type"            (mime/default-mime-types "html")}
   :status  200})

(defn workspace
  [req]
  {:status 200})


(def realworld-parser
  (p/parallel-parser
    {::p/plugins [(pc/connect-plugin {::pc/register connect.realworld/register})
                  p/elide-special-outputs-plugin]
     ::p/mutate  pc/mutate-async
     ::p/env     {::p/reader                   [p/map-reader
                                                pc/parallel-reader
                                                pc/open-ident-reader
                                                p/env-placeholder-reader]
                  :conduit.client-root/jwt     (atom nil)
                  :conduit.client-root/api-url "https://conduit.productionready.io/api"
                  ::pd.http/driver             pd.clj-http/request-async
                  ::p/placeholder-prefixes     #{">"}}}))

(defn realworld-api
  [req]
  (let [tx (-> req :body io/input-stream (t/reader :json) t/read)
        result (async/<!! (realworld-parser req tx))]
    {:body   (fn [out]
               (let [w (t/writer out :json)]
                 (t/write w result)))
     :status 200}))

(defn manifest
  [_]
  {:body   (json/write-str
             {:name             "Conduit"
              :display          "minimal-ui"
              :short_name       "Conduit"
              :theme_color      "#5cb85c"
              :start_url        "https://eql-realworld-example-app.herokuapp.com/spa-proxy"
              :background_color "#ffffff"
              :manifest_version 2
              :version          "1"})
   :status 200})

(def routes
  `#{["/spa" :get spa]
     ["/manifest.webmanifest" :get manifest]
     ["/spa-proxy" :get spa-proxy]
     ["/realworld-api" :post realworld-api]
     ["/workspace" :post workspace]})

(def service-map
  (-> {::http/routes                routes
       ::http/join?                 false
       ::http/mime-types            mime/default-mime-types
       ::http/file-path             "target"
       ::http/resource-path         "public"
       ::http/host                  "0.0.0.0"
       ::http/type                  :jetty
       ::http/container-options     {}
       ::http/not-found-interceptor {:name  ::not-found
                                     :leave (fn [{{:keys [uri]} :request
                                                  :keys         [response]
                                                  :as           ctx}]
                                              (if (http/response? response)
                                                ctx
                                                (assoc ctx
                                                  :response {:body    (->> [:html {:lang "en-US"}
                                                                            [:head
                                                                             [:title "conduit"]]
                                                                            [:body
                                                                             [:h1 (str "Can't find " (pr-str uri))]
                                                                             [:p "Try one of these"]
                                                                             [:ul
                                                                              (for [[href method route] routes
                                                                                    :when (= method :get)]
                                                                                [:li [:a {:href href} route]])]]]
                                                                           (h/html {:mode :html}
                                                                                   (h/raw "<!DOCTYPE html>\n"))
                                                                           str)
                                                             :headers {"Content-Type" (mime/default-mime-types "html")}
                                                             :status  404})))}}
      http/default-interceptors))

(defonce http-state (atom nil))
(defn -main
  [& _]
  (let [port (or (some-> "PORT" System/getenv edn/read-string)
                 8000)]
    (prn [:port port])
    (swap! http-state
           (fn [st]
             (some-> st http/stop)
             (-> service-map
                 (assoc ::http/port port)
                 http/create-server
                 http/start)))
    (prn [:started port])))

(comment
  (require 'shadow.cljs.devtools.server)

  (shadow.cljs.devtools.server/start!)
  (shadow.cljs.devtools.api/watch :conduit))