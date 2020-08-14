(ns conduit.tag
  (:require [com.fulcrologic.rad.attributes :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr tag ::tag :string
  {ao/identity? true})
