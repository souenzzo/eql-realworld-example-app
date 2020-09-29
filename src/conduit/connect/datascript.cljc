(ns conduit.connect.datascript
  (:require [datascript.core :as ds]
            [com.wsscode.pathom.connect :as pc]))

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
                      :as                       env} {:conduit.profile/keys [email password]}]))
   #_(pc/constantly-resolver :conduit.redirect/path nil)
   (pc/mutation `conduit.profile/register
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email
                              :conduit.profile/username]
                 ::pc/output []}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} {:conduit.profile/keys [email password username]}]))
   (pc/constantly-resolver :conduit.client-root/waiting? false)
   (pc/resolver `article
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/body
                              :conduit.profile/image
                              :conduit.article/created-at
                              :conduit.profile/username
                              :conduit.article/title]}
                (fn [ctx {:conduit.article/keys [slug]}]))
   (pc/resolver `slug->href
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/href]}
                (fn [_ {:conduit.article/keys [slug]}]))
   (pc/resolver `username->href
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/href]}
                (fn [_ {:conduit.profile/keys [username]}]))
   (pc/resolver `comments
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [:conduit.article/comments]}
                (fn [ctx {:conduit.article/keys [slug]}]))
   (pc/resolver `current-user
                {::pc/output [:conduit.client-root/my-profile]}
                (fn [{:conduit.client-root/keys [jwt]
                      :as                       env} _]))
   (pc/resolver `profile
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/bio
                              :conduit.profile/image
                              :conduit.profile/following]}
                (fn [ctx {:conduit.profile/keys [username]}]))
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
   (pc/resolver `articles
                {::pc/output [:conduit.client-root/articles]}
                (fn [ctx _]))])
