(ns javascript-externs-generator.util
  (:require [goog.async.Deferred]))

(defn ->deferred
  "Ensure a `goog.async.Deferred` from a possible regular value.
  Optional callback will be called when Deferred is resolved, defaults to identity"
  ([x]
   (->deferred x identity))
  ([x callback]
   (goog.async.Deferred/when x callback)))

(defn async-pipeline
  "Start with initial state `init`.
  Apply each fn in `seq-of-fns` sequentially.
  End by applying fn `fin`.

  Each fn in `seq-of-fns` builds up the accumulator `acc`, and each deferred is chained together. Each fn should return an accumulator, which can be a deferred.

  At any point if `acc` has a value for `errors`, acc is passed through without
  evaluating the remaining fns in `seq-of-fns`, but `fin` is called with the `acc`
  so you can respond to them."
  [init seq-of-fns fin]
  (->deferred
   (reduce (fn [deferred-acc cur]
             (.addCallback deferred-acc
                           (fn [acc]
                             ;; wrap to ensure return a deferred acc
                             (->deferred
                              (if (seq (:errors acc))
                                acc
                                (cur acc))))))
           (->deferred init identity)
           seq-of-fns)
   fin))
