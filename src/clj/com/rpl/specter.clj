(ns com.rpl.specter
  (:use [com.rpl.specter impl protocols])
  )

;;TODO: can make usage of vals much more efficient by determining during composition how many vals
;;there are going to be. this should make it much easier to allocate space for vals without doing concats
;;all over the place. The apply to the vals + structure can also be avoided since the number of vals is known
;;beforehand
(defn comp-paths [& paths]
  (comp-paths* (vec paths)))

;; Selector functions

(defn select [selector structure]
  (let [sp (comp-paths* selector)]
    (select-full* sp
                  []
                  structure
                  (fn [vals structure]
                    (if-not (empty? vals) [(conj vals structure)] [structure])))
    ))

(defn select-one
  "Like select, but returns either one element or nil. Throws exception if multiple elements returned"
  [selector structure]
  (let [res (select selector structure)]
    (when (> (count res) 1)
      (throw-illegal "More than one element found for params: " selector structure))
    (first res)
    ))

(defn select-one!
  "Returns exactly one element, throws exception if zero or multiple elements returned"
  [selector structure]
  (let [res (select-one selector structure)]
    (when (nil? res) (throw-illegal "No elements found for params: " selector structure))
    res
    ))

(defn select-first
  "Returns first element returned. Not any more efficient than select, just a convenience"
  [selector structure]
  (first (select selector structure)))

;; Update functions

(defn update [selector update-fn structure]
  (let [selector (comp-paths* selector)]
    (update-full* selector
                  []
                  structure
                  (fn [vals structure]
                    (if (empty? vals)
                      (update-fn structure)
                      (apply update-fn (conj vals structure)))
                    ))))

(defn setval [selector val structure]
  (update selector (fn [_] val) structure))

(defn replace-in [selector update-fn structure & {:keys [merge-fn] :or {merge-fn concat}}]
  "Returns [new structure [<user-ret> <user-ret>...]"
  (let [state (mutable-cell nil)]
    [(update selector
             (fn [e]
               (let [res (update-fn e)]
                 (if res
                   (let [[ret user-ret] res]
                     (->> user-ret
                          (merge-fn (get-cell state))
                          (set-cell! state))
                     ret)
                   e
                   )))
             structure)
     (get-cell state)]
    ))

;; Built-in pathing and context operations

(def ALL (->AllStructurePath))

(def VAL (->ValCollect))

(def LAST (->LastStructurePath))

(def FIRST (->FirstStructurePath))

(defn srange-dynamic [start-fn end-fn] (->SRangePath start-fn end-fn))

(defn srange [start end] (srange-dynamic (fn [_] start) (fn [_] end)))

(def START (srange 0 0))

(def END (srange-dynamic count count))

(defn walker [afn] (->WalkerStructurePath afn))

(defn codewalker [afn] (->CodeWalkerStructurePath afn))

(defn filterer [afn] (->FilterStructurePath afn))

(defn keypath [akey] (->KeyPath akey))

(defn view [afn] (->ViewPath afn))

(defmacro viewfn [& args]
  `(view (fn ~@args)))

(defn split [& selectors]
  (->SplitPath (->> selectors (map comp-paths*) doall)))

(defn selected?
  "Filters the current value based on whether a selector finds anything.
  e.g. (selected? :vals ALL even?) keeps the current element only if an
  even number exists for the :vals key"
  [& selectors]
  (let [s (comp-paths selectors)]
    (fn [structure]
      (->> structure
           (select s)
           empty?
           not))))

(extend-type clojure.lang.Keyword
  StructurePath
  (select* [kw structure next-fn]
    (key-select kw structure next-fn))
  (update* [kw structure next-fn]
    (key-update kw structure next-fn)
    ))

(extend-type clojure.lang.AFn
  StructurePath
  (select* [afn structure next-fn]
    (if (afn structure)
      (next-fn structure)))
  (update* [afn structure next-fn]
    (if (afn structure)
      (next-fn structure)
      structure)))

(defn collect [& selector]
  (->SelectCollector select (comp-paths* selector)))

(defn collect-one [& selector]
  (->SelectCollector select-one (comp-paths* selector)))
