(ns conduit.client
  (:require
    [clojure.core.async :as async]
    [clojure.core.async.interop :refer-macros [<p!]]
    [clojure.string :as string]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as ftx]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.report-options :as ro]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.fulcrologic.rad.container :refer [defsc-container]]
    [com.fulcrologic.rad.container-options :as co]
    [conduit.article :as article]
    [conduit.tag :as tag]
    [conduit.feed-button :as feed-button]
    [conduit.profile :as profile]
    [edn-query-language.core :as eql]
    [goog.events :as events]
    [goog.history.EventType :as et]
    [goog.object :as gobj]
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


(defn path->href
  [app path & {:as kvs}]
  (str "#/" (string/join "/" (map js/encodeURIComponent path))
       (when-not (empty? kvs)
         (str "?" (string/join ""
                               (map (fn [[k v]]
                                      (str (js/encodeURIComponent (name k))
                                           "=" (js/encodeURIComponent v)))
                                    kvs))))))


(defsc TagPill [this {:conduit.tag/keys [tag]}]
  {:query [:conduit.tag/tag]
   :ident :conduit.tag/tag}
  (dom/li
    {:className "tag-default tag-pill tag-outline"
     :href      (path->href this ["feed"] :tab (str "#" tag))}
    tag))

(def ui-tag-pill (comp/factory TagPill {:keyfn :conduit.tag/tag}))

(defn ui-banner
  []
  (dom/div
    {:className "banner"}
    (dom/div
      {:className "container"}
      (dom/h1
        {:className "logo-font"}
        "conduit")
      (dom/p "A place to share your knowledge."))))

(report/defsc-report FeedToggle [this props]
  {ro/source-attribute ::feed-toggle
   ro/run-on-mount?    true
   ro/row-pk           feed-button/label
   ro/columns          [feed-button/label
                        feed-button/href]}
  (let [{:ui/keys [current-rows]} props]
    (dom/div
      {:className "feed-toggle"}
      (dom/ul
        {:className "nav nav-pills outline-active"}
        (for [{::feed-button/keys [label href]} current-rows]
          (dom/li
            {:className "nav-item"
             :key       label}
            (dom/a {:className "nav-link disabled"
                    :href      href}
                   label)))))))


(def ui-feed-toggle (comp/factory FeedToggle))

(defsc ArticlePreview [this {:conduit.profile/keys [image username]
                             :conduit.article/keys [title created-at slug
                                                    description tag-list favorites-count]}]
  {:query [:conduit.article/description
           :conduit.article/title
           :conduit.article/favorites-count
           :conduit.article/created-at
           :conduit.article/slug
           ;; TODO: How to do it in RAD?
           {:conduit.article/tag-list (comp/get-query TagPill)}
           :conduit.profile/image
           :conduit.profile/username]
   :ident :conduit.article/slug}
  (dom/div
    {:className "article-preview"}
    (dom/div
      {:className "article-meta"}
      (dom/a
        {:href (path->href this ["profile" username])})
      (dom/img
        {:src image})
      (dom/div
        {:className "info"}
        (dom/a
          {:className "author"
           :href      (path->href this ["profile" username])}
          username)
        (dom/span
          {:className "date"}
          created-at))
      (dom/button
        {:className "btn btn-outline-primary btn-sm pull-xs-right"}
        (dom/i
          {:className "ion-heart"})
        favorites-count))
    (dom/a
      {:className "preview-link"
       :href      (path->href this ["article" slug])}
      (dom/h1 title)
      (dom/p description)
      (dom/span "Read more...")
      (dom/ul
        {:className "tag-list"}
        (map ui-tag-pill tag-list)))))

(def ui-article-preview (comp/factory ArticlePreview {:keyfn :conduit.article/slug}))

(report/defsc-report ArticleFeed [this props]
  {ro/source-attribute    ::articles
   ro/run-on-mount?       true
   ro/row-pk              article/slug
   ro/columns             [article/description
                           article/title
                           article/favorites-count
                           article/created-at
                           article/slug
                           profile/image
                           profile/username]
   ro/row-query-inclusion [{::article/tag-list (comp/get-query TagPill)}]}
  (let [{:ui/keys [current-rows]} props]
    (for [{:conduit.profile/keys [image username]
           :conduit.article/keys [title created-at slug
                                  description tag-list favorites-count]} current-rows]
      (dom/div
        {:key       slug
         :className "article-preview"}
        (dom/div
          {:className "article-meta"}
          (dom/a
            {:href (path->href this ["profile" username])})
          (dom/img
            {:src image})
          (dom/div
            {:className "info"}
            (dom/a
              {:className "author"
               :href      (path->href this ["profile" username])}
              username)
            (dom/span
              {:className "date"}
              created-at))
          (dom/button
            {:className "btn btn-outline-primary btn-sm pull-xs-right"}
            (dom/i
              {:className "ion-heart"})
            favorites-count))
        (dom/a
          {:className "preview-link"
           :href      (path->href this ["article" slug])}
          (dom/h1 title)
          (dom/p description)
          (dom/span "Read more...")
          (dom/ul
            {:className "tag-list"}
            (for [{::tag/keys [tag]} tag-list]
              (dom/li
                {:key       tag
                 :className "tag-default tag-pill tag-outline"
                 :href      (path->href this ["feed"] :tab (str "#" tag))}
                tag))))))))

(def ui-article-feed (comp/factory ArticleFeed))

(report/defsc-report PopularTags [this props]
  {ro/source-attribute ::popular-tags
   ro/run-on-mount?    true
   ro/row-pk           tag/tag
   ro/columns          [tag/tag]}
  (let [{:ui/keys [current-rows]} props]
    (dom/div
      {:className "sidebar"}
      (dom/p "Popular Tags")
      (dom/div
        {:className "tag-list"}
        (for [{::tag/keys [tag]} current-rows]
          (dom/a
            {:key       tag
             :className "tag-pill tag-default"
             :href      (path->href this ["feed"] :tab (str "#" tag))}
            tag))))))

(def ui-popular-tags (comp/factory PopularTags))

(defsc-container Feed [this props]
  {co/children {:article-feed ArticleFeed
                :feed-toggle  FeedToggle
                :popular-tags PopularTags}
   co/route    "feed"}
  (let [{:keys [article-feed
                feed-toggle
                popular-tags]} props]
    (dom/div
      {:className "home-page"}
      (ui-banner)
      (dom/div
        {:className "container page"}
        (dom/div
          {:className "row"}
          (dom/div
            {:className "col-md-9"}
            (ui-feed-toggle feed-toggle)
            (ui-article-feed article-feed))
          (dom/div
            {:className "col-md-3"}
            (ui-popular-tags popular-tags)))))))

(defsc Article [this {::profile/keys [username image]
                      ::article/keys [slug body title favorites-count]}]
  {:query         [::article/body
                   ::profile/username
                   ::profile/image
                   ::article/slug
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
        (dom/div
          :.article-meta
          (dom/a {:href (path->href this ["profile" username])}
                 (dom/img {:src image}))
          (dom/div :.info (dom/a :.author {:href (path->href this ["profile" username])}
                                 username) (dom/span :.date "January 20th"))
          (dom/button
            :.btn.btn-sm.btn-outline-secondary
            (dom/i :.ion-plus-round)
            (str "Follow " username)
            (dom/span :.counter (str "("  #_TODO ")")))
          (dom/button :.btn.btn-sm.btn-outline-primary (dom/i :.ion-heart) "Favorite Post" (dom/span :.counter "(" favorites-count ")")))))
    (dom/div
      :.container.page
      (dom/div
        :.row.article-content
        (dom/div
          :.col-md-12
          body))
      (dom/hr)
      (dom/div
        :.article-actions
        (dom/div
          :.article-meta
          (dom/a {:href (path->href this ["profile" username])}
                 (dom/img {:src image}))
          (dom/div :.info (dom/a :.author {:href (path->href this ["profile" username])}
                                 username) (dom/span :.date "January 20th"))
          (dom/button
            :.btn.btn-sm.btn-outline-secondary
            (dom/i :.ion-plus-round)
            (str "Follow " username)
            (dom/span :.counter "(" #_TODO ")"))
          (dom/button :.btn.btn-sm.btn-outline-primary (dom/i :.ion-heart) "Favorite Post" (dom/span :.counter "(" favorites-count ")"))))
      (dom/div
        :.row
        #_(dom/div
            :.col-xs-12.col-md-8.offset-md-2
            (dom/form
              :.card.comment-form
              (dom/div :.card-block (dom/textarea :.form-control {:placeholder "Write a comment...", :rows "3"}))
              (dom/div)
              :.card-footer
              (dom/img :.comment-author-img {:src "http://i.imgur.com/Qr71crq.jpg"})
              (dom/button :.btn.btn-sm.btn-primary "Post Comment"))
            (dom/div
              :.card
              (dom/div)
              :.card-block
              (dom/p :.card-text "With supporting text below as a natural lead-in to additional content.")
              (dom/div)
              :.card-footer
              (dom/a :.comment-author {:href ""} (dom/img :.comment-author-img {:src "http://i.imgur.com/Qr71crq.jpg"}))
              (dom/a :.comment-author {:href ""} "Jacob Schmidt")
              (dom/span :.date-posted "Dec 29th"))
            (dom/div
              :.card
              (dom/div)
              :.card-block
              (dom/p :.card-text "With supporting text below as a natural lead-in to additional content.")
              (dom/div)
              :.card-footer
              (dom/a :.comment-author {:href ""} (dom/img :.comment-author-img {:src "http://i.imgur.com/Qr71crq.jpg"}))
              (dom/a :.comment-author {:href ""} "Jacob Schmidt")
              (dom/span :.date-posted "Dec 29th")
              (dom/span :.mod-options (dom/i :.ion-edit) (dom/i :.ion-trash-a))))))))

(defsc ErrorMessage [this {:conduit.error/keys [message]}]
  {:query [:conduit.error/message]
   :ident :conduit.error/message}
  (dom/li {}
          message))

(def ui-error-message (comp/factory ErrorMessage {:keyfn :conduit.error/message}))

(defsc Redirect [this {:conduit.redirect/keys [path]}]
  {:query [:conduit.redirect/path]}
  (let [href (str "#/" (string/join "/" path))
        {::keys [^Html5History history]} (comp/shared this)]
    (dom/a
      {:href #(.setToken history href)}
      href)))

(def ui-redirect (comp/factory Redirect))

(declare Header)

(defsc SignIn [this {:conduit.profile.login/keys [loading?
                                                  errors
                                                  redirect]}]
  {:ident         (fn [] [:component/id ::sign-in])
   :query         [:conduit.profile.login/loading?
                   :conduit.profile/username
                   :conduit.profile/email
                   ::top-routes
                   {:conduit.profile.login/errors (comp/get-query ErrorMessage)}
                   {:>/header (comp/get-query Header)}
                   {:conduit.profile.login/redirect (comp/get-query Redirect)}]
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
              {:href (path->href this ["register"])}
              "Need an account?"))
          (dom/ul
            {:className "error-messages"}
            (map ui-error-message errors))
          (when redirect
            (ui-redirect redirect))
          (dom/form
            {:onSubmit (fn [e]
                         (.preventDefault e)
                         (let [form (-> e .-target)
                               email (-> form (gobj/get "email") .-value)
                               password (-> form (gobj/get "password") .-value)]
                           (comp/transact! this `[(conduit.profile/login ~{:conduit.profile/email    email
                                                                           :conduit.profile/password password})])))}
            (dom/fieldset
              {:className "form-group"}
              (dom/input
                {:className   "form-control form-control-lg"
                 :type        "text"
                 :name        "email"
                 :placeholder "Email"}))
            (dom/fieldset
              {:className "form-group"}
              (dom/input
                {:className   "form-control form-control-lg"
                 :type        "password",
                 :name        "password"
                 :placeholder "Password"}))
            (dom/button
              {:className "btn btn-lg btn-primary pull-xs-right"
               :disabled  loading?}
              (if loading?
                "loading ..."
                "Sign in"))))))))

(defsc SignUp [this {::keys [errors]}]
  {:ident         (fn [] [:component/id ::sign-up])
   :query         [:conduit.profile.login/loading?
                   :conduit.profile/username
                   :conduit.profile/email
                   ::top-routes
                   {::errors (comp/get-query ErrorMessage)}
                   {:>/header (comp/get-query Header)}
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
              {:href (path->href this ["login"])}
              "Have an account?"))
          (dom/ul
            {:className "error-messages"}
            (map ui-error-message errors))
          (dom/form
            {:onSubmit (fn [e]
                         (.preventDefault e)
                         (let [form (-> e .-target)
                               email (-> form (gobj/get "email") .-value)
                               username (-> form (gobj/get "username") .-value)
                               password (-> form (gobj/get "password") .-value)]
                           (comp/transact! this `[(conduit.profile/register ~{:conduit.profile/email    email
                                                                              :conduit.profile/username username
                                                                              :conduit.profile/password password})])))}
            (dom/fieldset
              {:className "form-group"}
              (dom/input
                {:className   "form-control form-control-lg"
                 :type        "text",
                 :name        "username",
                 :placeholder "Your Name"}))
            (dom/fieldset
              {:className "form-group"}
              (dom/input
                {:className   "form-control form-control-lg"
                 :type        "text"
                 :name        "email",
                 :placeholder "Email"}))
            (dom/fieldset
              {:className "form-group"}
              (dom/input
                {:className   "form-control form-control-lg"
                 :type        "password",
                 :name        "password",
                 :placeholder "Password"}))
            (dom/button
              {:className "btn btn-lg btn-primary pull-xs-right"}
              "Sign up")))))))


(m/defmutation conduit.profile/login
  [{:conduit.profile/keys [email password]}]
  (action [{:keys [ref state] :as env}]
          (swap! state (fn [st]
                         (-> st
                             (update-in ref assoc :conduit.profile.login/loading? true)))))
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
          (dom/form
            (dom/fieldset
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control.form-control-lg
                  {:type "text", :placeholder "Article Title"}))
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control
                  {:type "text", :placeholder "What's this article about?"}))
              (dom/fieldset
                :.form-group
                (dom/textarea
                  :.form-control
                  {:rows "8", :placeholder "Write your article (in markdown)"}))
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control
                  {:type "text", :placeholder "Enter tags"}))
              (dom/div :.tag-list)
              (dom/button
                :.btn.btn-lg.pull-xs-right.btn-primary
                {:type "button"}
                "Publish Article"))))))))

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
          (dom/form
            {:onSubmit (fn [e]
                         (.preventDefault e)
                         (js/alert "TODO"))}
            (dom/fieldset
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control
                  {:type         "text",
                   :defaultValue image
                   :placeholder  "URL of profile picture"}))
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control.form-control-lg
                  {:type         "text",
                   :defaultValue username
                   :placeholder  "Your Name"}))
              (dom/fieldset
                :.form-group
                (dom/textarea
                  :.form-control.form-control-lg
                  {:rows         "8",
                   :defaultValue bio
                   :placeholder  "Short bio about you"}))
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control.form-control-lg
                  {:type         "text",
                   :defaultValue email
                   :placeholder  "Email"}))
              (dom/fieldset
                :.form-group
                (dom/input
                  :.form-control.form-control-lg
                  {:type "password", :placeholder "Password"}))
              (dom/button
                :.btn.btn-lg.btn-primary.pull-xs-right
                "Update Settings"))))))))


(defsc Profile [this {:conduit.profile/keys [bio username image articles]}]
  {:query         [:conduit.profile/bio
                   :conduit.profile/username
                   :conduit.profile/image
                   {:conduit.profile/articles (comp/get-query ArticlePreview)}]
   :ident         :conduit.profile/username
   :route-segment ["profile" :conduit.profile/username]
   :will-enter    (fn [app {:conduit.profile/keys [username]}]
                    (dr/route-deferred [:conduit.profile/username username]
                                       #(df/load! app [:conduit.profile/username username] Profile
                                                  {:post-mutation        `dr/target-ready
                                                   :post-mutation-params {:target [:conduit.profile/username username]}})))}
  (dom/div
    :.profile-page
    (dom/div
      :.user-info
      (dom/div
        :.container
        (dom/div
          :.row
          (dom/div
            :.col-xs-12.col-md-10.offset-md-1
            (dom/img :.user-img {:src image})
            (dom/h4 username)
            (dom/p
              bio)
            (dom/button
              :.btn.btn-sm.btn-outline-secondary.action-btn)
            (dom/i :.ion-plus-round
                   (str "Follow " username))))))
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
              (dom/a :.nav-link {:href ""} "Favorited Articles"))))
        (map ui-article-preview articles)))))

(defrouter TopRouter [this {:keys [current-state]}]
  {:router-targets [Feed SignIn SignUp Article NewPost Settings Profile]}
  (case current-state
    :pending (dom/div "Loading...")
    :failed (dom/div "Loading seems to have failed. Try another route.")
    (dom/div "Unknown route")))

(def ui-top-router (comp/factory TopRouter))

(defsc Header [this {::keys [top-routes]}]
  {:query         [::dr/current-route
                   ::top-routes]
   :ident         (fn []
                    [::dr/id :conduit.client/TopRouter])
   :initial-state (fn [_]
                    {::top-routes [{::label "Home"
                                    ::path  ["feed"]}
                                   {::label "Sign in"
                                    ::path  ["login"]}
                                   {::label "Sign up"
                                    ::path  ["register"]}]})}
  (let [current-route (dr/current-route this)]
    (dom/nav
      {:className "navbar navbar-light"}
      (dom/div
        {:className "container"}
        (dom/a {:className "navbar-brand"
                :href      (path->href this ["feed"])}
               "conduit")
        (dom/ul
          {:className "nav navbar-nav pull-xs-right"}
          (for [{::keys [label path]} top-routes]
            (dom/li
              {:key       label
               :className "nav-item"}
              (dom/a
                {:href    (path->href this path)
                 :classes ["nav-link" (when (= current-route path)
                                        "active")]}
                label))))))))

(def ui-header (comp/factory Header))

(defsc Footer [this {::keys []}]
  {:query []}
  (dom/footer
    (dom/div
      {:className "container"}
      (dom/a {:className "logo-font"
              :href      (path->href this ["feed"])}
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
                                     (dr/change-route! app path))))
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

(defn qualify-article
  [{:strs [title slug body createdAt updatedAt tagList description author favorited favoritesCount]}]
  (let [{:strs [bio
                following
                image
                username]} author
        profile (when author
                  #:conduit.profile{:bio       bio
                                    :following following
                                    :image     image
                                    :username  username})]
    (merge profile
           (when author
             {::article/author profile})
           {::article/title           title
            ::article/created-at      createdAt
            ::article/slug            slug
            ::article/updated-at      updatedAt
            ::article/description     description
            ::article/favorited?      favorited
            ::article/favorites-count favoritesCount
            ::article/tag-list        (for [tag tagList]
                                        {::tag/tag tag})

            ::article/body            body})))

(def register
  [(pc/constantly-resolver
     ::feed-toggle [{::feed-button/label "Your Feed"
                     ::feed-button/href  (str "#/feed")}
                    {::feed-button/label "Global Feed"
                     ::feed-button/href  (str "#/feed")}])
   (pc/resolver `top-routes
                {::pc/output [::top-routes]}
                (fn [{::keys [authed-user]} _]
                  (let [username (-> @authed-user (get "username"))]
                    {::top-routes (if username
                                    [{::label "Home"
                                      ::path  ["feed"]}
                                     {::label "New Post"
                                      ::path  ["editor"]}
                                     {::label "Settings"
                                      ::path  ["settings" username]}
                                     {::label username
                                      ::path  ["profile" username]}]
                                    [{::label "Home"
                                      ::path  ["feed"]}
                                     {::label "Sign up"
                                      ::path  ["register"]}
                                     {::label "Sign in"
                                      ::path  ["login"]}])})))
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
                        (reset! authed-user user)
                        {:conduit.profile/username       username
                         :conduit.profile/email          email
                         :conduit.profile.login/loading? false
                         :conduit.profile.login/errors   []
                         :conduit.profile.login/redirect (when (empty? errors)
                                                           {:conduit.redirect/path ["feed"]})})))))

   (pc/resolver `article
                {::pc/input  #{:conduit.article/slug}
                 ::pc/output [::article/body
                              ::profile/image
                              ::profile/username
                              ::article/title]}
                (fn [ctx {:conduit.article/keys [slug]}]
                  (async/go
                    (let [result (async/<! (fetch ctx {::path (str "/articles/" slug)}))
                          {:strs [article]} (js->clj result)]
                      (qualify-article article)))))
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
   ::authed-user            (atom nil)
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
