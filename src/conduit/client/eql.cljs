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
        api-url (gset/get target "apiUrl")
        jwt-token (atom nil)
        wrap-fulcro-response (fnhr/wrap-fulcro-response)
        wrap-fulcro-request (fnhr/wrap-fulcro-request)
        request-middleware (fn [req]
                             (-> (if-let [token @jwt-token]
                                   (assoc-in req [:headers "Authorization"] (str "Token " token))
                                   req)
                                 (wrap-fulcro-request)))
        response-middleware (fn [res]
                              (let [{:keys [body]
                                     :as   result} (wrap-fulcro-response res)]
                                (when-let [token (some :conduit.jwt/token (vals body))]
                                  (reset! jwt-token token))
                                result))]

    (-> (reset! app (app/fulcro-app {:client-did-mount cr/client-did-mount
                                     :shared           {::cr/history (Html5History.)}
                                     :remotes          {:remote (fnhr/fulcro-http-remote {:response-middleware response-middleware
                                                                                          :request-middleware  request-middleware
                                                                                          :url                 api-url})}}))
        (app/mount! cr/Root target))))
