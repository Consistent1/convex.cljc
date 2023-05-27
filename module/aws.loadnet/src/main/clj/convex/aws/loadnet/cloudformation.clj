(ns convex.aws.loadnet.cloudformation

  (:require [convex.aws :as $.aws]))


;;;;;;;;;;


(defn client


  ([env]

   (client env
           nil))


  ([env region]

   (assoc-in env
             [:convex.aws.client/cloudformation+
              region]
             ($.aws/client :cloudformation
                           (or region
                               (first (env :convex.aws/region+)))
                           env))))



(defn client+

  [env]

  (reduce client
          env
          (env :convex.aws/region+)))



(defn invoke


  ([env op request]

   (invoke env
           nil
           op
           request))


  ([env region op request]

   ($.aws/invoke (get-in env
                         [:convex.aws.client/cloudformation+
                          (or region
                              (first (env :convex.aws/region+)))])
                 op
                 request)))



(defn param+

  [env]

  (mapv (fn [[k v]]
          {:ParameterKey   (name k)
           :ParameterValue v})
        (env :convex.aws.stack/parameter+)))
