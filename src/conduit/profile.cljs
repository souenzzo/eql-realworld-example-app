(ns conduit.profile
  (:require [com.fulcrologic.rad.attributes :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr image ::image :string
  {})

(defattr username ::username :string
  {ao/identity? true})
