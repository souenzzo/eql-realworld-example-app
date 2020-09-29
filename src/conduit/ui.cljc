(ns conduit.ui
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            #?@(:cljs    [[com.fulcrologic.fulcro.dom :as dom]
                          [goog.object :as gobj]
                          ["marksy" :as md]
                          ["react" :as r]]
                :default [[com.fulcrologic.fulcro.dom-server :as dom]])))

(def markdown-impl
  #?(:cljs    (md/marksy #js {:createElement r/createElement})
     :default (constantly nil)))

(defn markdown
  [s]
  #?(:cljs    (.-tree (markdown-impl (str s)))
     :default nil))

(defn show-date
  [x]
  #?(:cljs    (when (inst? x)
                (.toLocaleDateString x js/undefined #js {:weekday "short"
                                                         :month   "short"
                                                         :day     "numeric"
                                                         :year    "numeric"}))
     :default (str x)))

(defsc TagPillOutline [this {:conduit.tag/keys [tag href]}]
  {:query [:conduit.tag/tag
           :conduit.tag/href]
   :ident :conduit.tag/tag}
  (dom/li
    {:className "tag-default tag-pill tag-outline"
     :href      href}
    tag))

(def ui-tag-pill-outline (comp/factory TagPillOutline {:keyfn :conduit.tag/tag}))

(defsc TagPill [this {:conduit.tag/keys [tag href]}]
  {:query [:conduit.tag/tag
           :conduit.tag/href]
   :ident :conduit.tag/tag}
  (dom/a
    {:className "tag-default tag-pill"
     :href      href}
    tag))

(def ui-tag-pill (comp/factory TagPill {:keyfn :conduit.tag/tag}))

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
            types large
            multiline
            labels]}]
  (let [disabled? (not on-submit)]
    (dom/form
      {:onSubmit (fn [e]
                   (.preventDefault e)
                   #?(:cljs    (when-not disabled?
                                 (let [form (-> e .-target)
                                       params (into {}
                                                    (map (fn [attribute]
                                                           [attribute (.-value (gobj/get form (str attribute)))]))
                                                    attributes)]
                                   (on-submit params)))
                      :default nil))}
      (for [attribute attributes
            :let [attrs {:className    (if (contains? large attribute)
                                         "form-control form-control-lg"
                                         "form-control")
                         :name         (str attribute)
                         :defaultValue (get default-values attribute "")
                         :placeholder  (get labels attribute (name attribute))}]]
        (dom/fieldset
          {:key       (str attribute)
           :className "form-group"}
          (if (contains? multiline attribute)
            (dom/textarea
              (assoc attrs :rows 8))
            (dom/input
              (assoc attrs :type (get types attribute "text"))))))
      (dom/button
        {:className "btn btn-lg btn-primary pull-xs-right"
         :disabled  disabled?}
        submit-label))))

(defsc Comment [this {:conduit.profile/keys [username image]
                      :conduit.comment/keys [body created-at updated-at]}]
  {:query [:conduit.comment/id
           :conduit.comment/body
           :conduit.comment/created-at
           :conduit.comment/updated-at
           :conduit.profile/username
           :conduit.profile/image]
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
             (dom/img :.comment-author-img
                      {:alt (str "profile of " username)
                       :src image}))
      (dom/a :.comment-author {:href ""} username)
      (dom/span :.date-posted (show-date created-at)))))

(def ui-comment (comp/factory Comment {:keyfn :conduit.comment/id}))


(defsc FeedButton [this {:conduit.feed-button/keys [label href]}]
  {:query [:conduit.feed-button/href
           :conduit.feed-button/label]
   :ident :conduit.feed-button/label}
  (dom/li
    {:className "nav-item"
     :key       label}
    (dom/a {:className "nav-link disabled"
            :href      href}
           label)))


(def ui-feed-button (comp/factory FeedButton {:keyfn :conduit.feed-button/label}))


(defsc ArticleMeta [this {:conduit.profile/keys [href image username]
                          :conduit.article/keys [created-at
                                                 favorited?
                                                 favorites-count]}
                    {::keys [on-favorite
                             on-fav
                             on-flow]}]
  {:query [:conduit.profile/href
           :conduit.profile/image
           :conduit.profile/username
           :conduit.article/created-at
           :conduit.article/favorited?
           :conduit.article/favorites-count
           :conduit.article/slug]
   :ident :conduit.article/slug}
  (dom/div
    {:className "article-meta"}
    (dom/a
      {:href href}
      (dom/img
        {:alt (str "profile of " username)
         :src image}))
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
         :className (if favorited?
                      "btn btn-sm btn-primary pull-xs-right"
                      "btn btn-outline-primary btn-sm pull-xs-right")}
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
(def ui-article-meta (comp/factory ArticleMeta {:keyfn :conduit.article/slug}))

(defsc ArticlePreview [this
                       {:conduit.article/keys [href
                                               title
                                               description
                                               tag-list]
                        :>/keys               [article-meta]}
                       computed]
  {:query [:conduit.article/href
           :conduit.article/title
           :conduit.article/description
           :conduit.article/slug
           {:conduit.article/tag-list (comp/get-query TagPillOutline)}
           {:>/article-meta (comp/get-query ArticleMeta)}]
   :ident :conduit.article/slug}
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

(def ui-article-preview (comp/factory ArticlePreview {:keyfn :conduit.article/slug}))

(defsc UserInfo [this {:conduit.profile/keys [image username bio]}
                 {::keys [on-follow]}]
  {:query [:conduit.profile/image
           :conduit.profile/username
           :conduit.profile/bio]
   :ident :conduit.profile/username}
  (dom/div
    :.user-info
    (dom/div
      :.container
      (dom/div
        :.row
        (dom/div
          :.col-xs-12.col-md-10.offset-md-1
          (dom/img :.user-img {:alt (str "profile of " username)
                               :src image})
          (dom/h4 username)
          (dom/section
            (markdown bio))
          (when on-follow
            (dom/button
              {:className "btn btn-sm btn-outline-secondary action-btn"
               :onClick   on-follow}
              (dom/i :.ion-plus-round)
              (str "Follow " username))))))))

(def ui-user-info (comp/factory UserInfo {:keyfn :conduit.profile/username}))

(defsc ErrorMessage [this {:conduit.error/keys [message]
                           :as                 props}
                     {::keys [on-remove]}]
  {:query [:conduit.error/message]}
  (dom/li {:onClick (when on-remove
                      (partial on-remove props))}
          message))

(def ui-error-message (comp/factory ErrorMessage {:keyfn :conduit.error/message}))
