(ns conduit.connect.datascript-test
  (:require [clojure.test :refer [deftest is testing]]
            [conduit.connect.datascript :as connect.datascript]
            [com.wsscode.pathom.core :as p]
            [clojure.core.async :as async]
            [com.wsscode.pathom.connect :as pc]
            [datascript.core :as ds]))

(deftest root-articles
  (let [conn (ds/create-conn connect.datascript/schema)
        db (-> (ds/transact! conn [{:conduit.article/slug        "my-first-article"
                                    :conduit.article/title       "My first article!"
                                    :conduit.article/href        "/article/my-first-article"
                                    :conduit.article/created-at  #inst"2020"
                                    :conduit.article/author      -1
                                    :conduit.article/tag-list    [-2 -3]
                                    :conduit.article/description "Hello, I'm souenzzo and I'm writing a article for conduit!"}
                                   {:db/id                    -1
                                    :conduit.profile/username "souenzzo"
                                    :conduit.profile/image    "https://eql-realworld-example-app.herokuapp.com/images/souenzzo.jpg"
                                    :conduit.profile/href     "/profile/souenzzo"}
                                   {:db/id            -2
                                    :conduit.tag/tag  "a"
                                    :conduit.tag/href "/tag/a"}
                                   {:db/id            -3
                                    :conduit.tag/tag  "b"
                                    :conduit.tag/href "/tag/b"}])
               :db-after)
        parallel-parser (p/parallel-parser {::p/plugins [(pc/connect-plugin {::pc/register connect.datascript/register})]})
        env {::p/reader                [p/map-reader
                                        pc/parallel-reader
                                        pc/open-ident-reader
                                        p/env-placeholder-reader]
             ::connect.datascript/conn conn
             ::connect.datascript/db   db}
        tx! (fn [tx]
              (async/<!! (parallel-parser env tx)))]
    (testing
      "home page"
      (is (= {:>/article-meta              {:conduit.article/created-at      #inst "2020-01-01T00:00:00.000-00:00"
                                            ;; TBD
                                            :conduit.article/favorited?      :com.wsscode.pathom.core/not-found
                                            ;; TBD
                                            :conduit.article/favorites-count :com.wsscode.pathom.core/not-found
                                            :conduit.article/slug            "my-first-article"
                                            :conduit.profile/href            "/profile/souenzzo"
                                            :conduit.profile/image           "https://eql-realworld-example-app.herokuapp.com/images/souenzzo.jpg"
                                            :conduit.profile/username        "souenzzo"}
              :conduit.article/description "Hello, I'm souenzzo and I'm writing a article for conduit!"
              :conduit.article/href        "/article/my-first-article"
              :conduit.article/slug        "my-first-article"
              :conduit.article/tag-list    [{:conduit.tag/href "/tag/a"
                                             :conduit.tag/tag  "a"}
                                            {:conduit.tag/href "/tag/b"
                                             :conduit.tag/tag  "b"}]
              :conduit.article/title       "My first article!"}
             (-> [{:conduit.client-root/articles [:conduit.article/slug
                                                  :conduit.article/title
                                                  :conduit.article/href
                                                  :conduit.article/description
                                                  {:conduit.article/tag-list [:conduit.tag/tag
                                                                              :conduit.tag/href]}
                                                  {:>/article-meta [:conduit.profile/href
                                                                    :conduit.profile/image
                                                                    :conduit.profile/username
                                                                    :conduit.article/created-at
                                                                    :conduit.article/favorited?
                                                                    :conduit.article/favorites-count
                                                                    :conduit.article/slug]}]}]
                 tx!
                 :conduit.client-root/articles
                 first))))))
