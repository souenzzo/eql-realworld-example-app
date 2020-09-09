(ns conduit.workspace
  (:require [nubank.workspaces.core :as ws]
            [conduit.client-root :as cr]
            [nubank.workspaces.card-types.fulcro3 :as ctf3]))

(ws/defcard header
  (ctf3/fulcro-card
    {::ctf3/root cr/Header}))

(ws/defcard footer
  (ctf3/fulcro-card
    {::ctf3/root cr/Footer}))

(ws/defcard home
  (ctf3/fulcro-card
    {::ctf3/root cr/Home}))

(ws/defcard login
  (ctf3/fulcro-card
    {::ctf3/root cr/SignIn}))

(ws/defcard register
  (ctf3/fulcro-card
    {::ctf3/root cr/SignUp}))

(ws/defcard profile
  (ctf3/fulcro-card
    {::ctf3/root cr/Profile}))

(ws/defcard settings
  (ctf3/fulcro-card
    {::ctf3/root cr/Settings}))

(ws/defcard create-edit-article
  (ctf3/fulcro-card
    {::ctf3/root cr/NewPost}))

(ws/defcard article
  (ctf3/fulcro-card
    {::ctf3/root cr/Article}))

(defn init-fn
  []
  (ws/mount))
