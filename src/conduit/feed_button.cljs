(ns conduit.feed-button
  (:require [com.fulcrologic.rad.attributes :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr label ::label :string
  {ao/identity? true})

(defattr href ::href :string
  {ao/identity? true})

