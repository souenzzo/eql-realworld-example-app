(ns conduit.register
  (:require [com.wsscode.pathom.connect :as pc]
            [clojure.core.async :as async]
            [clojure.core.async.interop :refer [<p!]]
            [goog.object :as gobj]))

(defn fetch
  [{:conduit.client/keys [jwt api-url]} {::keys [path method body]
                                         :or    {method "GET"}}]
  (let [authorization (some-> jwt
                              deref
                              (->> (str "Token ")))
        headers (cond-> #js{"Content-Type" "application/json"}
                        authorization (doto (gobj/set "Authorization" authorization)))
        opts (cond-> #js{:method  method
                         :headers headers}
                     body (doto (gobj/set "body" body)))]
    (async/go
      (-> (js/fetch (str api-url path) opts)
          <p!
          .json
          <p!))))

(defn qualify-profile
  [{:strs [bio
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
  [{:strs [title slug body createdAt updatedAt tagList description author favorited favoritesCount]}]
  (let [profile (when author
                  (qualify-profile author))]
    (into {}
          (remove (comp nil? val))
          (merge profile
                 (when author
                   {:conduit.article/author profile})
                 {:conduit.article/title           title
                  :conduit.article/created-at      (new js/Date createdAt)
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
     :conduit.client/feed-toggle [{:conduit.feed-button/label "Your Feed"
                                   :conduit.feed-button/href  (str "#/home")}
                                  {:conduit.feed-button/label "Global Feed"
                                   :conduit.feed-button/href  (str "#/home")}])
   (pc/resolver `top-routes
                {::pc/output [:conduit.client/top-routes]}
                (fn [{:keys [parser]
                      :as   env} _]
                  (async/go
                    {:conduit.client/top-routes (let [{:conduit.profile/keys [username
                                                                              image]} (-> (parser env [{:conduit.client/my-profile [:conduit.profile/username
                                                                                                                                    :conduit.profile/image]}])
                                                                                          async/<!
                                                                                          :conduit.client/my-profile)]
                                                  (if username
                                                    [{:conduit.client/label "Home"
                                                      :conduit.client/path  "#/home"}
                                                     {:conduit.client/label "New Post"
                                                      :conduit.client/icon  "ion-compose"
                                                      :conduit.client/path  "#/editor"}
                                                     {:conduit.client/label "Settings"
                                                      :conduit.client/icon  "ion-gear-a"
                                                      :conduit.client/path  (str "#/settings")}
                                                     {:conduit.client/label username
                                                      :conduit.client/img   image
                                                      :conduit.client/path  (str "#/profile/" username)}]
                                                    [{:conduit.client/label "Home"
                                                      :conduit.client/path  "#/home"}
                                                     {:conduit.client/label "Sign up"
                                                      :conduit.client/path  "#/register"}
                                                     {:conduit.client/label "Sign in"
                                                      :conduit.client/path  "#/login"}]))})))
   (pc/mutation `conduit.profile/login
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email]
                 ::pc/output []}
                (fn [{:conduit.client/keys [jwt]
                      :as                  env} {:conduit.profile/keys [email password]}]
                  (let [body #js {:user #js{:email    email
                                            :password password}}]
                    (async/go
                      (let [response (async/<! (fetch env {::path   "/users/login"
                                                           ::method "POST"
                                                           ::body   (js/JSON.stringify body)}))
                            {:strs [errors user]} (js->clj response)
                            {:conduit.profile/keys [token]
                             :as                   my-profile} (assoc (qualify-profile user)
                                                                 :conduit.profile/email email)]
                        (when token
                          (reset! jwt token))
                        (cond-> my-profile
                                errors (assoc :conduit.client/errors (for [[k vs] errors
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
                (fn [{:conduit.client/keys [jwt]
                      :as                  env} {:conduit.profile/keys [email password username]}]
                  (let [body #js {:user #js{:email    email
                                            :username username
                                            :password password}}]
                    (async/go
                      (let [response (async/<! (fetch env {::path   "/users"
                                                           ::method "POST"
                                                           ::body   (js/JSON.stringify body)}))
                            {:strs [errors user]} (js->clj response)
                            {:conduit.profile/keys [token]
                             :as                   my-profile} (assoc (qualify-profile user)
                                                                 :conduit.profile/email email)]
                        (when token
                          (reset! jwt token))
                        (cond-> my-profile
                                errors (assoc :conduit.client/errors (for [[k vs] errors
                                                                           v vs]
                                                                       {:conduit.error/id      (str (gensym "conduit.error"))
                                                                        :conduit.error/message (str k ": " v)}))
                                (empty? errors) (assoc :conduit.redirect/path "#/home")))))))
   (pc/constantly-resolver :conduit.client/waiting? false)
   (pc/resolver `article
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/body
                              :conduit.profile/image
                              :conduit.article/created-at
                              :conduit.profile/username
                              :conduit.article/title]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles/" slug)}))
                          {:strs [article]} (js->clj result)]
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
                    (let [result (async/<! (fetch ctx {::path (str "/articles/" slug "/comments")}))
                          {:strs [comments]} (js->clj result)]
                      {:conduit.article/comments (for [{:strs [id body createdAt author updatedAt]} comments
                                                       :let [profile (qualify-profile author)]]
                                                   (merge
                                                     profile
                                                     {:conduit.comment/id         id
                                                      :conduit.comment/body       body
                                                      :conduit.comment/author     author
                                                      :conduit.comment/created-at (new js/Date createdAt)
                                                      :conduit.comment/updated-at (new js/Date updatedAt)}))}))))
   (pc/resolver `current-user
                {::pc/output [:conduit.client/my-profile]}
                (fn [{:conduit.client/keys [jwt]
                      :as                  env} _]
                  (when @jwt
                    (async/go
                      (let [result (async/<! (fetch env {::path (str "/user")}))
                            {:strs [user]} (js->clj result)]
                        {:conduit.client/my-profile (assoc (qualify-profile user)
                                                      :conduit.profile/me? true)})))))
   (pc/resolver `profile
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/bio
                              :conduit.profile/image
                              :conduit.profile/following]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/profiles/" username)}))
                          {:strs [profile]} (js->clj result)]
                      (qualify-profile profile)))))
   (pc/resolver `profile/articles
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/article-count
                              :conduit.profile/articles]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles?author=" username "&limit=5&offset=0")}))
                          {:strs [articles articlesCount]} (js->clj result)]
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
                {::pc/output [:conduit.client/popular-tags]}
                (fn [ctx _]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path "/tags"}))
                          {:strs [tags]} (js->clj result)]
                      {:conduit.client/popular-tags (for [tag tags]
                                                      {:conduit.tag/tag tag})}))))
   (pc/resolver `articles
                {::pc/output [:conduit.client/articles]}
                (fn [ctx _]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path "/articles"}))
                          {:strs [articlesCount
                                  articles]} (js->clj result)]
                      {:conduit.client/articles-count articlesCount
                       :conduit.client/articles       (for [article articles]
                                                        (qualify-article article))}))))])
