(ns conduit.model.article
  (:require [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr slug :conduit.article/slug :string
  {ao/identity? true})

(defattr title :conduit.article/title :string
  {})
