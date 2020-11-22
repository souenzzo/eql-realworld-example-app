(ns conduit.connect.proxy
  (:require #?@(:cljs    []
                :default [[clojure.instant :as instant]
                          [cheshire.core :as json]])
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.diplomat.http :as http]
            [clojure.core.async :as async]
            [clojure.string :as string]))

(defn read-instant-date
  [s]
  #?(:cljs    (js/Date. s)
     :default (instant/read-instant-date s)))

(defn json-stringify
  [value]
  #?(:cljs    (js/JSON.stringify value)
     :default (json/generate-string value)))

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
  [profile]
  (->> profile
       (into {}
             (map (fn [[k v]]
                    [(keyword "conduit.profile" (name k))
                     v])))))

(defn qualify-article
  [{:keys [author]
    :as   article}]
  (let [profile (when author
                  (qualify-profile author))]
    (->> article
         (into (if profile
                 profile
                 {})
               (map (fn [[k v]]
                      (cond
                        (= k :createdAt) [:conduit.article/created-at
                                          (read-instant-date v)]
                        (= k :tagList) [:conduit.article/tag-list
                                        (for [tag v]
                                          {:conduit.tag/tag tag})]
                        :else [(keyword "conduit.article" (name k))
                               v])))))))


(pc/defmutation login [{:conduit.client-root/keys [jwt]
                        :as                       env}
                       {:conduit.profile/keys [email password]}]
  {::pc/params [:conduit.profile/password
                :conduit.profile/email]
   ::pc/sym    'conduit.profile/login}
  (let [body #?(:cljs #js {:user #js{:email    email
                                     :password password}}
                :default {:user {:email    email
                                 :password password}})]
    (async/go
      (let [{::http/keys [body]} (async/<! (fetch env {::path   "/users/login"
                                                       ::method "POST"
                                                       ::body   (json-stringify body)}))
            {:keys [errors user]} body
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
                (empty? errors) (assoc :conduit.redirect/path "#/home"))))))

(pc/defmutation create-user [{:conduit.client-root/keys [jwt]
                              :as                       env}
                             {:conduit.profile/keys [email password username]}]
  {::pc/params [:conduit.profile/password
                :conduit.profile/email
                :conduit.profile/username]
   ::pc/sym    'conduit.profile/register}
  (let [body #?(:cljs #js {:user #js{:email    email
                                     :username username
                                     :password password}}
                :default {:user {:email    email
                                 :username username
                                 :password password}})]
    (async/go
      (let [{::http/keys [body]} (async/<! (fetch env {::path   "/users"
                                                       ::method "POST"
                                                       ::body   (json-stringify body)}))
            {:keys [errors user]} body
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
                (empty? errors) (assoc :conduit.redirect/path "#/home"))))))

(pc/defresolver get-article [env {:conduit.article/keys [slug]}]
  {::pc/output [:conduit.article/body
                :conduit.profile/image
                :conduit.article/created-at
                :conduit.profile/username
                :conduit.article/title]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path (str "/articles/" slug)}))
          {:keys [article]} body]
      (qualify-article article))))

(pc/defresolver get-article-comments [env {:conduit.article/keys [slug]}]
  {::pc/output [:conduit.article/comments]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path (str "/articles/" slug "/comments")}))
          {:keys [comments]} body]
      {:conduit.article/comments (for [{:keys [id body createdAt author updatedAt]} comments
                                       :let [profile (qualify-profile author)]]
                                   (merge
                                     profile
                                     {:conduit.comment/id         id
                                      :conduit.comment/body       body
                                      :conduit.comment/author     author
                                      :conduit.comment/created-at (read-instant-date createdAt)
                                      :conduit.comment/updated-at (read-instant-date updatedAt)}))})))

(pc/defresolver get-profile-by-username [env {:conduit.profile/keys [username]}]
  {::pc/output [:conduit.profile/bio
                :conduit.profile/image
                :conduit.profile/following]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path (str "/profiles/" username)}))
          {:keys [profile]} body]
      (qualify-profile profile))))

(pc/defresolver get-articles-by-username [env {:conduit.profile/keys [username]}]
  {::pc/output [:conduit.profile/article-count
                :conduit.profile/articles]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path (str "/articles?author=" username "&limit=5&offset=0")}))
          {:keys [articles articlesCount]} body]
      {:conduit.profile/article-count articlesCount
       :conduit.profile/articles      (map qualify-article articles)})))

(pc/defresolver get-articles [env _input]
  {::pc/output [:conduit.client-root/articles]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path "/articles"}))
          {:keys [articlesCount
                  articles]} body
          articles* (for [article articles]
                      (qualify-article article))]
      {:conduit.client-root/articles-count articlesCount
       :conduit.client-root/articles       articles*})))

(pc/defresolver current-user [{:conduit.client-root/keys [jwt]
                               :as                       env} _input]
  {::pc/output [:conduit.client-root/my-profile]}
  (when @jwt
    (async/go
      (let [{::http/keys [body]} (async/<! (fetch env {::path "/user"}))
            {:keys [user]} body]
        {:conduit.client-root/my-profile (assoc (qualify-profile user)
                                           :conduit.profile/me? true)}))))

(pc/defresolver popular-tags [env _input]
  {::pc/output [:conduit.client-root/popular-tags]}
  (async/go
    (let [{::http/keys [body]} (async/<! (fetch env {::path "/tags"}))
          {:keys [tags]} body]
      {:conduit.client-root/popular-tags (for [tag tags]
                                           {:conduit.tag/tag tag})})))

(def -register
  [login
   create-user
   get-article
   get-article-comments
   get-profile-by-username
   current-user
   popular-tags
   get-articles
   get-articles-by-username
   (pc/constantly-resolver
     :conduit.client-root/feed-toggle [{:conduit.feed-button/label "Your Feed"
                                        :conduit.feed-button/href  "#/home"}
                                       {:conduit.feed-button/label "Global Feed"
                                        :conduit.feed-button/href  "#/home"}])
   (pc/single-attr-resolver :conduit.article/slug :conduit.article/href #(str "#/article/" %))
   (pc/single-attr-resolver :conduit.profile/username :conduit.profile/href #(str "#/profile/" %))
   (pc/resolver `top-routes
                {::pc/output [:conduit.client-root/top-routes]}
                (fn [{:keys [parser]
                      :as   env} _input]
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
                                                           :conduit.client-root/path  "#/settings"}
                                                          {:conduit.client-root/label username
                                                           :conduit.client-root/img   image
                                                           :conduit.client-root/path  (str "#/profile/" username)}]
                                                         [{:conduit.client-root/label "Home"
                                                           :conduit.client-root/path  "#/home"}
                                                          {:conduit.client-root/label "Sign up"
                                                           :conduit.client-root/path  "#/register"}
                                                          {:conduit.client-root/label "Sign in"
                                                           :conduit.client-root/path  "#/login"}]))})))
   (pc/constantly-resolver :conduit.client-root/waiting? false)
   (pc/resolver `fav-articles
                {::pc/input  #{:conduit.profile/articles}
                 ::pc/output [:conduit.profile/favorites-articles]}
                (fn [_ {:conduit.profile/keys [articles]}]
                  (let [favs (filter :conduit.article/favorited? articles)]
                    {:conduit.profile/favorites-articles       favs
                     :conduit.profile/favorites-articles-count (count favs)})))])
