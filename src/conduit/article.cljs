(ns conduit.article
  (:require [com.fulcrologic.rad.attributes :refer [defattr]]
            [com.fulcrologic.rad.attributes-options :as ao]))

(defattr slug ::slug :string
  {ao/identity? true})

(defattr title ::title :string
  {})

(defattr description ::description :string
  {})

(defattr favorites-count ::favorites-count :number
  {})

(defattr created-at ::created-at :instant
  {})
