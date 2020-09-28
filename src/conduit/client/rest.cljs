(ns conduit.client.rest
  (:require [conduit.client-root :as cr]
            [com.fulcrologic.fulcro.algorithms.tx-processing :as ftx]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.diplomat.http :as pd.http]
            [com.wsscode.pathom.diplomat.http.fetch :as pd.fetch]
            [com.wsscode.pathom.core :as p]
            [conduit.connect.proxy :as connect]
            [clojure.core.async :as async]
            [goog.dom :as gdom]
            [goog.dom.dataset :as gset]
            [com.fulcrologic.fulcro.application :as app]
            [edn-query-language.core :as eql])
  (:import (goog.history Html5History Event)))

;; TODO: Create a lib for "pathom remote"
(defn transmit!
  [{:keys [parser]
    :as   env} {::ftx/keys [; id idx options update-handler active?
                            result-handler ast]}]
  (let [query (eql/ast->query ast)
        result (parser env query)]
    (async/go
      (result-handler {:body                 (async/<! result)
                       :original-transaction ast
                       :status-code          200}))))


(def parser
  (p/parallel-parser
    {::p/plugins [(pc/connect-plugin {::pc/register (concat connect/register
                                                            [pc/index-explorer-resolver])})
                  p/elide-special-outputs-plugin]
     ::p/mutate  pc/mutate-async}))

(def remote
  {:transmit!               transmit!
   :parser                  parser
   ::cr/jwt                 (reify
                              IDeref (-deref [this] (.getItem js/localStorage "jwt"))
                              IReset (-reset! [this value] (.setItem js/localStorage "jwt" value)
                                       value))
   ::pd.http/driver         pd.fetch/request-async
   ::p/reader               [p/map-reader
                             pc/parallel-reader
                             pc/open-ident-reader
                             p/env-placeholder-reader]
   ::p/placeholder-prefixes #{">"}})

(defonce app (atom nil))

(defn ^:export main
  [node]
  (let [target (gdom/getElement node)
        api-url (gset/get target "apiUrl")]
    (-> (reset! app (app/fulcro-app {:client-did-mount cr/client-did-mount
                                     :shared           {::cr/history (Html5History.)}
                                     :remotes          {:remote (assoc remote
                                                                  ::cr/api-url api-url)}}))
        (app/mount! cr/Root node))))
