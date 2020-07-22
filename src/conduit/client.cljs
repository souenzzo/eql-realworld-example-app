(ns conduit.client
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.application :as app]))

(defsc TagPill [this {:conduit.tag/keys [tag]}]
  {:query [:conduit.tag/tag]
   :ident :conduit.tag/tag}
  (dom/a
    {:className "tag-pill tag-default"
     :href      ""}
    tag))

(def ui-tag-pill (comp/factory TagPill {:keyfn :conduit.tag/tag}))

(defsc PopularTags [this {::keys [popular-tags]}]
  {:ident (fn [] [:component/id ::popular-tags])
   :query [::popular-tags]}
  (dom/div
    {:className "sidebar"}
    (dom/p "Popular Tags")
    (dom/div
      {:className "tag-list"}
      (map ui-tag-pill popular-tags))))

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
  {:query []})

(defn ui-feed-toggle
  [_]
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

(defsc ArticlePreview [this props]
  {:query []})

(defn ui-article-preview
  [_]
  (dom/div
    {:className "article-preview"}
    (dom/div
      {:className "article-meta"}
      (dom/a
        {:href "profile.html"})
      (dom/img
        {:src "http://i.imgur.com/Qr71crq.jpg"})
      (dom/div
        {:className "info"}
        (dom/a
          {:className "author"
           :href      ""} "Eric Simons")
        (dom/span
          {:className "date"}
          "January 20th"))
      (dom/button
        {:className "btn btn-outline-primary btn-sm pull-xs-right"}
        (dom/i
          {:className "ion-heart"}
          "29")))
    (dom/a
      {:className "preview-link"
       :href      ""}
      (dom/h1 "How to build webapps that scale")
      (dom/p "This is the description for the post.")
      (dom/span "Read more..."))))

(defsc Feed [this {::keys  [articles]
                   :>/keys [popular-tags feed-toggle]}]
  {:ident         (fn [] [:component/id ::feed])
   :query         [{::articles (comp/get-query ArticlePreview)}
                   {:>/popular-tags (comp/get-query PopularTags)}
                   {:>/feed-toggle (comp/get-query FeedToggle)}]
   :route-segment ["feed"]}
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


(defsc Root [this {:>/keys    [footer header]
                   :root/keys [router]}]
  {:query         [{:>/footer (comp/get-query Footer)}
                   {:>/header (comp/get-query Header)}
                   {:root/router (comp/get-query TopRouter)}]
   :initial-state {:root/router {}}}
  (comp/fragment
    (ui-header header)
    (ui-top-router router)
    (ui-footer footer)))

(defn client-did-mount
  "Must be used as :client-did-mount parameter of app creation, or called just after you mount the app."
  [app]
  (dr/change-route app ["main"]))

(def api-url
  "https://conduit.productionready.io/api")


(defonce app (app/fulcro-app {:client-did-mount client-did-mount}))

(def node "conduit")

(defn ^:export init-fn
  []
  (app/mount! app Root node))

