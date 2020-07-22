(ns conduit.user
  (:require [com.fulcrologic.fulcro.inspect.preload]
            #_[devtools.preload]
            [conduit.client :as client]))

(defn after-load
  []
  (client/init-fn))
