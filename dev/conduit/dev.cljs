(ns conduit.dev
  (:require [com.fulcrologic.fulcro.inspect.preload]
            [devtools.preload]
            [conduit.client :as client]
            [com.fulcrologic.fulcro.application :as app]))

(defn after-load
  []
  (app/force-root-render! @client/app))
