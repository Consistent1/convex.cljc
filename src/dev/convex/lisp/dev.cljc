(ns convex.lisp.dev

  "CLJC playground during dev."

  {:author "Adam Helinski"}

  (:require #?(:clj [convex.lisp                      :as $])
            [convex.lisp.ctx                          :as $.ctx]
            [convex.lisp.edn                          :as $.edn]
            [convex.lisp.form                         :as $.form]
            [convex.lisp.hex                          :as $.hex]
            [convex.lisp.schema                       :as $.schema]
            #?@(:clj [[convex.lisp.test]
                      [convex.lisp.test.core.account]
                      [convex.lisp.test.core.actor]
                      [convex.lisp.test.core.coerce]
                      [convex.lisp.test.core.coll]
                      [convex.lisp.test.core.fn]
                      [convex.lisp.test.core.math]
                      [convex.lisp.test.core.pred]
                      [convex.lisp.test.data]
                      [convex.lisp.test.edn]
                      [convex.lisp.test.eval]
                      [convex.lisp.test.mult]
                      [convex.lisp.test.prop]
                      [convex.lisp.test.schema        :as $.test.schema]
                      [convex.lisp.test.util]
			          [clojure.data]])
            [clojure.pprint]
            #?(:clj [clojure.reflect])
            [clojure.test.check.generators            :as tc.gen]
            [clojure.walk]
            [clojure.test.check.properties            :as tc.prop]
            [clojure.test.check.results               :as tc.result]
            [malli.core                               :as malli]
            [malli.generator                          :as malli.gen])
  #?(:clj (:import clojure.lang.RT
                   (convex.core Init
                                State)
                   convex.core.data.Symbol)))


#?(:clj (set! *warn-on-reflection*
              true))


;;;;;;;;;;


(def ppr
     clojure.pprint/pprint)


;;;;;;;;;;


#?(:clj (comment



  (-> State
      clojure.reflect/reflect
      clojure.pprint/pprint)



  (-> Init/STATE
      $/datafy
      :accounts
      (->> (into []
                 (comp (map :environment)
                       (filter some?))))
      clojure.pprint/pprint)



  (-> Init/STATE
      $.edn/write
      ;(->> (spit "/tmp/convex.edn"))
      $.edn/read
      ;clojure.pprint/pprint
      )
  


  (def tx
       ($.form/templ {'?amount (malli.gen/generate [:int
                                                    {:min 10
                                                     :max 1000}])
                      '?key    (malli.gen/generate ($.test.schema/generator :convex/hexstring-32))}
                     '(do
                        (let [addr (create-account ?key)]
                          (transfer addr
                                    ?amount)
                          [*balance*
                           (balance addr)]))))
                


  (def ctx
       ($.ctx/create-fake))
                


  (->> '(inc 42)
       $/read-form
       ($/eval ($.ctx/fork ctx))
       $.ctx/result
       $/datafy
       )



  (-> ($/expand-compile ($.ctx/create-fake)
                        ($/read "(+ 1 1)"))
      $/query
      $.ctx/result
      $/datafy
      )


  (->> '(defn bar [x] (inc x))
       $/read-form
       ($/eval ($.ctx/create-fake))
       $.ctx/env
       $/datafy
       )

  
  (time
    (do
      ;(malli/validate [:not [:enum 1 2]]
      ;                3
      (malli.gen/generate [:and
                           {:registry {::data    [:or
                                                  ::int
                                                  ::vector]
                                       ::int     :int
                                       ::vector  [:vector
                                                  [:ref ::data]]}}
                           [:or ::data]
                           [:fn
                            (fn [_]
                              false)]]


                          {:registry (-> (malli/default-schemas)
                                         $.schema/registry
                                         )
                           :size     5
                           })
      nil))




  ))
