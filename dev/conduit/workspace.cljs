(ns conduit.workspace
  (:require [nubank.workspaces.core :as ws]
            [conduit.client :as conduit]
            [nubank.workspaces.card-types.fulcro3 :as ctf3]))

(ws/defcard header
  (ctf3/fulcro-card
    {::ctf3/root conduit/Header}))

(ws/defcard footer
  (ctf3/fulcro-card
    {::ctf3/root conduit/Footer}))

(ws/defcard home
  (ctf3/fulcro-card
    {::ctf3/root conduit/Feed}))

(ws/defcard login
  (ctf3/fulcro-card
    {::ctf3/root conduit/SignIn}))

(ws/defcard register
  (ctf3/fulcro-card
    {::ctf3/root conduit/SignUp}))

(ws/defcard profile
  (ctf3/fulcro-card
    {::ctf3/root conduit/Profile}))

(ws/defcard settings
  (ctf3/fulcro-card
    {::ctf3/root conduit/Settings}))

(ws/defcard create-edit-article
  (ctf3/fulcro-card
    {::ctf3/root conduit/NewPost}))

(ws/defcard article
  (ctf3/fulcro-card
    {::ctf3/root conduit/Article}))

(defonce init (ws/mount))
