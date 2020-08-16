(ns conduit.storage
  (:refer-clojure :exclude [atom]))

(defn atom
  [{::keys [storage key-name]}]
  (reify
    IDeref
    (-deref [this]
      (.getItem storage key-name))
    IReset
    (-reset! [this value]
      (.setItem storage key-name value)
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
    #_Object #_(equiv [this other])
    #_IAtom
    #_IEquiv #_(-equiv [o other])
    #_IWatchable #_(-notify-watches [this oldval newval]) #_(-add-watch [this key f]) #_(-remove-watch [this key])
    #_IHash #_(-hash [this])))
