(ns conduit.client-root
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            #?@(:cljs    [[goog.history.EventType :as et]
                          [goog.events :as events]
                          [com.fulcrologic.fulcro.dom :as dom]]
                :default [[com.fulcrologic.fulcro.dom-server :as dom]])
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
            [conduit.ui :as ui]
            [clojure.string :as string])
  #?@(:cljs    [(:import (goog.history Html5History Event))]
      :default []))

(defn push!
  [app path]
  #?(:cljs (let [{::keys [^Html5History history]} (comp/shared app)]
             (.setToken history path))))

(defsc Home [this {::keys [articles
                           feed-toggle
                           popular-tags]}]
  {:query         [{::feed-toggle (comp/get-query ui/FeedButton)}
                   {::articles (comp/get-query ui/ArticlePreview)}
                   {::popular-tags (comp/get-query ui/TagPill)}]
   :ident         (fn []
                    [:component/id ::home])
   :route-segment ["home"]
   :will-enter    (fn [app _]
                    (dr/route-deferred [:component/id ::home]
                                       #(df/load! app [:component/id ::home] Home
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:component/id ::home]}})))}
  (dom/div
    {:className "home-page"}
    (ui/banner)
    (dom/div
      {:className "container page"}
      (dom/div
        {:className "row"}
        (dom/div
          {:className "col-md-9"}
          (dom/div
            {:className "feed-toggle"}
            (dom/ul
              {:className "nav nav-pills outline-active"}
              (map ui/ui-feed-button feed-toggle)))
          (for [article articles]
            (ui/ui-article-preview (comp/computed article
                                                  {::ui/on-fav (fn []
                                                                 (push! this "/login"))}))))
        (dom/div
          {:className "col-md-3"}
          (dom/div
            {:className "sidebar"}
            (dom/p "Popular tags")
            (dom/div
              {:className "tag-list"}
              (map ui/ui-tag-pill popular-tags))))))))

(def ui-home (comp/factory Home))

(defsc Article [this {:>/keys               [article-meta]
                      :conduit.article/keys [comments body title]}]
  {:query         [:conduit.article/body
                   {:conduit.article/comments (comp/get-query ui/Comment)}
                   {:>/article-meta (comp/get-query ui/ArticleMeta)}
                   :conduit.article/slug
                   :conduit.article/title]
   :ident         :conduit.article/slug
   :route-segment ["article" :conduit.article/slug]
   :will-enter    (fn [app {:conduit.article/keys [slug]}]
                    (dr/route-deferred [:conduit.article/slug slug]
                                       #(df/load! app [:conduit.article/slug slug] Article
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:conduit.article/slug slug]}})))}
  (dom/div
    :.article-page
    (dom/div
      :.banner
      (dom/div
        :.container
        (dom/h1 title)
        (ui/ui-article-meta article-meta)))
    (dom/div
      :.container.page
      (dom/div
        :.row.article-content
        (dom/section
          {:className "col-md-12"}
          (ui/markdown body)))
      (dom/hr)
      (dom/div
        :.article-actions
        (ui/ui-article-meta article-meta))
      (dom/div
        :.row
        (dom/div
          :.col-xs-12.col-md-8.offset-md-2
          (dom/form
            {:className "card comment-form"}
            (dom/div
              {:className "card-block"}
              (dom/textarea
                {:className   "form-control"
                 :placeholder "Write a comment ..."
                 :rows        3})
              (dom/div
                {:className "card-footer"}
                (dom/img {:src "" #_my-image
                          :alt "my profile image"})
                (dom/button
                  {:className "btn btn-sm btn-primary"}
                  "Post Comment"))))
          (map ui/ui-comment comments))))))

(defsc SignIn [this {::keys [waiting? errors]}]
  {:ident         (fn [] [::session ::my-profile])
   :query         [::waiting?
                   {::errors (comp/get-query ui/ErrorMessage)}]
   :will-enter    (fn [app _]
                    (dr/route-deferred [::session ::my-profile]
                                       #(df/load! app [::session ::my-profile] SignIn
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [::session ::my-profile]}})))
   :route-segment ["login"]}
  (dom/div
    {:className "auth-page"}
    (dom/div
      {:className "container page"}
      (dom/div
        {:className "row"}
        (dom/div
          {:className "col-md-6 offset-md-3 col-xs-12"}
          (dom/h1
            {:className "text-xs-center"}
            "Sign in")
          (dom/p
            {:className "text-xs-center"}
            (dom/a
              {:href "#/register"}
              "Need an account?"))
          (dom/ul
            {:className "error-messages"}
            (for [error errors
                  :let [on-remove (fn []
                                    (m/set-value! this
                                                  ::errors (remove #{error} errors)))]]
              (ui/ui-error-message (comp/computed error
                                                  {::ui/on-remove on-remove}))))
          (ui/form
            {::ui/on-submit    (when-not waiting?
                                 (fn [params]
                                   (comp/transact! this `[(conduit.profile/login ~params)])))
             ::ui/attributes   [:conduit.profile/email
                                :conduit.profile/password]
             ::ui/labels       {:conduit.profile/email    "Email"
                                :conduit.profile/password "Password"}
             ::ui/large        #{:conduit.profile/email
                                 :conduit.profile/password}
             ::ui/submit-label (if waiting?
                                 "Signing in ..."
                                 "Sign in")
             ::ui/types        {:conduit.profile/password "password"}}))))))

(defsc SignUp [this {::keys [waiting? errors]}]
  {:ident         (fn [] [::session ::my-profile])
   :query         [::waiting?
                   {::errors (comp/get-query ui/ErrorMessage)}]
   :will-enter    (fn [app _]
                    (dr/route-deferred [::session ::my-profile]
                                       #(df/load! app [::session ::my-profile] SignUp
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [::session ::my-profile]}})))
   :route-segment ["register"]}
  (dom/div
    {:className "auth-page"}
    (dom/div
      {:className "container page"}
      (dom/div
        {:className "row"}
        (dom/div
          {:className "col-md-6 offset-md-3 col-xs-12"}
          (dom/h1
            {:className "text-xs-center"}
            "Sign up")
          (dom/p
            {:className "text-xs-center"}
            (dom/a
              {:href "#/login"}
              "Have an account?"))
          (dom/ul
            {:className "error-messages"}
            (for [error errors
                  :let [on-remove (fn []
                                    (m/set-value! this
                                                  ::errors (remove #{error} errors)))]]
              (ui/ui-error-message (comp/computed error
                                                  {::ui/on-remove on-remove}))))
          (ui/form
            {::ui/on-submit    (when-not waiting?
                                 (fn [params]
                                   (comp/transact! this `[(conduit.profile/register ~params)])))
             ::ui/attributes   [:conduit.profile/username
                                :conduit.profile/email
                                :conduit.profile/password]
             ::ui/labels       {:conduit.profile/username "Your Name"
                                :conduit.profile/email    "Email"
                                :conduit.profile/password "Password"}
             ::ui/large        #{:conduit.profile/email
                                 :conduit.profile/username
                                 :conduit.profile/password}
             ::ui/submit-label (if waiting?
                                 "Signing up ..."
                                 "Sign up")
             ::ui/types        {:conduit.profile/password "password"}}))))))

(defsc NewPost [this props]
  {:ident         (fn []
                    [:component/id ::new-post])
   :query         []
   :route-segment ["editor"]}
  (dom/div
    :.editor-page
    (dom/div
      :.container.page
      (dom/div
        :.row
        (dom/div
          :.col-md-10.offset-md-1.col-xs-12
          (ui/form
            {::ui/attributes   [:conduit.article/title
                                :conduit.article/description
                                :conduit.article/body
                                :conduit.article/tags]
             ::ui/multiline    #{:conduit.article/body}
             ::ui/large        #{:conduit.article/title}
             ::ui/labels       {:conduit.article/title       "Article Title"
                                :conduit.article/description "What's this article about?"
                                :conduit.article/body        "Write your article (in markdown)"
                                :conduit.article/tags        "Enter tags"}
             ::ui/on-submit    (fn [params]
                                 (prn params))
             ::ui/submit-label "Publish Article"}))))))

(defsc Settings [this props]
  {:ident         (fn [] [::session ::my-profile])
   :query         [:conduit.profile/bio
                   :conduit.profile/username
                   :conduit.profile/email
                   :conduit.profile/image]
   :route-segment ["settings"]
   :will-enter    (fn [app _]
                    (dr/route-deferred [::session ::my-profile]
                                       #(df/load! app ::my-profile Settings
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [::session ::my-profile]}})))}
  (dom/div
    :.settings-page
    (dom/div
      :.container.page
      (dom/div
        :.row
        (dom/div
          :.col-md-6.offset-md-3.col-xs-12
          (dom/h1 :.text-xs-center
                  "Your Settings")
          (ui/form
            {::ui/default-values props
             ::ui/on-submit      (fn [props]
                                   (prn props))
             ::ui/large          #{:conduit.profile/username
                                   :conduit.profile/email
                                   :conduit.profile/bio
                                   :conduit.profile/password}
             ::ui/multiline      #{:conduit.profile/bio}
             ::ui/attributes     [:conduit.profile/image
                                  :conduit.profile/username
                                  :conduit.profile/bio
                                  :conduit.profile/email
                                  :conduit.profile/password]
             ::ui/submit-label   "Update Settings"}))))))

(defsc ProfileFavorites [this {:>/keys               [user-info]
                               :conduit.profile/keys [favorites-articles me?]}]
  {:query         [:conduit.profile/username
                   :conduit.profile/me?
                   {:>/user-info (comp/get-query ui/UserInfo)}
                   {:conduit.profile/favorites-articles (comp/get-query ui/ArticlePreview)}]
   :ident         :conduit.profile/username
   :route-segment ["profile" :conduit.profile/username "favorites"]
   :will-enter    (fn [app {:conduit.profile/keys [username]}]
                    (dr/route-deferred [:conduit.profile/username username]
                                       #(df/load! app [:conduit.profile/username username] ProfileFavorites
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:conduit.profile/username username]}})))}
  (dom/div
    :.profile-page
    (ui/ui-user-info (comp/computed user-info
                                    (if me?
                                      {::ui/on-edit (fn []
                                                      (push! this "/settings"))}
                                      {::ui/on-follow (fn []
                                                        (push! this "/login"))})))
    (dom/div
      :.container
      (dom/div
        :.row
        (dom/div
          :.col-xs-12.col-md-10.offset-md-1
          (dom/div
            :.articles-toggle
            (dom/ul
              :.nav.nav-pills.outline-active
              (dom/li
                :.nav-item
                (dom/a :.nav-link.active {:href "#"} "My Articles"))
              (dom/li
                :.nav-item
                (dom/a :.nav-link {} "Favorited Articles"))))
          (for [article favorites-articles]
            (ui/ui-article-preview (comp/computed article
                                                  {::ui/on-fav (fn [])}))))))))


(defsc Profile [this {:>/keys               [user-info]
                      :conduit.profile/keys [articles me?]}]
  {:query         [:conduit.profile/username
                   :conduit.profile/me?
                   {:>/user-info (comp/get-query ui/UserInfo)}
                   {:conduit.profile/articles (comp/get-query ui/ArticlePreview)}]
   :ident         :conduit.profile/username
   :route-segment ["profile" :conduit.profile/username]
   :will-enter    (fn [app {:conduit.profile/keys [username]}]
                    (dr/route-deferred [:conduit.profile/username username]
                                       #(df/load! app [:conduit.profile/username username] Profile
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:conduit.profile/username username]}})))}
  (dom/div
    :.profile-page
    (ui/ui-user-info (comp/computed user-info
                                    (if me?
                                      {::ui/on-edit (fn []
                                                      (push! this "/settings"))}
                                      {::ui/on-follow (fn []
                                                        (push! this "/login"))})))
    (dom/div
      :.container
      (dom/div
        :.row
        (dom/div
          :.col-xs-12.col-md-10.offset-md-1
          (dom/div
            :.articles-toggle
            (dom/ul
              :.nav.nav-pills.outline-active
              (dom/li
                :.nav-item
                (dom/a :.nav-link.active {:href "#"} "My Articles"))
              (dom/li
                :.nav-item
                (dom/a :.nav-link {} "Favorited Articles"))))
          (for [article articles]
            (ui/ui-article-preview (comp/computed article
                                                  {::ui/on-fav (fn [])}))))))))

(defrouter TopRouter [this {:keys [current-state]}]
  {:router-targets [Home SignIn ProfileFavorites SignUp Article NewPost Settings Profile]}
  (case current-state
    :pending (dom/div "Loading...")
    :failed (dom/div "Loading seems to have failed. Try another route.")
    (dom/div "Unknown route")))

(def ui-top-router (comp/factory TopRouter))

(defsc Header [this {:conduit.redirect/keys [path]
                     ::keys                 [top-routes]}]
  {:query [::dr/current-route
           ::top-routes
           :conduit.redirect/path]
   :ident (fn []
            [::dr/id ::TopRouter])}
  (let [current-route (dr/current-route this)]
    (dom/nav
      {:className "navbar navbar-light"}
      (dom/div
        {:className "container"}
        (dom/a {:className "navbar-brand"
                :href      "#/home"}
               "conduit")
        (when path
          (dom/button
            {:onClick #(push! this path)}
            (str "Redirect: '" path "'")))
        (dom/ul
          {:className "nav navbar-nav pull-xs-right"}
          (for [{::keys [label icon img path]} top-routes]
            (dom/li
              {:key       label
               :className "nav-item"}
              (dom/a
                {:href    path
                 :classes ["nav-link" (when (= current-route path)
                                        "active")]}
                (when icon
                  (dom/i {:className icon}))
                (when img
                  (dom/img {:className "user-pic"
                            :src       img}))
                label))))))))

(def ui-header (comp/factory Header))


#?(:cljs    (goog-define VERSION "develop")
   :default (def VERSION "develop"))

(defsc Footer [this {::keys []}]
  {:query []}
  (dom/footer
    (dom/div
      {:className "container"}
      (dom/a {:className "logo-font"
              :href      "#/home"}
             "conduit")
      (dom/span
        {:className "attribution"}
        "An interactive learning project from "
        (dom/a {:href "https://thinkster.io"} "Thinkster")
        ". Code & design licensed under MIT."))
    (dom/div
      {:className "container"}
      (dom/code
        (str "v: " VERSION)))))

(def ui-footer (comp/factory Footer))

(defsc Root [this {:>/keys [footer header router]}]
  {:query         [{:>/header (comp/get-query Header)}
                   {:>/router (comp/get-query TopRouter)}
                   {:>/footer (comp/get-query Footer)}]
   :initial-state (fn [_]
                    {:>/header (comp/get-initial-state Header _)
                     :>/router (comp/get-initial-state TopRouter _)
                     :>/footer (comp/get-initial-state Footer _)})}
  (comp/fragment
    (ui-header header)
    (ui-top-router router)
    (ui-footer footer)))

(def ui-root (comp/factory Root))

(defsc AuthReturn [_ _]
  {:query [{:>/header (comp/get-query Header)}
           {:>/sign-in (comp/get-query SignIn)}
           {:>/sign-up (comp/get-query SignUp)}]})

(m/defmutation conduit.profile/register
  [{:conduit.profile/keys [email password]}]
  (action [{:keys [ref state] :as env}]
          (swap! state (fn [st]
                         (-> st
                             (update-in ref assoc
                                        ::errors []
                                        ::waiting? true)))))
  (remote [env]
          (-> env
              (m/returning AuthReturn))))

(m/defmutation conduit.profile/login
  [{:conduit.profile/keys [email password]}]
  (action [{:keys [ref state] :as env}]
          (swap! state (fn [st]
                         (-> st
                             (update-in ref assoc
                                        ::errors []
                                        ::waiting? true)))))
  (remote [env]
          (-> env
              (m/returning AuthReturn))))


(defn client-did-mount
  "Must be used as :client-did-mount parameter of app creation, or called just after you mount the app."
  [app]
  (let [{::keys [history]} (comp/shared app)]
    (doto history
      #?(:cljs (events/listen et/NAVIGATE (fn [^Event e]
                                            (let [token (.-token e)
                                                  path (vec (rest (string/split (first (string/split token #"\?"))
                                                                                #"/")))]
                                              (dr/change-route! app path)
                                              (df/load! app :>/header Header)))))
      (.setEnabled true))))
