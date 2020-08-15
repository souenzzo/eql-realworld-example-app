(ns conduit.ui
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [goog.object :as gobj]
            [conduit.article :as article]
            [conduit.profile :as profile]
            [conduit.tag :as tag]))

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
            types
            labels]}]
  (dom/form
    {:onSubmit (fn [e]
                 (.preventDefault e)
                 (when on-submit
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
          {:className   "form-control form-control-lg"
           :type        (get types attribute "text")
           :name        (str attribute)
           :placeholder (get labels attribute "Email")})))
    (dom/button
      {:className "btn btn-lg btn-primary pull-xs-right"
       :disabled  (not on-submit)}
      submit-label)))

(defn article-meta
  [{::profile/keys [href image username]
    ::article/keys [created-at
                    favorites-count]}]
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
        created-at)
      (dom/button
        {:className "btn btn-outline-primary btn-sm pull-xs-right"}
        (dom/i
          {:className "ion-heart"})
        favorites-count))))

(defn article-preview
  [{::article/keys [href
                    title
                    description
                    tag-list]
    :as            params}]
  (dom/div
    {:key       title
     :className "article-preview"}
    (article-meta params)
    (dom/a
      {:className "preview-link"
       :href      href}
      (dom/h1 title)
      (dom/p description)
      (dom/span "Read more...")
      (dom/ul
        {:className "tag-list"}
        (for [{::tag/keys [tag href]} tag-list]
          (dom/li
            {:key       tag
             :className "tag-default tag-pill tag-outline"
             :href      href}
            tag))))))
