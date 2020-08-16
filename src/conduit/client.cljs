(ns conduit.client
  (:require
    [clojure.core.async :as async]
    [clojure.core.async.interop :refer [<p!]]
    [clojure.string :as string]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as ftx]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.rad.application :as rad-app]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [conduit.article :as article]
    [conduit.tag :as tag]
    [conduit.ui :as ui]
    [conduit.feed-button :as feed-button]
    [conduit.profile :as profile]
    [goog.object :as gobj]
    [conduit.storage :as storage]
    [edn-query-language.core :as eql]
    [goog.events :as events]
    [goog.history.EventType :as et])
  (:import (goog.history Html5History)))
;; TODO: Create a lib for "pathom remote"
(defn transmit!
  [{:keys [parser]
    :as   env} {::ftx/keys [; id idx options update-handler active?
                            result-handler ast]}]
  (let [query (eql/ast->query ast)
        result (parser env query)]
    (async/go
      (result-handler {:body                 (async/<! result)
                       :original-transaction ast
                       :status-code          200}))))

(defn push!
  [app path]
  (let [{::keys [^Html5History history]} (comp/shared app)]
    (.setToken history (subs path 1))))

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
                                                                 (push! this "#/login"))}))))
        (dom/div
          {:className "col-md-3"}
          (dom/div
            {:className "sidebar"}
            (dom/p "Popular tags")
            (dom/div
              {:className "tag-list"}
              (map ui/ui-tag-pill popular-tags))))))))

(defsc Article [this {:>/keys        [article-meta]
                      ::profile/keys [username image]
                      ::article/keys [created-at comments body title favorites-count]}]
  {:query         [::article/body
                   ::profile/username
                   {::article/comments (comp/get-query ui/Comment)}
                   ::profile/image
                   {:>/article-meta (comp/get-query ui/ArticleMeta)}
                   ::article/slug
                   ::article/created-at
                   ::article/favorites-count

                   ::article/title]
   :ident         ::article/slug
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
          (ui/form {::ui/attributes   [:conduit.comment/body]
                    ::ui/submit-label "Post Comment"})
          (map ui/ui-comment comments))))))

(defsc Redirect [this {:conduit.redirect/keys [path]}]
  {:query [:conduit.redirect/path]}
  (let [{::keys [^Html5History history]} (comp/shared this)]
    (dom/button
      {:onClick #(push! this path)}
      (str "Redirect: '" path "'"))))

(def ui-redirect (comp/factory Redirect))

(defsc SignIn [this {::keys [waiting? errors redirect]}]
  {:ident         (fn [] [::session ::my-profile])
   :query         [::waiting?
                   {::errors (comp/get-query ui/ErrorMessage)}
                   {::redirect (comp/get-query Redirect)}]
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
          (when redirect
            (ui-redirect redirect))
          (ui/form
            {::ui/on-submit    (when-not waiting?
                                 (fn [params]
                                   (comp/transact! this `[(conduit.profile/login ~params)])))
             ::ui/attributes   [::profile/email
                                ::profile/password]
             ::ui/labels       {::profile/email    "Email"
                                ::profile/password "Password"}
             ::ui/submit-label (if waiting?
                                 "Signing in ..."
                                 "Sign in")
             ::ui/types        {::profile/password "password"}}))))))

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
              (m/returning SignIn))))

(defsc SignUp [this {::keys [waiting? errors redirect]}]
  {:ident         (fn [] [::session ::my-profile])
   :query         [::waiting?
                   {::errors (comp/get-query ui/ErrorMessage)}
                   {::redirect (comp/get-query Redirect)}]
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
          (when redirect
            (ui-redirect redirect))
          (ui/form
            {::ui/on-submit    (when-not waiting?
                                 (fn [params]
                                   (comp/transact! this `[(conduit.profile/register ~params)])))
             ::ui/attributes   [::profile/username
                                ::profile/email
                                ::profile/password]
             ::ui/labels       {::profile/username "Your Name"
                                ::profile/email    "Email"
                                ::profile/password "Password"}
             ::ui/submit-label (if waiting?
                                 "Signing up ..."
                                 "Sign up")
             ::ui/types        {::profile/password "password"}}))))))

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
              (m/returning SignIn))))


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
            {::ui/attributes [::article/title
                              ::article/description
                              ::article/body
                              ::article/tags]}))))))

(defsc Settings [this {:conduit.profile/keys [image username bio email]
                       :as                   props}]
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
             ::ui/attributes     [::profile/image
                                  ::profile/username
                                  ::profile/bio
                                  ::profile/email
                                  ::profile/password]}))))))

(defsc Profile [this {:>/keys               [user-info]
                      :conduit.profile/keys [articles]}]
  {:query         [:conduit.profile/username
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
                                    {::ui/on-follow (fn []
                                                      (push! this "#/login"))}))
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
  {:router-targets [Home SignIn SignUp Article NewPost Settings Profile]}
  (case current-state
    :pending (dom/div "Loading...")
    :failed (dom/div "Loading seems to have failed. Try another route.")
    (dom/div "Unknown route")))

(def ui-top-router (comp/factory TopRouter))

(defsc Header [this {::keys [top-routes]}]
  {:query [::dr/current-route
           ::top-routes]
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
        ". Code & design licensed under MIT."))))

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

(defn client-did-mount
  "Must be used as :client-did-mount parameter of app creation, or called just after you mount the app."
  [app]
  (let [{::keys [history]} (comp/shared app)]
    (doto history
      (events/listen et/NAVIGATE (fn [^goog.history.Event e]
                                   (let [token (.-token e)
                                         path (vec (rest (string/split (first (string/split token #"\?"))
                                                                       #"/")))]
                                     (dr/change-route! app path)
                                     (df/load! app :>/header Header))))
      (.setEnabled true))))

(defn fetch
  [{::keys [authed-user
            api-url]} {::keys [path method body]
                       :or    {method "GET"}}]
  (let [authorization (some-> authed-user
                              deref
                              ::profile/token
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
           username]}]
  (into {}
        (remove (comp nil? val))
        #:conduit.profile{:bio       bio
                          :following following
                          :image     image
                          :token     token
                          :username  username}))

(defn qualify-article
  [{:strs [title slug body createdAt updatedAt tagList description author favorited favoritesCount]}]
  (let [profile (when author
                  (qualify-profile author))]
    (into {}
          (remove (comp nil? val))
          (merge profile
                 (when author
                   {::article/author profile})
                 {::article/title           title
                  ::article/created-at      (new js/Date createdAt)
                  ::article/slug            slug
                  ::article/updated-at      updatedAt
                  ::article/description     description
                  ::article/favorited?      favorited
                  ::article/favorites-count favoritesCount
                  ::article/tag-list        (for [tag tagList]
                                              {::tag/tag tag})

                  ::article/body            body}))))

(def register
  [(pc/constantly-resolver
     ::feed-toggle [{::feed-button/label "Your Feed"
                     ::feed-button/href  (str "#/home")}
                    {::feed-button/label "Global Feed"
                     ::feed-button/href  (str "#/home")}])
   (pc/resolver `top-routes
                {::pc/output [::top-routes]}
                (fn [{::keys [authed-user]} _]
                  (let [{::profile/keys [username image]} @authed-user]
                    {::top-routes (if username
                                    [{::label "Home"
                                      ::path  "#/home"}
                                     {::label "New Post"
                                      ::icon  "ion-compose"
                                      ::path  "#/editor"}
                                     {::label "Settings"
                                      ::icon  "ion-gear-a"
                                      ::path  (str "#/settings")}
                                     {::label username
                                      ::img   image
                                      ::path  (str "#/profile/" username)}]
                                    [{::label "Home"
                                      ::path  "#/home"}
                                     {::label "Sign up"
                                      ::path  "#/register"}
                                     {::label "Sign in"
                                      ::path  "#/login"}])})))
   (pc/mutation `conduit.profile/login
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email]
                 ::pc/output []}
                (fn [{::keys [authed-user]
                      :as    env} {:conduit.profile/keys [email password]}]
                  (let [body #js {:user #js{:email    email
                                            :password password}}]
                    (async/go
                      (let [response (async/<! (fetch env {::path   "/users/login"
                                                           ::method "POST"
                                                           ::body   (js/JSON.stringify body)}))
                            {:strs [errors user]} (js->clj response)
                            my-profile (assoc (qualify-profile user)
                                         ::profile/email email)]
                        (when-not (empty? user)
                          (reset! authed-user my-profile))
                        (cond-> my-profile
                                errors (assoc ::errors (for [[k vs] errors
                                                             v vs]
                                                         {:conduit.error/id      (str (gensym "conduit.error"))
                                                          :conduit.error/message (str k ": " v)}))
                                (empty? errors) (assoc ::redirect {:conduit.redirect/path "#/home"})))))))
   (pc/mutation `conduit.profile/register
                {::pc/params [:conduit.profile/password
                              :conduit.profile/email
                              :conduit.profile/username]
                 ::pc/output []}
                (fn [{::keys [authed-user]
                      :as    env} {:conduit.profile/keys [email password username]}]
                  (let [body #js {:user #js{:email    email
                                            :username username
                                            :password password}}]
                    (async/go
                      (let [response (async/<! (fetch env {::path   "/users"
                                                           ::method "POST"
                                                           ::body   (js/JSON.stringify body)}))
                            {:strs [errors user]} (js->clj response)
                            my-profile (assoc (qualify-profile user)
                                         ::profile/email email)]
                        (when-not (empty? user)
                          (reset! authed-user my-profile))
                        (cond-> my-profile
                                errors (assoc ::errors (for [[k vs] errors
                                                             v vs]
                                                         {:conduit.error/id      (str (gensym "conduit.error"))
                                                          :conduit.error/message (str k ": " v)}))
                                (empty? errors) (assoc ::redirect {:conduit.redirect/path "#/home"})))))))
   (pc/constantly-resolver ::waiting? false)
   (pc/resolver `session-image
                {::pc/output [:conduit.session/image]}
                (fn [{::keys [authed-user]}]
                  (some-> authed-user
                          deref
                          (get "image")
                          (->> (hash-map :conduit.session/image)))))

   (pc/resolver `article
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [::article/body
                              ::profile/image
                              ::article/created-at
                              ::profile/username
                              ::article/title]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles/" slug)}))
                          {:strs [article]} (js->clj result)]
                      (qualify-article article)))))
   (pc/resolver `slug->href
                {::pc/input  #{::article/slug}
                 ::pc/output [::article/href]}
                (fn [_ {::article/keys [slug]}]
                  {::article/href (str "#/article/" slug)}))
   (pc/resolver `username->href
                {::pc/input  #{::profile/username}
                 ::pc/output [::profile/href]}
                (fn [_ {::profile/keys [username]}]
                  {::profile/href (str "#/profile/" username)}))
   (pc/resolver `comments
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [::article/comments]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles/" slug "/comments")}))
                          {:strs [comments]} (js->clj result)]
                      {::article/comments (for [{:strs [id body createdAt author updatedAt]} comments
                                                :let [profile (qualify-profile author)]]
                                            (merge
                                              profile
                                              {:conduit.comment/id         id
                                               :conduit.comment/body       body
                                               :conduit.comment/author     author
                                               :conduit.comment/created-at (new js/Date createdAt)
                                               :conduit.comment/updated-at (new js/Date updatedAt)}))}))))
   (pc/resolver `current-user
                {::pc/output [::my-profile]}
                (fn [{::keys [authed-user]
                      :as    env} _]
                  (async/go
                    (let [result (async/<! (fetch env {::path (str "/user")}))
                          {:strs [user]} (js->clj result)]
                      {::my-profile (swap! authed-user merge (qualify-profile user))}))))
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
                 ::pc/output [::profile/article-count
                              ::profile/articles]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles?author=" username "&limit=5&offset=0")}))
                          {:strs [articles articlesCount]} (js->clj result)]
                      {::profile/article-count articlesCount
                       ::profile/articles      (map qualify-article articles)}))))

   (pc/resolver `popular-tags
                {::pc/output [::popular-tags]}
                (fn [ctx _]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path "/tags"}))
                          {:strs [tags]} (js->clj result)]
                      {::popular-tags (for [tag tags]
                                        {:conduit.tag/tag tag})}))))
   (pc/resolver `articles
                {::pc/output [:conduit.client/articles]}
                (fn [ctx _]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path "/articles"}))
                          {:strs [articlesCount
                                  articles]} (js->clj result)]
                      {::articles-count articlesCount
                       ::articles       (for [article articles]
                                          (qualify-article article))}))))])

(def parser
  (p/parallel-parser
    {::p/plugins [(pc/connect-plugin {::pc/register (concat register
                                                            [pc/index-explorer-resolver])})
                  p/elide-special-outputs-plugin]
     ::p/mutate  pc/mutate-async}))

(def remote
  {:transmit!               transmit!
   :parser                  parser
   ::authed-user            (storage/atom {::storage/storage  js/localStorage
                                           ::storage/type     :json
                                           ::storage/key-name "conduit.client.authed-user"})
   ::api-url                "https://conduit.productionready.io/api"
   ::p/reader               [p/map-reader
                             pc/parallel-reader
                             pc/open-ident-reader
                             p/env-placeholder-reader]
   ::p/placeholder-prefixes #{">"}})

(defonce app (rad-app/fulcro-rad-app {:client-did-mount client-did-mount
                                      :shared           {::history (Html5History.)}
                                      :remotes          {:remote remote}}))

(def node "conduit")

(defn ^:export init-fn
  []
  (app/mount! app Root node))
