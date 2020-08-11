(ns conduit.workspace
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.core :as ws]
            [conduit.client :as conduit]
            [nubank.workspaces.card-types.fulcro3 :as ctf3]
            [com.fulcrologic.fulcro.components :refer [defsc]]))

(ws/defcard tag-link
  (ctf3/fulcro-card
    {::ctf3/root          conduit/TagLink
     ::ctf3/initial-state {:conduit.tag/tag "Hello"}}))

(ws/defcard popular-tags
  (ctf3/fulcro-card
    {::ctf3/root          conduit/PopularTags
     ::ctf3/initial-state {::conduit/popular-tags [{:conduit.tag/tag "Hello"}]}}))

(defonce init (ws/mount))
