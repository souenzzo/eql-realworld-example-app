(ns conduit.dev
  (:require [com.fulcrologic.fulcro.inspect.preload]
            [devtools.preload]
            [conduit.client :as client]))

(defn after-load
  []
  (client/init-fn))
