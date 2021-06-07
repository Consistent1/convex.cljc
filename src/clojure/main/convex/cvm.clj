(ns convex.cvm

  "A CVM context is needed for compiling and executing Convex code.

   It can be created using [[ctx]].
  
   This namespace provide all needed utilities for such endeavours as well few functions for
   querying useful properties, such as [[juice]].
  
   A context is mostly immutable, albeit some aspects such as juice tracking are mutable for performance
   reason. Operations that modifies a context (expansion, compilation, or any form of execution) returns
   a new instance and the old one should be discarded

   Such operations consume juice and lead either to a successful [[result]] or to an [[error]]. Functions that
   do not return a context (eg. [[env]], [[juice]]) do not consume juice.

   Result objects (Convex objects) can be datafied with [[convex.lisp/datafy]] for easy consumption from Clojure."

  {:author "Adam Helinski"}

  (:import (convex.core Init)
           (convex.core.data AccountStatus
                             ACell
                             Address
                             AHashMap
                             Symbol)
           (convex.core.lang Context
                             Reader)
           java.io.File)
  (:refer-clojure :exclude [compile
                            eval
                            import
                            read])
  (:require [clojure.core.protocols]
            [convex.cvm.type         :as $.cvm.type]
            [convex.lisp             :as $.lisp]
            [hawk.core               :as watcher]))


(set! *warn-on-reflection*
      true)


(declare run)


;;;;;;;;;; Creating a new context


(defn ctx

  "Creates a \"fake\" context. Ideal for testing and repl'ing around."


  (^Context []

   (ctx Init/HERO))


  (^Context [account]

   (ctx (Init/createState)
        account))

  
  (^Context [state account]

   (Context/createFake state
                       account)))



(defn fork

  "Duplicates the given [[ctx]] (very cheap).

   Any operation on the returned copy has no impact on the original context."

  ^Context [^Context ctx]

  (.fork ctx))


;;;;;;;;;; Querying context properties


(defn account

  "Returns the account for the given `address` (or the return value of [[address]] if none is provided)."

  
  (^AccountStatus [^Context ctx]

   (.getAccountStatus ctx))


  (^AccountStatus [^Context ctx address]

    (.getAccountStatus ctx
                       (cond->
                         address
                         (number? address)
                         $.cvm.type/address))))



(defn address
  
  "Returns the executing address of the given `ctx`."

  ^Address

  [^Context ctx]

  (.getAddress ctx))



(defn env

  "Returns the environment of the executing account attached to `ctx`."


  (^AHashMap [^Context ctx]

   (.getEnvironment ctx))


  (^AHashMap [ctx address]

   (.getEnvironment (account ctx
                             address))))



(defn exception

  "The CVM enters in exceptional state in case of error or particular patterns such as
   halting or doing a rollback.

   Returns the current exception or nil if `ctx` is not in such a state meaning that [[result]]
   can be safely used.
  
   An exception code can be provided as a filter, meaning that even if an exception occured, this
   functions will return nil unless that exception had the given `code`.
  
   Also see [[std-code*]] for easily retrieving an official error code. Note that in practise, unlike the CVM
   itself or any of the core function, a user Convex function can return anything as a code."


  ([^Context ctx]

   (when (.isExceptional ctx)
     (.getExceptional ctx)))


  ([^ACell code ^Context ctx]

   (when (.isExceptional ctx)
     (let [e (.getExceptional ctx)]
       (when (= (.getCode e)
                code)
         e)))))



(defn exception?

  "Returns true if the given `ctx` is in an exceptional state.

   See [[exception]]."


  ([^Context ctx]

   (.isExceptional ctx))


  ([^ACell code ^Context ctx]

   (if (.isExceptional ctx)
     (= code
        (.getCode (.getExceptional ctx)))
     false)))



(defn juice

  "Returns the remaining amount of juice available for the executing account.
  
   See [[set-juice]]."

  [^Context ctx]

  (.getJuice ctx))



(defn log

  "Returns the log of `ctx` (a map of `address` -> `vector of values)."

  [^Context ctx]

  (.getLog ctx))



(defn result

  "Extracts the result (eg. after expansion, compilation, execution, ...) wrapped in a `ctx`.
  
   Throws if the `ctx` is in an exceptional state. See [[exception]]."

  [^Context ctx]

  (.getResult ctx))



(defn state

  "Returns the whole CVM state associated with `ctx`."

  [^Context ctx]

  (.getState ctx))


;;;;;;;;;; Modifying context properties after fork


(defn set-juice

  "Forks and sets the juice of the copied context to the requested amount"

  [^Context ctx juice]

  (.withJuice (fork ctx)
              juice))


;;;;;;;;;; Phase 1 - Reading Convex Lisp 


(defn read

  "Converts Convex Lisp source to a Convex object.

   Such an object can be used as is, using its Java API. More often, is it converted to Clojure or
   compiled and executed on the CVM. See the [[convex.cvm]] namespace."

  [string]

  (let [parsed (Reader/readAll string)]
    (if (second parsed)
      (.cons parsed
             (Symbol/create "do"))
      (first parsed))))



(defn read-form

  "Stringifies the given Clojure form to Convex Lisp source and applies the result to [[read]]."

  [form]

  (-> form
      $.lisp/src
      read))


;;;;;;;;;; Phase 2 & 3 - Expanding Convex objects and compiling into operations


(defn compile

  "Compiles an expanded Convex object using the given `ctx`.

   Object must be canonical (all items are fully expanded). See [[expand]].
  
   See [[run]] for execution after compilation.

   Returns `ctx`, result being the compiled object."


  (^Context [ctx]

    (compile ctx
             (result ctx)))


  (^Context [^Context ctx canonical-object]

   (.compile ctx
             canonical-object)))



(defn expand

  "Expands a Convex object so that it is canonical (fully expanded and ready for compilation).

   Usually run before [[compile]] with the result from [[read]].
  
   Returns `ctx`, result being the expanded object."


  (^Context [ctx]

   (expand ctx
           (result ctx)))


  (^Context [^Context ctx object]

   (.expand ctx
            object)))



(defn expand-compile

  "Chains [[expand]] and [[compile]] while being slightly more efficient than calling both separately.
  
   See [[run]] for execution after compilation.

   Returns `ctx`, result being the compiled object."

  
  (^Context [ctx]

   (expand-compile ctx
                   (result ctx)))


  (^Context [^Context ctx object]

   (.expandCompile ctx
                   object)))


;;;;;;;;;; Pahse 4 - Executing compiled code


(defn eval

  "Evaluates the given form after fully expanding and compiling it.
  
   Returns `ctx`, result being the evaluated object."


  (^Context [ctx]

   (eval ctx
         (result ctx)))


  (^Context [ctx object]

   (run (expand-compile ctx
                        object))))



(defn query

  "Like [[run]] but the resulting state is discarded.

   Returns `ctx`, result being the evaluated object in query mode."


  (^Context [ctx]

   (if (exception? ctx)
     ctx
     (query ctx
            (result ctx))))


  (^Context [^Context ctx compiled-object]

   (.query ctx
           compiled-object)))



(defn run

  "Runs compiled Convex code.
  
   Usually run after [[compile]].
  
   Returns `ctx`, result being the evaluated object."


  (^Context [ctx]

   (if (exception? ctx)
     ctx
     (run ctx
          (result ctx))))


  (^Context [^Context ctx compiled]

   (.run ctx
         compiled)))


;;;;;;;;;; Miscellaneous


(defmacro std-code*

  "Given a Clojure keyword, returns the corresponding standard error code (any of the Convex keyword the CVM itself
   can throw):
  
   - `:ARGUMENT`
   - `:ARITY`
   - `:ASSERT`
   - `:BOUNDS`
   - `:CAST`
   - `:COMPILE`
   - `:DEPTH`
   - `:EXCEPTION`
   - `:EXPAND`
   - `:FATAL`
   - `:FUNDS`
   - `:HALT`
   - `:JUICE`
   - `:MEMORY`
   - `:NOBODY`
   - `:RECUR`
   - `:REDUCED`
   - `:RETURN`
   - `:ROLLBACK`
   - `:SEQUENCE`
   - `:SIGNATURE`
   - `:STATE`
   - `:TAILCALL`
   - `:TODO`
   - `:TRUST`
   - `:UNDECLARED`
   - `:UNEXPECTED`
  
   Throws if keyword does not match any of those.
  
   Note that in user functions, codes can be anything, any type."

  [kw]

  (case kw
    :ARGUMENT   'convex.core.ErrorCodes/ARGUMENT
    :ARITY      'convex.core.ErrorCodes/ARITY
    :ASSERT     'convex.core.ErrorCodes/ASSERT
    :BOUNDS     'convex.core.ErrorCodes/BOUNDS
    :CAST       'convex.core.ErrorCodes/CAST
    :COMPILE    'convex.core.ErrorCodes/COMPILE
    :DEPTH      'convex.core.ErrorCodes/DEPTH
    :EXCEPTION  'convex.core.ErrorCodes/EXCEPTION
    :EXPAND     'convex.core.ErrorCodes/EXPAND
    :FATAL      'convex.core.ErrorCodes/FATAL
    :FUNDS      'convex.core.ErrorCodes/FUNDS
    :HALT       'convex.core.ErrorCodes/HALT
    :JUICE      'convex.core.ErrorCodes/JUICE
    :MEMORY     'convex.core.ErrorCodes/MEMORY
    :NOBODY     'convex.core.ErrorCodes/NOBODY
    :RECUR      'convex.core.ErrorCodes/RECUR
    :REDUCED    'convex.core.ErrorCodes/REDUCED
    :RETURN     'convex.core.ErrorCodes/RETURN
    :ROLLBACK   'convex.core.ErrorCodes/ROLLBACK
    :SEQUENCE   'convex.core.ErrorCodes/SEQUENCE
    :SIGNATURE  'convex.core.ErrorCodes/SIGNATURE
    :STATE      'convex.core.ErrorCodes/STATE
    :TAILCALL   'convex.core.ErrorCodes/TAILCALL
    :TODO       'convex.core.ErrorCodes/TODO
    :TRUST      'convex.core.ErrorCodes/TRUST
    :UNDECLARED 'convex.core.ErrorCodes/UNDECLARED
    :UNEXPECTED 'convex.core.ErrorCodes/UNEXPECTED
    (throw (ex-info (str "There is no official exception code for: "
                         kw)
                    {::code kw}))))
    

;;;;;;;;;; Converting Convex -> Clojure


(defn as-clojure

  "Converts a Convex object into Clojure data."

  [object]

  (clojure.core.protocols/datafy object))



(extend-protocol clojure.core.protocols/Datafiable


  nil

    (datafy [_this]
      nil)


  convex.core.data.ABlob

    (datafy [this]
      ($.lisp/blob (.toHexString this)))

  
  convex.core.data.Address

    (datafy [this]
      ($.lisp/address (.longValue this)))


  convex.core.data.AList

    (datafy [this]
      (map clojure.core.protocols/datafy
           this))


  convex.core.data.AMap

    (datafy [this]
      (reduce (fn [hmap [k v]]
                (assoc hmap
                       (clojure.core.protocols/datafy k)
                       (clojure.core.protocols/datafy v)))
              {}
              this))


  convex.core.data.ASet

    (datafy [this]
      (into #{}
            (map clojure.core.protocols/datafy)
            this))


  convex.core.data.AString

    (datafy [this]
      (.toString this))

  
  convex.core.data.AVector

    (datafy [this]
      (mapv clojure.core.protocols/datafy
            this))


  convex.core.data.Keyword

    (datafy [this]
      (-> this
          .getName
          clojure.core.protocols/datafy
          keyword))


  convex.core.data.Symbol

    (datafy [this]
      (let [name (.getName this)
            path (some-> (.getPath this)
                         .toString)]
        (symbol (if path
                  (str path
                       \/
                       name)
                  (str name)))))


  convex.core.data.Syntax

    (datafy [this]
      (let [mta   (.getMeta this)
            value (-> this 
                      .getValue
                      clojure.core.protocols/datafy)]
        (if (seq mta)
          (list 'syntax
                value
                (clojure.core.protocols/datafy mta))
          value)))



  convex.core.data.prim.CVMBool

    (datafy [this]
      (.booleanValue this))


  convex.core.data.prim.CVMByte

    (datafy [this]
      (.longValue this))


  convex.core.data.prim.CVMChar

    (datafy [this]
      (char (.longValue this)))


  convex.core.data.prim.CVMDouble

    (datafy [this]
      (.doubleValue this))


  convex.core.data.prim.CVMLong

    (datafy [this]
      (.longValue this))


  convex.core.lang.impl.CoreFn

    (datafy [this]
      (clojure.core.protocols/datafy (.getSymbol this)))


  convex.core.lang.impl.ErrorValue

    (datafy [this]
      {:convex.error/code    (clojure.core.protocols/datafy (.getCode this))
       :convex.error/message (clojure.core.protocols/datafy (.getMessage this))
       :convex.error/trace   (clojure.core.protocols/datafy (mapv clojure.core.protocols/datafy
                                                                  (.getTrace this)))})


  ;; TODO. Use EDN? Ops have protected fields meaning they cannot be readily translated.
  ;;
  convex.core.lang.impl.Fn

    (datafy [this]
      (-> this
          .toString
          read
          clojure.core.protocols/datafy)))


;;;;;;;;;; Converting Convex -> EDN


(defn as-edn

  "Translates a Convex object into an EDN string."
  
  [^ACell form]

  (.ednString form))


;;;;;;;;;; Importing Convex Lisp files as libraries


(defn form-import

  "Given a `path` to a Convex Lisp file and an alias, returns a form which deploys that code
   as an actor and then imports it using that alias."


  ([[path alias]]

   (form-import path
                alias))


  ([path alias]

   ($.lisp/templ* (let [addr (deploy (quote ~($.lisp/literal (slurp path))))]
                    (def *aliases*
                         (assoc *aliases*
                                (quote ~alias)
                                addr))))))



(defn import

  "Given a map of `file path` -> `alias symbol`, imports that code into the given `ctx` using
   [[form-import]].
  
   Creates a new [[ctx]] if none is provided.

   Also see [[watch]]."


  ([path->alias]

   (import (ctx)
           path->alias))


  ([ctx path->alias]

   (eval ctx
         (read-form ($.lisp/templ* (do
                                     ~@(map form-import
                                            path->alias)))))))


;;;;;;;;;; Watching Convex Lisp files and syncing with a context


(let [-ctx-watch (fn [f import+]
                   (f (eval (ctx)
                            (read-form ($.lisp/templ* (do
                                                        ~@(map :code
                                                               (vals import+))))))))]
  (defn watch

    "Starts a watcher which syncs Convex Lisp files to a context.

     Returns a object which can be `deref` into that synced context.
    
     The given files are first imported as libraries just like in [[import]]. Then, everytime one of those
     files is modified or deleted, a fresh context is created with all updates.
    
     Very useful for setting up a base context which loads a bunch of files and is then used for development and testing:

     ```clojure
     (def ctx
          (watch {\"some/lib.cvx\" 'lib}))

     (eval (fork @ctx)
           '(lib/my-func 42))
     ```

     Supported options are:

     | Key | Value |
     |---|---|
     | `:after-import` | Function which maps the prepared context after all imports |"


    ([path->alias]

     (watch path->alias
            nil))


    ([path->alias option+]

     (let [after-import (or (:after-import option+)
                            identity)
           import+      (reduce-kv (fn [import+ ^String path alias]
                                     (let [path-2 (.getCanonicalPath (File. path))]
                                       (assoc import+
                                              path-2
                                              {:alias alias
                                               :code  (form-import path-2
                                                                   alias)})))
                                   {}
                                   path->alias)
           *state       (atom {:ctx     (-ctx-watch after-import
                                                    import+)
                               :import+ import+})
           watcher      (watcher/watch! [{:handler (fn [_ {:keys [^File file
                                                                  kind]}]
                                                     (swap! *state
                                                            (fn [{:as   state
                                                                  :keys [import+]}]
                                                              (let [path      (.getCanonicalPath file)
                                                                    import-2+ (if (= kind
                                                                                     :delete)
                                                                                (update import+
                                                                                        path
                                                                                        dissoc
                                                                                        :code)
                                                                                (update import+
                                                                                        path
                                                                                        (fn [{:as   import-
                                                                                              :keys [alias]}]

                                                                                          (assoc import-
                                                                                                 :code
                                                                                                 (form-import path
                                                                                                              alias)))))]
                                                                (assoc state
                                                                       :ctx     (-ctx-watch after-import
                                                                                            import-2+)
                                                                       :import+ import-2+))))
                                                     nil)
                                          :paths   (keys import+)}])]
       (reify


         clojure.lang.IDeref

           (deref [_]
             (@*state :ctx))
         

         java.lang.AutoCloseable

           (close [_]
             (watcher/stop! watcher)))))))
