(ns conduit.storage
  (:refer-clojure :exclude [atom])
  (:require [cognitect.transit :as t]))

(defn atom
  [{::keys [storage
            type
            key-name]
    :as    opts}]
  (let [w (t/writer type opts)
        r (t/reader type opts)]
    (reify
      IDeref
      (-deref [this]
        (t/read r (.getItem storage key-name)))
      IReset
      (-reset! [this value]
        (.setItem storage key-name (t/write w value))
        value)
      ISwap
      (-swap! [this f]
        (reset! this (f @this)))
      (-swap! [this f a]
        (reset! this (f @this a)))
      (-swap! [this f a b]
        (reset! this (f @this a b)))
      (-swap! [this f a b xs]
        (reset! this (apply f @this a b xs)))
      #_Object
      #_(equiv [this other])
      #_IAtom
      #_IEquiv
      #_(-equiv [o other])
      #_IWatchable
      #_(-notify-watches [this oldval newval])
      #_(-add-watch [this key f])
      #_(-remove-watch [this key])
      #_IHash
      #_(-hash [this]))))
