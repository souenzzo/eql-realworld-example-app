(ns conduit.connect.datascript
  (:require [datascript.core :as ds]
            [com.wsscode.pathom.connect :as pc]
            [clojure.core.async :as async]))

(def schema {:conduit.article/slug               {:db/unique :db.unique/identity}
             :conduit.profile/email              {:db/unique :db.unique/identity}
             :conduit.profile/username           {:db/unique :db.unique/identity}
             :conduit.article/tag-list           {:db/valueType   :db.type/ref
                                                  :db/cardinality :db.cardinality/many}
             :conduit.tag/tag                    {:db/unique :db.unique/identity}
             :conduit.profile/favorites-articles {:db/valueType   :db.type/ref
                                                  :db/cardinality :db.cardinality/many}
             :conduit.article/author             {:db/valueType :db.type/ref}})

#?(:cljs (def Throwable :default))

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
   (pc/single-attr-resolver :conduit.profile/username :conduit.profile/href #(str "/profile/" %))
   (pc/mutation `conduit.profile/login
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email]
                 ::pc/output []}
                (fn [{::keys [db]} {:conduit.profile/keys [email password]}]
                  (if-let [{:conduit.profile/keys [href username]} (ffirst (ds/q '[:find (pull ?e [*])
                                                                                   :in $ ?email ?password
                                                                                   :where
                                                                                   [?e :conduit.profile/email ?email]
                                                                                   [?e :conduit.profile/password ?password]]
                                                                                 db email password))]
                    {:conduit.client-root/top-routes [{:conduit.client-root/label "Home"
                                                       :conduit.client-root/path  "/home"}
                                                      {:conduit.client-root/label "New Article"
                                                       :conduit.client-root/path  "/editor"}
                                                      {:conduit.client-root/label "Settings"
                                                       :conduit.client-root/path  "/settings"}
                                                      {:conduit.client-root/label username
                                                       :conduit.client-root/path  href}]
                     :conduit.redirect/path          "/home"}
                    {:conduit.client-root/top-routes [{:conduit.client-root/label "Home"
                                                       :conduit.client-root/path  "/home"}
                                                      {:conduit.client-root/label "Sign up"
                                                       :conduit.client-root/path  "/register"}
                                                      {:conduit.client-root/label "Sign in"
                                                       :conduit.client-root/path  "/login"}]
                     :conduit.client-root/errors     [{:conduit.error/message "wrong password"}]})))
   (pc/mutation `conduit.profile/register
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email
                              :conduit.profile/username]
                 ::pc/output []}
                (fn [{::keys [conn]} {:conduit.profile/keys [email password username]}]
                  (let [href (str "/profile/" username)]
                    (try
                      (ds/transact! conn [{:conduit.profile/password password
                                           :conduit.profile/email    email
                                           :conduit.profile/href     href
                                           :conduit.profile/username username}])
                      {:conduit.client-root/top-routes [{:conduit.client-root/label "Home"
                                                         :conduit.client-root/path  "/home"}
                                                        {:conduit.client-root/label "New Article"
                                                         :conduit.client-root/path  "/editor"}
                                                        {:conduit.client-root/label "Settings"
                                                         :conduit.client-root/path  "/settings"}
                                                        {:conduit.client-root/label username
                                                         :conduit.client-root/path  href}]
                       :conduit.redirect/path          "/home"}
                      (catch Throwable ex
                        {:conduit.client-root/top-routes [{:conduit.client-root/label "Home"
                                                           :conduit.client-root/path  "/home"}
                                                          {:conduit.client-root/label "Sign up"
                                                           :conduit.client-root/path  "/register"}
                                                          {:conduit.client-root/label "Sign in"
                                                           :conduit.client-root/path  "/login"}]
                         :conduit.client-root/errors     [{:conduit.error/message "wrong password"}]})))))
   (pc/constantly-resolver :conduit.client-root/waiting? false)
   (pc/resolver `current-user
                {::pc/output [:conduit.client-root/my-profile]}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} _]))
   (pc/resolver `profile
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/bio
                              :conduit.profile/image
                              :conduit.profile/following]}
                (fn [{::keys [db]} {:conduit.profile/keys [username]}]
                  (ds/pull db '[*]
                           [:conduit.profile/username username])))
   (pc/resolver `profile/articles
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/article-count
                              :conduit.profile/articles]}
                (fn [ctx {:conduit.profile/keys [username]}]))
   (pc/resolver `fav-articles
                {::pc/input  #{:conduit.profile/articles}
                 ::pc/output [:conduit.profile/favorites-articles]}
                (fn [_ {:conduit.profile/keys [articles]}]))
   (pc/resolver `popular-tags
                {::pc/output [:conduit.client-root/popular-tags]}
                (fn [ctx _]))

   (pc/resolver `article-pull
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/title
                              :conduit.article/created-at
                              :conduit.article/description
                              :conduit.article/tag-list
                              :conduit.article/href]}
                (fn [{::keys [db]} {:conduit.article/keys [slug]}]
                  (ds/pull db '[{:conduit.article/tag-list [*]}
                                *]
                           [:conduit.article/slug slug])))
   (pc/resolver `article-author
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.profile/username]}
                (fn [{::keys [db]} {:conduit.article/keys [slug]}]
                  (-> (ds/pull db '[{:conduit.article/author [*]}]
                               [:conduit.article/slug slug])
                      :conduit.article/author)))
   (pc/resolver `article-favorites-count
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/favorites-count]}
                (fn [{::keys [db]} {:conduit.article/keys [slug]}]
                  {:conduit.article/favorites-count (ds/q '[:find (count ?profile) .
                                                            :in $ ?slug
                                                            :where
                                                            [?article :conduit.article/slug ?slug]
                                                            [?profile :conduit.profile/favorites-articles ?article]]
                                                          db slug)}))
   (pc/resolver `articles
                {::pc/output [:conduit.client-root/articles
                              :conduit.client-root/articles-count]}
                (fn [{::keys [db]} _]
                  (let [articles (map (partial zipmap [:conduit.article/slug])
                                      (ds/q '[:find ?slug
                                              ;; :keys :conduit.article/slug
                                              :where
                                              [_ :conduit.article/slug ?slug]]
                                            db))]
                    {:conduit.client-root/articles       articles
                     :conduit.client-root/articles-count (count articles)})))])
