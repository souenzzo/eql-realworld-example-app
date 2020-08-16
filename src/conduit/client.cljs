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
    [conduit.local-storage :as ls]
    [edn-query-language.core :as eql]
    [goog.events :as events]
    [goog.history.EventType :as et]
    [taoensso.timbre :as log])
  (:import (goog.history Html5History)))
;; TODO: Create a lib for "pathom remote"
(defn transmit!
  [{:keys [parser]
    :as   env} {::ftx/keys [; id idx options update-handler active?
                            result-handler ast]}]
  (let [query (eql/ast->query ast)
        result (parser env query)]
    (log/info :query query)
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
          (map ui/ui-tag-pill popular-tags))))))

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


(defsc ErrorMessage [this {:conduit.error/keys [message hidden?]}]
  {:query [:conduit.error/id
           :conduit.error/hidden?
           :conduit.error/message]
   :ident :conduit.error/id}
  (dom/li {:onClick #(m/set-value! this :conduit.error/hidden? true)
           :hidden  hidden?}
          message))

(def ui-error-message (comp/factory ErrorMessage {:keyfn :conduit.error/id}))

(defsc Redirect [this {:conduit.redirect/keys [path]}]
  {:query [:conduit.redirect/path]}
  (let [{::keys [^Html5History history]} (comp/shared this)]
    (dom/button
      {:onClick #(push! this path)}
      (str "Redirect: '" path "'"))))

(def ui-redirect (comp/factory Redirect))

(defsc SignIn [this {::keys [waiting?
                             errors
                             redirect]
                     :as    props}]
  {:ident         (fn [] [:component/id ::sign-in])
   :query         [::waiting?
                   :conduit.profile/username
                   :conduit.profile/email
                   {::errors (comp/get-query ErrorMessage)}
                   {::redirect (comp/get-query Redirect)}]
   :will-enter    (fn [app _]
                    (dr/route-deferred [:component/id ::sign-in]
                                       #(df/load! app [:component/id ::sign-in] SignIn
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:component/id ::sign-in]}})))
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
            (map ui-error-message errors))
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

(defsc SignUp [this {::keys [loading? errors]}]
  {:ident         (fn [] [:component/id ::sign-up])
   :query         [::loading?
                   :conduit.profile/username
                   :conduit.profile/email
                   ::top-routes
                   {::errors (comp/get-query ErrorMessage)}
                   {:conduit.profile.login/redirect (comp/get-query Redirect)}]
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
            (map ui-error-message errors))
          (ui/form
            {::ui/on-submit    (when-not loading?
                                 (fn [params]
                                   (comp/transact! this `[(conduit.profile/login ~params)])))
             ::ui/attributes   [::profile/username
                                ::profile/email
                                ::profile/password]
             ::ui/labels       {::profile/username "Your Name"
                                ::profile/email    "Email"
                                ::profile/password "Password"}
             ::ui/submit-label (if loading?
                                 "loading ..."
                                 "Sign up")
             ::ui/types        {::profile/password "password"}}))))))


(m/defmutation conduit.profile/login
  [{:conduit.profile/keys [email password]}]
  (action [{:keys [ref state] :as env}]
          (swap! state (fn [st]
                         (-> st
                             (update-in ref assoc ::waiting? true)))))
  (remote [env]
          (-> env
              (m/returning SignIn)))
  (ok-action [{:keys [state result]}]
             (swap! state (fn [st]
                            (-> st
                                (assoc-in [:component/id
                                           ::settings
                                           ::me]
                                          (-> result
                                              :body
                                              (get `conduit.profile/login)
                                              (find :conduit.profile/username))))))))

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

(defsc Settings [this {:conduit.profile/keys [image username bio email]}]
  {:ident         :conduit.profile/username
   :query         [:conduit.profile/bio
                   :conduit.profile/username
                   :conduit.profile/email
                   :conduit.profile/image]
   :route-segment ["settings" :conduit.profile/username]
   :will-enter    (fn [app {:conduit.profile/keys [username]}]
                    (dr/route-deferred [:conduit.profile/username username]
                                       #(df/load! app [:conduit.profile/username username] Settings
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:conduit.profile/username username]}})))}
  (dom/div
    :.settings-page
    (dom/div
      :.container.page
      (dom/div
        :.row
        (dom/div
          :.col-md-6.offset-md-3.col-xs-12
          (dom/h1 :.text-xs-center "Your Settings")
          (ui/form
            {::ui/attributes [::profile/image
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
    (ui/ui-user-info user-info)
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
                (dom/a :.nav-link.active {:href ""} "My Articles"))
              (dom/li
                :.nav-item)
              (dom/a :.nav-link {:href ""} "Favorited Articles")))
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
            [::dr/id :conduit.client/TopRouter])}
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
  [{::keys [api-url]} {::keys [path method body]}]
  (let [opts (when body
               #js {:method  method
                    :headers #js{"Content-Type" "application/json"}
                    :body    body})]
    (async/go
      (-> (js/fetch (str api-url path) opts)
          <p!
          .json
          <p!))))

(defn qualify-profile
  [{:strs [bio
           following
           image
           username]}]
  (into {}
        (remove (comp nil? val))
        #:conduit.profile{:bio       bio
                          :following following
                          :image     image
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
                  (let [{:strs [username image]} @authed-user]
                    {::top-routes (if username
                                    [{::label "Home"
                                      ::path  "#/home"}
                                     {::label "New Post"
                                      ::icon "ion-compose"
                                      ::path  "#/editor"}
                                     {::label "Settings"
                                      ::icon "ion-gear-a"
                                      ::path  (str "#/settings/" username)}
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
                            {:strs [username]} user]
                        (when-not (empty? user)
                          (ls/set! ::authed-user (gobj/get response "user")))
                        (reset! authed-user user)
                        (cond-> {:conduit.profile/username username
                                 :conduit.profile/email    email
                                 ::errors                  (for [[k vs] errors
                                                                 v vs]
                                                             {:conduit.error/id      (str (gensym "conduit.error"))
                                                              :conduit.error/message (str k ": " v)})}
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
   (pc/resolver `profile
                {::pc/input  #{:conduit.profile/username}
                 ::pc/output [:conduit.profile/bio
                              :conduit.profile/image
                              :conduit.profile/following]}
                (fn [ctx {:conduit.profile/keys [username]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/profiles/" username)}))
                          {:strs [profile]} (js->clj result)
                          {:strs [bio image following]} profile]
                      {:conduit.profile/bio       bio
                       :conduit.profile/image     image
                       :conduit.profile/following following}))))
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
   ::authed-user            (atom (js->clj (ls/get ::authed-user)))
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
