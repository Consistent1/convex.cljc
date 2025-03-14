(ns convex.shell.dep        

  "Experimental dependency management framework for Convex Lisp.
  
   In the Shell, see `(?.shell '.dep)`."

  {:author "Adam Helinski"}

  (:import (convex.core.init Init)
           (convex.core.lang.impl ErrorValue))
  (:refer-clojure :exclude [read])
  (:require [clojure.string            :as string]
            [convex.cell               :as $.cell]
            [convex.cvm                :as $.cvm]
            [convex.shell.dep.fail     :as $.shell.dep.fail]
            [convex.shell.dep.git      :as $.shell.dep.git]
            [convex.shell.dep.local    :as $.shell.dep.local]
            [convex.shell.dep.relative :as $.shell.dep.relative]
            [convex.shell.flow         :as $.shell.flow]
            [convex.shell.project      :as $.shell.project]
            [convex.std                :as $.std]))


(declare fetch)


;;;;;;;;;; Private


(defn- -jump

  ;; Used and forwarded by [[fetch]].
  ;; This is how a dependency can jump and pass resolution to another dependency.

  [env dep-parent actor-sym actor-path]

  (-> env
      (assoc :convex.shell/dep          dep-parent
             :convex.shell.dep/required ($.cell/* [~actor-sym
                                                   ~($.std/next actor-path)]))
      (fetch)
      (assoc :convex.shell/dep
             (env :convex.shell/dep))))


;;;;;;;;;; Miscellaneous


(defn project

  "Returns a `project.cvx` file where dependencies reside.
  
   Also validates it."

  [ctx dep dir]

  (let [fail    (fn [code message]
                  ($.shell.flow/fail ctx
                                     code
                                     ($.std/assoc message
                                                  ($.cell/* :dep)
                                                  dep)))
        project ($.shell.project/read dir
                                      fail)]
    ($.shell.project/dep+ project
                          fail)
    project))


;;;;;;;;;; Fetching actors


(defn fetch

  "Main function for fetching dependencies (Convex Lisp files), which may or may not be
   deployed as actors in a latter step."


  ([env]

   (if-some [required (env :convex.shell.dep/required)]
     ;; 
     ;; Need to resolve an actor path.
     ;;
     (recur
       (let [[actor-sym
              actor-path
              & required-2] required
             dep-child      (env :convex.shell/dep)
             project-child  (get-in env
                                    [:convex.shell.dep/dep->project
                                     dep-child])
             project-sym    (first actor-path)
             dep-parent     (get-in project-child
                                    [($.cell/* :deps)
                                     project-sym])
             _              (when-not dep-parent
                              ($.shell.dep.fail/with-ancestry (env :convex.shell/ctx)
                                                              ($.cell/code-std* :ARGUMENT)
                                                              ($.cell/string (format "Dependency alias not found: %s"
                                                                                     project-sym))
                                                              (env :convex.shell.dep/ancestry)))

             fetch-parent  (get-in env
                                   [:convex.shell.dep/resolver+
                                    (first dep-parent)])]
         (-> env
             (update :convex.shell.dep/ancestry
                     $.std/conj
                     ($.cell/* [~(env :convex.shell/dep)
                                ~actor-path]))
             (fetch-parent project-child
                           dep-parent
                           actor-sym
                           actor-path)
             (assoc :convex.shell.dep/ancestry (env :convex.shell.dep/ancestry)
                    :convex.shell.dep/required required-2))))
     ;;
     ;; No actor is required.
     ;;
     env))


  ([env required]

   (if (and ($.std/vector? required) 
            ($.std/empty? required))
     env
     (let [ancestry ($.cell/* [])
           ctx      (env :convex.shell/ctx)]
       ($.shell.dep.relative/validate-required ctx
                                               required
                                               ancestry)
       (fetch (-> env
                  (update :convex.shell.dep/resolver+
                          (fn [resolver+]
                            (-> resolver+
                                (assoc ($.cell/* :git)   $.shell.dep.git/fetch
                                       ($.cell/* :local) $.shell.dep.local/fetch)
                                (update ($.cell/* :relative)
                                        #(or %
                                             $.shell.dep.relative/fetch)))))
                  (merge {:convex.shell/dep               ($.cell/* :root)
                          :convex.shell.dep/ancestry      ancestry
                          :convex.shell.dep/dep->project  {($.cell/* :root) (project ctx
                                                                                     ($.cell/* :root)
                                                                                     (str ($.cvm/look-up ctx
                                                                                                         Init/CORE_ADDRESS
                                                                                                         ($.cell/* .project.*dir*))))}
                          :convex.shell.dep/fetch         fetch
                          :convex.shell.dep/foreign?      false
                          :convex.shell.dep/hash          ($.cell/* :root)
                          :convex.shell.dep/jump          -jump
                          :convex.shell.dep/read-project  project
                          :convex.shell.dep/required      required
                          :convex.shell.dep.hash/pending+ #{}})))))))


;;;;;;;;;; Deploying actors


(defn deploy-actor

  "Used in [[deploy-fetched]] for deploying a single actor in the Shell."

  [env hash code]

  (let [ctx     ($.cvm/deploy (env :convex.shell/ctx)
                              code)
        ex      ($.cvm/exception ctx)
        _       (when ex
                  ($.shell.flow/fail ctx
                                     (doto ^ErrorValue ex
                                       (.addTrace (str "Deploying: "
                                                       (string/join " <- "
                                                                    (-> env
                                                                        (get-in [:convex.shell.dep/hash->ancestry
                                                                                hash])
                                                                        (reverse))))))))
        address ($.cvm/result ctx)]
    (-> env
        (assoc :convex.shell/ctx         ctx
               :convex.shell.dep/address address)
        (assoc-in [:convex.shell.dep/hash->address
                   hash]
                  address))))



(defn deploy-fetched

  "Deploys actors that have prefetched with [[fetched]]."

  [env]

  (let [hash    (env :convex.shell.dep/hash)
        address (get-in env
                        [:convex.shell.dep/hash->address
                         hash])]
    (if address
      ;;
      (assoc env
             :convex.shell.dep/address
             address)
      ;;
      (if-some [binding+ (get-in env
                                 [:convex.shell.dep/hash->binding+
                                  hash])]
        ;;
        (let [env-2   (reduce (fn [env-2 [actor-sym hash]]
                                (let [env-3 (-> env-2
                                                (assoc :convex.shell.dep/hash
                                                       hash)
                                                (deploy-fetched))]
                                  (assoc env-3
                                         :convex.shell.dep/let
                                         (cond->
                                           (env-2 :convex.shell.dep/let)
                                           (not= actor-sym
                                                 ($.cell/* _))
                                           (conj actor-sym
                                                 (env-3 :convex.shell.dep/address))))))
                               (assoc env
                                      :convex.shell.dep/let
                                      [])
                               binding+)
              src     (get-in env
                              [:convex.shell.dep/hash->file
                               hash
                               ($.cell/* :src)])]
          (if src
            (-> (deploy-actor env-2
                               hash
                               ($.std/concat ($.cell/* (let ~($.cell/vector (env-2 :convex.shell.dep/let))))
                                             src))
                (assoc :convex.shell.dep/hash
                       hash))
            env-2))
        ;;
        (if-some [src (get-in env
                              [:convex.shell.dep/hash->file
                               hash
                               ($.cell/* :src)])]
          (deploy-actor env
                        hash
                        ($.std/cons ($.cell/* do)
                                    src))
          env)))))
