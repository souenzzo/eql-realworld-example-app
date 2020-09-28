(ns conduit.client.eql
  (:require [conduit.client-root :as cr]
            [com.fulcrologic.fulcro.networking.http-remote :as fnhr]
            [goog.dom :as gdom]
            [goog.dom.dataset :as gset]
            [com.fulcrologic.fulcro.application :as app])
  (:import (goog.history Html5History)))


(defonce app
         (atom nil))

(defn ^:export main
  [node]
  (let [target (gdom/getElement node)
        api-url (gset/get target "apiUrl")]
    (-> (reset! app (app/fulcro-app {:client-did-mount cr/client-did-mount
                                     :shared           {::cr/history (Html5History.)}
                                     :remotes          {:remote (fnhr/fulcro-http-remote {:url api-url})}}))
        (app/mount! cr/Root target))))
