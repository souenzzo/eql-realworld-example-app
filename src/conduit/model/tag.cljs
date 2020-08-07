(ns conduit.model.tag
  (:require [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr tag :conduit.tag/tag :string
  {ao/identity? true})
