(ns convex.shell.req.juice

  "Requests relating to juice."

  {:author "Adam Helinski"}

  (:refer-clojure :exclude [set])
  (:require [convex.clj  :as $.clj]
            [convex.cell :as $.cell]
            [convex.cvm  :as $.cvm]
            [convex.std  :as $.std]))


;;;;;;;;;;


(defn set

  "Request for setting the current juice value."

  [ctx [n-unit]]

  (or (when-not ($.std/long? n-unit)
        ($.cvm/exception-set ctx
                             ($.cell/code-std* :ARGUMENT)
                             ($.cell/* "Setting juice requires a long")))
      (let [n-unit-2 ($.clj/long n-unit)]
        (or (when (neg? n-unit-2)
              ($.cvm/exception-set ctx
                                   ($.cell/code-std* :ARGUMENT)
                                   ($.cell/* "Juice units cannot be < 1")))
            (when (> n-unit-2
                     ($.cvm/juice-limit ctx))
              ($.cvm/exception-set ctx
                                   ($.cell/code-std* :ARGUMENT)
                                   ($.cell/* "Juice used cannot be > juice limit")))
            ($.cvm/juice-set ctx
                             ($.clj/long n-unit))))))



(defn track

  "Request for tracking juice cost of a transaction.
  
   See `.juice.track`."

  [ctx [trx]]

  (let [j-1   ($.cvm/juice ctx)   
        ctx-2 (-> ctx
                  ($.cvm/fork)
                  ($.cvm/eval trx))]
    ($.cvm/result-set ctx
                      ($.cell/* [~($.cvm/result ctx-2)
                                 ~($.cell/long (- ($.cvm/juice ctx-2)
                                                  j-1))]))))
