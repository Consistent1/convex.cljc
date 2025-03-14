(ns convex.shell.req.str

  "Requests relating to strings."

  {:author "Adam Helinski"}

  (:import (java.io BufferedReader
                    StringReader
                    StringWriter))
  (:refer-clojure :exclude [sort])
  (:require [convex.cell             :as $.cell]
            [convex.cvm              :as $.cvm]
            [convex.shell.req.stream :as $.shell.req.stream]
            [convex.shell.resrc      :as $.shell.resrc]
            [convex.std              :as $.std]))


;;;;;;;;;;


(defn sort

  "Secret request for sorting a vector of strings."

  [ctx [str+]]

  (or (when-not ($.std/vector? str+)
        ($.cvm/exception-set ctx
                             ($.cell/code-std* :ARGUMENT)
                             ($.cell/* "Can only sort a vector")))
      ($.cvm/result-set ctx
                        ($.cell/vector
                          (map $.cell/string
                               (clojure.core/sort (map str
                                                       str+)))))))



(defn stream-in

  "Request for turning a string into an input stream."

  [ctx [string]]

  (or (when-not ($.std/string? string)
        ($.cvm/exception-set ctx
                             ($.cell/code-std* :ARGUMENT)
                             ($.cell/* "String input stream requires a string")))
      ($.cvm/result-set ctx
                        ($.shell.resrc/create (-> string
                                                  (str)
                                                  (StringReader.)
                                                  (BufferedReader.))))))



(defn stream-out

  "Request for creating an output stream backed by a string."

  [ctx _arg+]

  ($.cvm/result-set ctx
                    ($.shell.resrc/create (StringWriter.))))



(defn stream-unwrap

  "Request for extracting the string inside a [[stream-out]]."

  [ctx [handle]]

  ($.shell.req.stream/operation ctx
                                handle
                                #{:unwrap}
                                (fn [ctx-2 stream]
                                  ($.cvm/result-set ctx-2
                                                    ($.cell/string (str stream))))))
