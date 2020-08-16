(ns conduit.ui
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [conduit.article :as article]
            [conduit.profile :as profile]
            [conduit.tag :as tag]
            ["marksy" :as md]
            ["react" :as r]
            [conduit.feed-button :as feed-button]))

(def markdown-impl
  (md/marksy #js {:createElement r/createElement}))

(defn markdown
  [s]
  (.-tree (markdown-impl s)))

(defn show-date
  [x]
  (when (inst? x)
    (.toLocaleDateString x js/undefined #js {:weekday "short"
                                             :month   "short"
                                             :day     "numeric"
                                             :year    "numeric"})))


(defsc TagPillOutline [this {::tag/keys [tag href]}]
  {:query [::tag/tag
           ::tag/href]
   :ident ::tag/tag}
  (dom/li
    {:className "tag-default tag-pill tag-outline"
     :href      href}
    tag))

(def ui-tag-pill-outline (comp/factory TagPillOutline {:keyfn ::tag/tag}))

(defsc TagPill [this {::tag/keys [tag href]}]
  {:query [::tag/tag
           ::tag/href]
   :ident ::tag/tag}
  (dom/a
    {:className "tag-default tag-pill"
     :href      href}
    tag))

(def ui-tag-pill (comp/factory TagPill {:keyfn ::tag/tag}))

(defn banner
  []
  (dom/div
    {:className "banner"}
    (dom/div
      {:className "container"}
      (dom/h1
        {:className "logo-font"}
        "conduit")
      (dom/p "A place to share your knowledge."))))

(defn form
  [{::keys [attributes
            on-submit
            submit-label
            default-values
            types
            labels]}]
  (let [disabled? (not on-submit)]
    (dom/form
      {:onSubmit (fn [e]
                   (.preventDefault e)
                   (when-not disabled?
                     (let [form (-> e .-target)
                           params (into {}
                                        (map (fn [attribute]
                                               [attribute (.-value (gobj/get form (str attribute)))]))
                                        attributes)]
                       (on-submit params))))}
      (for [attribute attributes]
        (dom/fieldset
          {:key       (str attribute)
           :className "form-group"}
          (dom/input
            {:className    "form-control form-control-lg"
             :type         (get types attribute "text")
             :name         (str attribute)
             :ref          (fn [])
             :defaultValue (get default-values attribute "")
             :placeholder  (get labels attribute (name attribute))})))
      (dom/button
        {:className "btn btn-lg btn-primary pull-xs-right"
         :disabled  disabled?}
        submit-label))))

(defsc Comment [this {::profile/keys        [username image]
                      :conduit.comment/keys [body created-at updated-at]}]
  {:query [:conduit.comment/id
           :conduit.comment/body
           :conduit.comment/created-at
           :conduit.comment/updated-at
           ::profile/username
           ::profile/image]
   :ident :conduit.comment/id}
  (dom/div
    :.card
    (dom/div
      :.card-block
      (dom/section
        {:className "card-text"}
        (markdown body)))
    (dom/div
      :.card-footer
      (dom/a :.comment-author {:href ""}
             (dom/img :.comment-author-img {:src image}))
      (dom/a :.comment-author {:href ""} username)
      (dom/span :.date-posted (show-date created-at)))))

(def ui-comment (comp/factory Comment {:keyfn :conduit.comment/id}))


(defsc FeedButton [this {::feed-button/keys [label href]}]
  {:query [::feed-button/href
           ::feed-button/label]
   :ident ::feed-button/label}
  (dom/li
    {:className "nav-item"
     :key       label}
    (dom/a {:className "nav-link disabled"
            :href      href}
           label)))


(def ui-feed-button (comp/factory FeedButton {:keyfn ::feed-button/label}))


(defsc ArticleMeta [this {::profile/keys [href image username]
                          ::article/keys [created-at
                                          favorites-count]}
                    {::keys [on-favorite
                             on-fav
                             on-flow]}]
  {:query [::profile/href
           ::profile/image
           ::profile/username
           ::article/created-at
           ::article/favorites-count
           ::article/slug]
   :ident ::article/slug}
  (dom/div
    {:className "article-meta"}
    (dom/a
      {:href href}
      (dom/img
        {:src image}))
    (dom/div
      {:className "info"}
      (dom/a
        {:className "author"
         :href      href}
        username)
      (dom/span
        {:className "date"}
        (show-date created-at)))
    (when on-fav
      (dom/button
        {:onClick   on-fav
         :className "btn btn-outline-primary btn-sm pull-xs-right"}
        (dom/i {:className "ion-heart"})
        favorites-count))
    (when on-flow
      (dom/button
        {:onClick   on-flow
         :className "btn btn-sm btn-outline-secondary"}
        (dom/i
          {:className "ion-plus-round"})
        (str "Flow " username)))
    (when on-favorite
      (dom/button
        {:onClick   on-favorite
         :className "btn btn-sm btn-outline-primary"}
        (dom/i
          {:className "ion-heart"})
        (str "Favorite Post " favorites-count)))))
(def ui-article-meta (comp/factory ArticleMeta {:keyfn ::article/slug}))

(defsc ArticlePreview [this
                       {::article/keys [href
                                        title
                                        description
                                        tag-list]
                        :>/keys        [article-meta]}
                       computed]
  {:query [::article/href
           ::article/title
           ::article/description
           ::article/slug
           {::article/tag-list (comp/get-query TagPillOutline)}
           {:>/article-meta (comp/get-query ArticleMeta)}]
   :ident ::article/slug}
  (dom/div
    {:key       title
     :className "article-preview"}
    (ui-article-meta (comp/computed article-meta computed))
    (dom/a
      {:className "preview-link"
       :href      href}
      (dom/h1 title)
      (dom/p description)
      (dom/span "Read more...")
      (dom/ul
        {:className "tag-list"}
        (map ui-tag-pill-outline tag-list)))))

(def ui-article-preview (comp/factory ArticlePreview {:keyfn ::article/slug}))

(defsc UserInfo [this {::profile/keys [image username bio]}
                 {::keys [on-follow]}]
  {:query [::profile/image
           ::profile/username
           ::profile/bio]
   :ident ::profile/username}
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
          (when on-follow
            (dom/button
              {:className "btn btn-sm btn-outline-secondary action-btn"
               :onClick   on-follow}
              (dom/i :.ion-plus-round)
              (str "Follow " username))))))))

(def ui-user-info (comp/factory UserInfo {:keyfn ::profile/username}))

(defsc ErrorMessage [this {:conduit.error/keys [message]
                           :as                 props}
                     {::keys [on-remove]}]
  {:query [:conduit.error/message]}
  (dom/li {:onClick (when on-remove
                      (partial on-remove props))}
          message))

(def ui-error-message (comp/factory ErrorMessage {:keyfn :conduit.error/message}))
