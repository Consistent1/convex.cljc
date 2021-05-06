(ns convex.lisp.test.core.coll

  ""

  {:author "Adam Helinski"}

  (:require [clojure.test                    :as t]
            [clojure.test.check.clojure-test :as tc.ct]
            [convex.lisp.test.eval           :as $.test.eval]
            [convex.lisp.test.prop           :as $.test.prop]))


(def max-size-coll 5)


;;;;;;;;;;


(t/deftest blob-map--

  (t/is (map? ($.test.eval/form '(blob-map)))))



(tc.ct/defspec hash-map--

  ;; Cannot compare with Clojure: https://github.com/Convex-Dev/convex-web/issues/66

  {:max-size max-size-coll}

  ($.test.prop/check [:+ [:cat
                          :convex/data
                          :convex/data]]
                     (fn [x]
                       (map? ($.test.eval/form (list* 'hash-map
                                                      (map #(list 'quote
                                                                  %)
                                                           x)))))))



(t/deftest hash-map--no-arg

  (t/is (= {}
           ($.test.eval/form '(hash-map)))))



(tc.ct/defspec hash-set--

  ;; Cannot compare with Clojure: https://github.com/Convex-Dev/convex-web/issues/66

  {:max-size max-size-coll}

  ($.test.prop/check [:vector
                      {:min 1}
                      :convex/data]
                     (fn [x]
                       (set? ($.test.eval/form (list* 'hash-set
                                                      (map #(list 'quote
                                                                  %)
                                                           x)))))))



(t/deftest hash-set--no-arg

  (t/is (= #{}
           ($.test.eval/form '(hash-set)))))



;; TODO. `log`, about logging



(tc.ct/defspec list--

  {:max-size max-size-coll}

  ($.test.prop/check [:vector
                      {:min 1}
		      	      :convex/data]
		      	     (fn [x]
		      	       (= (apply list
		      	       	   	    x)
		      	          ($.test.eval/form (list* 'list
 		      	       							  (map #(list 'quote
                                                               %)
                                                        x)))))))



(tc.ct/defspec vector--

  {:max-size max-size-coll}

  ($.test.prop/check [:vector
                      {:min 1}
		      		  :convex/data]
		      	     (fn [x]
		      	       (= (apply vector
                                 x)
		      	          ($.test.eval/form (list* 'vector
 		      	       							  (map #(list 'quote
                                                                %)
                                                        x)))))))

