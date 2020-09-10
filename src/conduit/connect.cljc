(ns conduit.connect
  (:require [com.wsscode.pathom.connect :as pc]
            [clojure.core.async :as async]
            #?@(:cljs    [[clojure.core.async.interop :refer [<p!]]
                          [goog.object :as gobj]]
                :default [[clojure.instant :as instant]
                          [cheshire.core :as json]])
            [com.wsscode.pathom.diplomat.http :as http]
            [clojure.string :as string]))

(defn read-instant-date
  [s]
  #?(:cljs    (new js/Date s)
     :default (instant/read-instant-date s)))

(defn json-stringify
  [value]
  #?(:cljs    (js/JSON.stringify value)
     :default (json/generate-string value)))

#?(:clj (def js->clj identity))

(defn fetch
  [{:conduit.client-root/keys [jwt api-url]
    :as                       env}
   {::keys [path method body]
    :or    {method "GET"}}]
  (let [authorization (some-> jwt
                              deref
                              (->> (str "Token ")))
        url (str api-url path)]
    (cond-> (assoc env
              ::http/as ::http/json
              ::http/url url
              ::http/content-type ::http/json
              ::http/method (keyword "com.wsscode.pathom.diplomat.http" (string/lower-case method)))
            body (assoc ::http/body body)
            authorization (assoc [::http/headers "Authorization"] body)
            :always http/request)))

(defn qualify-profile
  [{:keys [bio
           following
           token
           image
           email
           username]}]
  (into {}
        (remove (comp nil? val))
        #:conduit.profile{:bio       bio
                          :following following
                          :image     image
                          :token     token
                          :email     email
                          :username  username}))

(defn qualify-article
  [{:keys [title slug body createdAt updatedAt tagList description author favorited favoritesCount]}]
  (let [profile (when author
                  (qualify-profile author))]
    (into {}
          (remove (comp nil? val))
          (merge profile
                 (when author
                   {:conduit.article/author profile})
                 {:conduit.article/title           title
                  :conduit.article/created-at      (read-instant-date createdAt)
                  :conduit.article/slug            slug
                  :conduit.article/updated-at      updatedAt
                  :conduit.article/description     description
                  :conduit.article/favorited?      favorited
                  :conduit.article/favorites-count favoritesCount
                  :conduit.article/tag-list        (for [tag tagList]
                                                     {:conduit.tag/tag tag})

                  :conduit.article/body            body}))))


(def register
  [(pc/constantly-resolver
     :conduit.client-root/feed-toggle [{:conduit.feed-button/label "Your Feed"
                                        :conduit.feed-button/href  (str "#/home")}
                                       {:conduit.feed-button/label "Global Feed"
                                        :conduit.feed-button/href  (str "#/home")}])
   (pc/resolver `top-routes
                {::pc/output [:conduit.client-root/top-routes]}
                (fn [{:keys [parser]
                      :as   env} _]
                  (async/go
                    {:conduit.client-root/top-routes (let [{:conduit.profile/keys [username
                                                                                   image]} (-> (parser env [{:conduit.client-root/my-profile [:conduit.profile/username
                                                                                                                                              :conduit.profile/image]}])
                                                                                               async/<!
                                                                                               :conduit.client-root/my-profile)]
                                                       (if username
                                                         [{:conduit.client-root/label "Home"
                                                           :conduit.client-root/path  "#/home"}
                                                          {:conduit.client-root/label "New Post"
                                                           :conduit.client-root/icon  "ion-compose"
                                                           :conduit.client-root/path  "#/editor"}
                                                          {:conduit.client-root/label "Settings"
                                                           :conduit.client-root/icon  "ion-gear-a"
                                                           :conduit.client-root/path  (str "#/settings")}
                                                          {:conduit.client-root/label username
                                                           :conduit.client-root/img   image
                                                           :conduit.client-root/path  (str "#/profile/" username)}]
                                                         [{:conduit.client-root/label "Home"
                                                           :conduit.client-root/path  "#/home"}
                                                          {:conduit.client-root/label "Sign up"
                                                           :conduit.client-root/path  "#/register"}
                                                          {:conduit.client-root/label "Sign in"
                                                           :conduit.client-root/path  "#/login"}]))})))
   (pc/mutation `conduit.profile/login
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email]
                 ::pc/output []}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} {:conduit.profile/keys [email password]}]
                  (let [body #js {:user #js{:email    email
                                            :password password}}]
                    (async/go
                      (let [response (::http/body (async/<! (fetch env {::path   "/users/login"
                                                                        ::method "POST"
                                                                        ::body   (json-stringify body)})))
                            {:keys [errors user]} (js->clj response)
                            {:conduit.profile/keys [token]
                             :as                   my-profile} (assoc (qualify-profile user)
                                                                 :conduit.profile/email email)]
                        (when token
                          (reset! jwt token))
                        (cond-> my-profile
                                errors (assoc :conduit.client-root/errors (for [[k vs] errors
                                                                                v vs]
                                                                            {:conduit.error/id      (str (gensym "conduit.error"))
                                                                             :conduit.error/message (str k ": " v)}))
                                (empty? errors) (assoc :conduit.redirect/path "#/home")))))))
   #_(pc/constantly-resolver :conduit.redirect/path nil)
   (pc/mutation `conduit.profile/register
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email
                              :conduit.profile/username]
                 ::pc/output []}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} {:conduit.profile/keys [email password username]}]
                  (let [body #js {:user #js{:email    email
                                            :username username
                                            :password password}}]
                    (async/go
                      (let [response (::http/body (async/<! (fetch env {::path   "/users"
                                                                        ::method "POST"
                                                                        ::body   (json-stringify body)})))
                            {:keys [errors user]} (js->clj response)
                            {:conduit.profile/keys [token]
                             :as                   my-profile} (assoc (qualify-profile user)
                                                                 :conduit.profile/email email)]
                        (when token
                          (reset! jwt token))
                        (cond-> my-profile
                                errors (assoc :conduit.client-root/errors (for [[k vs] errors
                                                                                v vs]
                                                                            {:conduit.error/id      (str (gensym "conduit.error"))
                                                                             :conduit.error/message (str k ": " v)}))
                                (empty? errors) (assoc :conduit.redirect/path "#/home")))))))
   (pc/constantly-resolver :conduit.client-root/waiting? false)
   (pc/resolver `article
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/body
                              :conduit.profile/image
                              :conduit.article/created-at
                              :conduit.profile/username
                              :conduit.article/title]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path (str "/articles/" slug)})))
                          {:keys [article]} (js->clj result)]
                      (qualify-article article)))))
   (pc/resolver `slug->href
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/href]}
                (fn [_ {:conduit.article/keys [slug]}]
                  {:conduit.article/href (str "#/article/" slug)}))
   (pc/resolver `username->href
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/href]}
                (fn [_ {:conduit.profile/keys [username]}]
                  {:conduit.profile/href (str "#/profile/" username)}))
   (pc/resolver `comments
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/comments]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path (str "/articles/" slug "/comments")})))
                          {:keys [comments]} (js->clj result)]
                      {:conduit.article/comments (for [{:keys [id body createdAt author updatedAt]} comments
                                                       :let [profile (qualify-profile author)]]
                                                   (merge
                                                     profile
                                                     {:conduit.comment/id         id
                                                      :conduit.comment/body       body
                                                      :conduit.comment/author     author
                                                      :conduit.comment/created-at (read-instant-date createdAt)
                                                      :conduit.comment/updated-at (read-instant-date updatedAt)}))}))))
   (pc/resolver `current-user
                {::pc/output [:conduit.client-root/my-profile]}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} _]
                  (when @jwt
                    (async/go
                      (let [result (::http/body (async/<! (fetch env {::path (str "/user")})))
                            {:keys [user]} (js->clj result)]
                        {:conduit.client-root/my-profile (assoc (qualify-profile user)
                                                           :conduit.profile/me? true)})))))
   (pc/resolver `profile
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/bio
                              :conduit.profile/image
                              :conduit.profile/following]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path (str "/profiles/" username)})))
                          {:keys [profile]} (js->clj result)]
                      (qualify-profile profile)))))
   (pc/resolver `profile/articles
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/article-count
                              :conduit.profile/articles]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path (str "/articles?author=" username "&limit=5&offset=0")})))
                          {:keys [articles articlesCount]} (js->clj result)]
                      {:conduit.profile/article-count articlesCount
                       :conduit.profile/articles      (map qualify-article articles)}))))
   (pc/resolver `fav-articles
                {::pc/input  #{:conduit.profile/articles}
                 ::pc/output [:conduit.profile/favorites-articles]}
                (fn [_ {:conduit.profile/keys [articles]}]
                  (let [favs (filter :conduit.article/favorited? articles)]
                    {:conduit.profile/favorites-articles       favs
                     :conduit.profile/favorites-articles-count (count favs)})))

   (pc/resolver `popular-tags
                {::pc/output [:conduit.client-root/popular-tags]}
                (fn [ctx _]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path "/tags"})))
                          {:keys [tags]} (js->clj result)]
                      {:conduit.client-root/popular-tags (for [tag tags]
                                                           {:conduit.tag/tag tag})}))))
   (pc/resolver `articles
                {::pc/output [:conduit.client-root/articles]}
                (fn [ctx _]
                  (async/go
                    (let [result (::http/body (async/<! (fetch ctx {::path "/articles"})))
                          {:keys [articlesCount
                                  articles]} (js->clj result)]
                      {:conduit.client-root/articles-count articlesCount
                       :conduit.client-root/articles       (for [article articles]
                                                             (qualify-article article))}))))])
