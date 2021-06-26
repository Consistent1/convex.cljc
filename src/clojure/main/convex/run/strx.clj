(ns convex.run.strx

  "Special transactions interpreted by the runner."

  {:author "Adam Helinski"}

  (:import (convex.core ErrorCodes)
           (convex.core.data AVector))
  (:require [convex.code     :as $.code]
            [convex.cvm      :as $.cvm]
            [convex.run.ctx  :as $.run.ctx]
            [convex.run.err  :as $.run.err]
            [convex.run.exec :as $.run.exec]
            [convex.run.kw   :as $.run.kw]))

;;;;;;;;;; Miscellaneous


(defn screen-clear

  ""

  ;; https://www.delftstack.com/howto/java/java-clear-console/

  []

  (print "\033[H\033[2J")
  (flush))


;;;;;;;;;; Helpers used in special transaction implementations


(defn restore

  ""


  ([env kw hook]

   (restore env
            kw
            hook
            hook))


  ([env kw restore? hook]

   (cond->
     env
     restore?
     (-> (assoc kw
                hook)
         (update :convex.run/restore
                 dissoc
                 kw)))))


;;;;;;;;;; Setup


(defmethod $.run.exec/strx nil

  ;; No special request, simply finalize a regular transaction.

  [env _result]

  (let [juice-last (- Long/MAX_VALUE  ;; Juice is always refilled to max prior to evaluation.
                      ($.cvm/juice (env :convex.sync/ctx)))
        env-2      (-> env
                       (assoc :convex.run/juice-last
                              juice-last)
                       (update :convex.run/juice-total
                               +
                               juice-last)
                       (update :convex.run/i-trx
                               inc))
        hook       (env-2 :convex.run.hook/trx)]
    (if hook
      (-> env-2
          (dissoc :convex.run.hook/trx)
          ($.run.exec/trx hook)
          (assoc :convex.run.hook/trx
                 hook))
      env-2)))



(defmethod $.run.exec/strx :unknown

  ;; Unknown special request.

  [env tuple]

  ($.run.err/signal env
                    ($.run.err/strx ErrorCodes/ARGUMENT
                                    tuple
                                    ($.code/string "Unsupported special transaction"))))

;;;;;;;;;; Implementations


(defmethod $.run.exec/strx

  $.run.kw/dep

  [env ^AVector tuple]

  ($.run.err/signal env
                    ($.run.err/strx ErrorCodes/STATE
                                    tuple
                                    ($.code/string "CVM special command 'strx/dep' can only be used as the very first transaction"))))



(defmethod $.run.exec/strx

  $.run.kw/do

  [env ^AVector tuple]

  ($.run.exec/trx+ env
                   (.get tuple
                         2)))



(defmethod $.run.exec/strx

  $.run.kw/env

  [env ^AVector tuple]

  ($.run.ctx/def-current env
                         {(.get tuple
                                2)
                          (if-some [env-var (.get tuple
                                                  3)]
                            ($.code/string (System/getenv (str env-var)))
                            ($.code/map (map (fn [[k v]]
                                               [($.code/string k)
                                                ($.code/string v)])
                                             (System/getenv))))}))


(defmethod $.run.exec/strx
  
  $.run.kw/hook-end

  [env ^AVector tuple]

  (let [path-restore [:convex.run/restore
                      :convex.run.hook/end]
        hook-restore (get-in env
                             path-restore)
        trx+         (.get tuple
                           2)]
    (if (and trx+
             (not= trx+
                   [nil]))
      (let [hook-old (or hook-restore
                         (env :convex.run.hook/end))]
        (-> env
            (cond->
              (not hook-restore)
              (assoc-in path-restore
                        hook-old))
            (assoc :convex.run.hook/end
                   (fn hook-new  [env-2]
                     (hook-old ($.run.exec/trx+ (dissoc env-2
                                                        :convex.run/error)
                                                trx+))))))
      (restore env
               :convex.run.hook/end
               hook-restore))))



(defmethod $.run.exec/strx
  
  $.run.kw/hook-error

  ;; TODO. Ensure failing hook is handled properly.

  [env ^AVector tuple]

  (let [path-restore [:convex.run/restore
                      :convex.run.hook/error]
        hook-restore (get-in env
                             path-restore)
        f            (.get tuple
                           2)]
    (if f
      (-> env
          (cond->
            (not hook-restore)
            (assoc-in path-restore
                      (env :convex.run.hook/error)))
          (assoc :convex.run.hook/error
                 (fn hook [env-2]
                   (let [err   (env-2 :convex.run/error)
                         ctx   ($.cvm/invoke (-> env-2
                                                 :convex.sync/ctx
                                                 $.cvm/juice-refill)
                                             f
                                             ($.cvm/arg+* err))
                         env-3 (assoc env-2
                                      :convex.sync/ctx
                                      ctx)
                         ex    ($.cvm/exception ctx)]
                     (if ex
                       ($.run.err/fatal env-3
                                        ($.run.err/error ex)
                                        ;err
                                        ($.code/string "Calling error hook failed")
                                        (-> ex
                                            $.run.err/error
                                            ($.run.err/assoc-cause (env-2 :convex.run/error))))
                       (let [form  ($.cvm/result ctx)
                             env-4 (-> env-3
                                       (assoc :convex.run.hook/error
                                              identity)
                                       (dissoc :convex.run/error)
                                       ($.run.exec/strx form)
                                       (assoc :convex.run.hook/error
                                              hook))
                             err-2 (env-4 :convex.run/error)]
                         (if err-2
                           ($.run.err/fatal env-4
                                            form
                                            ($.code/string "Evaluating output from error hook failed")
                                            ($.run.err/assoc-cause err-2
                                                                   err))
                           (assoc env-4
                                  :convex.run/error
                                  err))))))))
      (restore env
               :convex.run.hook/error
               hook-restore))))



(defmethod $.run.exec/strx
  
  $.run.kw/hook-out

  [env ^AVector tuple]

  (let [path-restore [:convex.run/restore
                      :convex.run.hook/out]
        hook-restore (get-in env
                             path-restore)
        f            (.get tuple
                           2)]
    (if f
      (let [hook-old (or hook-restore
                         (env :convex.run.hook/out))]
        (-> env
            (cond->
              (not hook-restore)
              (assoc-in path-restore
                        hook-old))
            (assoc :convex.run.hook/out
                   (fn hook-new [env-2 x]
                     (let [ctx    ($.cvm/invoke (-> env-2
                                                    :convex.sync/ctx
                                                    $.cvm/juice-refill)
                                                f
                                                ($.cvm/arg+* x))
                           env-3  (assoc env-2
                                         :convex.sync/ctx
                                         ctx)
                           ex     ($.cvm/exception ctx)]
                       (if ex
                         (-> env-3
                             (assoc :convex.run.hook/out
                                    hook-old)
                             ($.run.err/fatal ($.code/list [f
                                                            x])
                                              ($.code/string "Calling output hook failed, using default output")
                                              ($.run.err/error ex))
                             (assoc :convex.run.hook/out
                                    hook-new))
                         (hook-old env-3
                                   ($.cvm/result ctx))))))))
      (restore env
               :convex.run.hook/out
               hook-restore))))



(defmethod $.run.exec/strx
  
  $.run.kw/hook-result

  [env ^AVector tuple]

  (let [path-restore [:convex.run/restore
                      :convex.run.hook/trx]
        trx-restore  (get-in env
                             path-restore
                             ::nil)
        trx-restore? (not (identical? trx-restore
                                      ::nil))
        trx-new      (.get tuple
                           2)]
    (if trx-new
      (-> env
          (cond->
            (not trx-restore?)
            (assoc-in path-restore
                      (env :convex.run.hook/trx)))
          (assoc :convex.run.hook/trx
                 trx-new))
      (restore env
               :convex.run.hook/trx
               trx-restore?
               trx-restore))))



(defmethod $.run.exec/strx
  
  $.run.kw/log

  [env ^AVector tuple]

  ($.run.ctx/def-current env
                         {(.get tuple
                                2)
                          ($.cvm/log (env :convex.sync/ctx))}))



(defmethod $.run.exec/strx

  $.run.kw/out

  [env ^AVector tuple]

  ((env :convex.run.hook/out)
   env
   (.get tuple
         2)))



(defmethod $.run.exec/strx

  $.run.kw/read
  
  [env ^AVector tuple]

  (let [[err
         code] (try
                 [nil
                  (-> (.get tuple
                            3)
                      str
                      $.cvm/read
                      $.code/vector)]
                  (catch Throwable _err
                    [($.run.err/strx ErrorCodes/ARGUMENT
                                     tuple
                                     ($.code/string "Unable to read source"))
                     nil]))]
    (if err
      ($.run.err/signal env
                        err)
      ($.run.ctx/def-current env
                             {(.get tuple
                                    2)
                              code}))))



(defmethod $.run.exec/strx
  
  $.run.kw/screen-clear

  [env _tuple]

  (screen-clear)
  env)



(defmethod $.run.exec/strx
  
  $.run.kw/try

  [env ^AVector tuple]

  (let [trx-catch+ (.get tuple
                         3)
        hook-error (env :convex.run.hook/error)]
    (-> env
        (assoc :convex.run.hook/error
               (fn [env-2]
                 (-> env-2
                     (dissoc :convex.run/error)
                     (cond->
                       trx-catch+
                       (-> (assoc :convex.run.hook/error
                                  hook-error)
                           ($.run.exec/trx+ trx-catch+)))
                     (update :convex.run/error
                             #(or %
                                  ::try)))))
        ($.run.exec/trx+ (.get tuple
                               2))
        (assoc :convex.run.hook/error
               hook-error)
        (as->
          env-2
          (if (identical? (env-2 :convex.run/error)
                          ::try)
            (-> env-2
                (dissoc :convex.run/error)
                ($.run.ctx/error nil))
            env-2)))))
