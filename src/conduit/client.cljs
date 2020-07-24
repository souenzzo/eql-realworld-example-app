(ns conduit.client
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as ftx]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [cljs.core.async.interop :refer-macros [<p!]]
    [com.fulcrologic.fulcro.application :as app]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [edn-query-language.core :as eql]
    [clojure.core.async :as async]))

;; TODO: Create a lib for "pathom remote"
(defn transmit!
  [{:keys [parser]
    :as   env} {::ftx/keys [id idx ast options update-handler
                            result-handler active?]}]
  (let [query (eql/ast->query ast)
        result (parser env query)]
    (async/go
      (result-handler {:body                 (async/<! result)
                       :original-transaction ast
                       :status-code          200}))))



(defsc TagPill [this {:conduit.tag/keys [tag]}]
  {:query [:conduit.tag/tag]
   :ident :conduit.tag/tag}
  (dom/li
    {:className "tag-default tag-pill tag-outline"
     :href      ""}
    tag))

(def ui-tag-pill (comp/factory TagPill {:keyfn :conduit.tag/tag}))

(defsc TagLink [this {:conduit.tag/keys [tag]}]
  {:query [:conduit.tag/tag]
   :ident :conduit.tag/tag}
  (dom/a
    {:className "tag-pill tag-default"
     :href      ""}
    tag))

(def ui-tag-link (comp/factory TagLink {:keyfn :conduit.tag/tag}))

(defsc PopularTags [this {::keys [popular-tags]}]
  {:ident (fn [] [:component/id ::popular-tags])
   :query [::popular-tags]}
  (dom/div
    {:className "sidebar"}
    (dom/p "Popular Tags")
    (dom/div
      {:className "tag-list"}
      (map ui-tag-link popular-tags))))

(def ui-popular-tags (comp/factory PopularTags))

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

(defsc FeedToggle [this props]
  {:query []}
  (dom/div
    {:className "feed-toggle"}
    (dom/ul
      {:className "nav nav-pills outline-active"}
      (for [{::keys [label href]} [{::label "Your Feed"
                                    ::href  ""}
                                   {::label "Global Feed"
                                    ::href  ""}]]

        (dom/li
          {:key       label
           :className "nav-item"}
          (dom/a {:className "nav-link disabled"
                  :href      href}
                 label))))))

(def ui-feed-toggle (comp/factory FeedToggle))

(defsc ArticlePreview [this {:conduit.profile/keys [image username]
                             :conduit.article/keys [title created-at
                                                    description tag-list favorites-count]}]
  {:query [:conduit.article/description
           :conduit.article/title
           :conduit.article/tag-list
           :conduit.article/favorites-count
           :conduit.article/created-at
           :conduit.profile/image
           :conduit.profile/username
           :conduit.article/slug]
   :ident :conduit.article/slug}
  (dom/div
    {:className "article-preview"}
    (dom/div
      {:className "article-meta"}
      (dom/a
        {:href "profile.html"})
      (dom/img
        {:src image})
      (dom/div
        {:className "info"}
        (dom/a
          {:className "author"
           :href      ""}
          username)
        (dom/span
          {:className "date"}
          created-at))
      (dom/button
        {:className "btn btn-outline-primary btn-sm pull-xs-right"}
        (dom/i
          {:className "ion-heart"}
          favorites-count)))
    (dom/a
      {:className "preview-link"
       :href      ""}
      (dom/h1 title)
      (dom/p description)
      (dom/span "Read more...")
      (dom/ul
        {:className "tag-list"}
        (map ui-tag-pill tag-list)))))

(def ui-article-preview (comp/factory ArticlePreview {:keyfn :conduit.article/slug}))

(defsc Feed [this {::keys  [articles]
                   :>/keys [popular-tags feed-toggle]
                   :as     props}]
  {:ident         (fn [] [:component/id ::feed])
   :query         [:component/id
                   {::articles (comp/get-query ArticlePreview)}
                   {:>/popular-tags (comp/get-query PopularTags)}
                   {:>/feed-toggle (comp/get-query FeedToggle)}]
   #_#_:will-enter (fn [app _]
                     (dr/route-deferred [:component/id ::feed]
                                        #(df/load! app [:component/id ::feed] Feed
                                                   {:post-mutation        `dr/target-ready
                                                    :post-mutation-params {:target [:component/id ::feed]}})))
   :route-segment ["feed"]}
  (dom/div
    {:className "home-page"}
    (dom/button
      {:onClick #(df/load! this [:component/id ::feed] Feed)}
      "WIP - load")
    (ui-banner)
    (dom/div
      {:className "container page"}
      (dom/div
        {:className "row"}
        (dom/div
          {:className "col-md-9"}
          (ui-feed-toggle feed-toggle)
          (map ui-article-preview articles))
        (dom/div
          {:className "col-md-3"}
          (ui-popular-tags popular-tags))))))

(defsc Login [this props]
  {:ident         (fn [] [:component/id ::login])
   :query         []
   :route-segment ["login"]}
  (dom/div "login"))

(defsc Register [this props]
  {:ident         (fn [] [:component/id ::register])
   :query         []
   :route-segment ["register"]}
  (dom/div "register"))

(defsc Settings [this props]
  {:ident         (fn [] [:component/id ::settings])
   :query         []
   :route-segment ["settings"]}
  (dom/div "settings"))

(defrouter TopRouter [this {:keys [current-state]}]
  {:router-targets [Feed Login Register Settings]}
  (case current-state
    :pending (dom/div "Loading...")
    :failed (dom/div "Loading seems to have failed. Try another route.")
    (dom/div "Unknown route")))

(def ui-top-router (comp/factory TopRouter))

(defsc Header [this {::keys []}]
  {:query []}
  (dom/nav
    {:className "navbar navbar-light"}
    (dom/div
      {:className "container"}
      (dom/a {:className "navbar-brand"
              :href      "index.html"}
             "conduit")
      (dom/ul
        {:className "nav navbar-nav pull-xs-right"}
        (for [{::keys [label href]} [{::label "Home"
                                      ::href  "feed"}
                                     {::label "New Post"
                                      ::href  ""}
                                     {::label "Settings"
                                      ::href  "settings"}
                                     {::label "Sign Up"
                                      ::href  "login"}]]
          (dom/li
            {:key       href
             :className "nav-item"}
            (dom/a
              {:onClick   #(dr/change-route this [href])
               :className "nav-link active"}
              label)
            ;;TODO: Back to href
            #_(dom/a
                {:href      href
                 :className "nav-link active"}
                label)))))))

(def ui-header (comp/factory Header))

(defsc Footer [this {::keys []}]
  {:query []}
  (dom/footer
    (dom/div
      {:className "container"}
      (dom/a {:className "logo-font"
              :href      "/"}
             "conduit")
      (dom/span
        {:className "attribution"}
        "An interactive learning project from "
        (dom/a {:href "https://thinkster.io"} "Thinkster")
        ". Code & design licensed under MIT."))))

(def ui-footer (comp/factory Footer))

(defsc Root [this {:>/keys [footer header router]
                   :as     props}]
  {:query         [{:>/header (comp/get-query Header)}
                   {:>/router (comp/get-query TopRouter)}
                   {:>/footer (comp/get-query Footer)}]
   :initial-state (fn [_]
                    {:>/header (comp/get-initial-state Header _)
                     :>/router {}
                     :>/footer (comp/get-initial-state Footer _)})}
  (comp/fragment
    #_(debug props)
    (ui-header header)
    (ui-top-router router)
    (ui-footer footer)))

(defn client-did-mount
  "Must be used as :client-did-mount parameter of app creation, or called just after you mount the app."
  [app]
  (dr/change-route app ["feed"]))


(defn fetch
  [{::keys [api-url]} {::keys [path]}]
  (async/go
    (-> (js/fetch (str api-url path))
        <p!
        .json
        <p!)))

(def register
  [(pc/resolver `popular-tags
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
                       ::articles       (for [{:strs [updatedAt body createdAt author favorited slug tagList favoritesCount title
                                                      description]} articles
                                              :let [{:strs [bio
                                                            following
                                                            image
                                                            username]} author
                                                    profile #:conduit.profile{:bio       bio
                                                                              :following following
                                                                              :image     image
                                                                              :username  username}]]
                                          (merge
                                            profile
                                            #:conduit.article{:updated-at      updatedAt
                                                              :body            body
                                                              :created-at      createdAt
                                                              :author          profile
                                                              :favorited?      favorited
                                                              :slug            slug
                                                              :tag-list        (for [tag tagList]
                                                                                 {:conduit.tag/tag tag})
                                                              :favorites-count favoritesCount
                                                              :title           title
                                                              :description     description}))}))))])

(def parser
  (p/parallel-parser
    {::p/plugins [(pc/connect-plugin {::pc/register register})]}))

(def remote
  {:transmit!               transmit!
   :parser                  parser
   ::api-url                "https://conduit.productionready.io/api"
   ::p/reader               [p/map-reader
                             pc/parallel-reader
                             pc/open-ident-reader
                             p/env-placeholder-reader]
   ::p/placeholder-prefixes #{">"}})

(defonce app (app/fulcro-app {:client-did-mount client-did-mount
                              :remotes          {:remote remote}}))

(def node "conduit")

(defn ^:export init-fn
  []
  (app/mount! app Root node))

