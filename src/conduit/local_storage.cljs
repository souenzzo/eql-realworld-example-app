(ns conduit.local-storage
  (:refer-clojure :exclude [get set!]))

(defn ->key
  [x]
  (str (namespace x)
       "/"
       (name x)))

(defn get
  [k]
  (-> k
      ->key
      js/localStorage.getItem
      js/JSON.parse))

(defn set!
  [k value]
  (-> k
      ->key
      (js/localStorage.setItem (js/JSON.stringify value))))
