(ns conduit.eql-client
  (:require [conduit.client-root :as cr]
            [com.fulcrologic.fulcro.networking.http-remote :as fnhr]
            [com.fulcrologic.fulcro.application :as app])
  (:import (goog.history Html5History)))

(defonce app
         (delay (app/fulcro-app {:client-did-mount cr/client-did-mount
                                 :shared           {::cr/history (Html5History.)}
                                 :remotes          {:remote (fnhr/fulcro-http-remote {})}})))

(defn ^:export main
  [node]
  (app/mount! @app cr/Root node))
