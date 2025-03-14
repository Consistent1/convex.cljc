(ns convex.aws.loadnet.rpc

  "Remote operations on EC2 instances through SSH."

  (:import (java.net InetSocketAddress
                     Socket
                     SocketTimeoutException))
  (:require [convex.cell       :as $.cell]
            [convex.read       :as $.read]
            [convex.std        :as $.std]
            [convex.write      :as $.write]
            [protosens.process :as P.process]
            [taoensso.timbre   :as log]))


;;;;;;;;;; Private


(defn- -ip

  ;; TODO. Rename to `-host`.

  [env k-ip+ i-ip]

  (format "ubuntu@%s"
          (get-in env
                  [k-ip+
                   i-ip])))



(defn- -key

  [env]

  (or (env :convex.aws.key/file)
      (throw (IllegalArgumentException. "Path to key missing"))))


;;;;;;;;;; Public


(defn await-ssh

  "Tries to await all SSH servers from load generator and peer instances.

   Returns a Boolean in `env` under `:convex.aws.loadnet/ssh-ready?` indicating success."

  [env]

  (log/info "Awaiting for all SSH servers to be ready")
  (let [env-2
        (reduce (fn [env-2 ip]
                  (loop [i 10]
                    (or (try
                          (with-open [^Socket socket (Socket.)]
                            (.connect socket
                                      (InetSocketAddress. ip
                                                          22)
                                      60000))
                          env-2
                          ;;
                          (catch SocketTimeoutException _ex
                            (log/error (format "Timeout when awaiting SSH server seems closed on %s"
                                               ip))
                            (reduced (assoc env-2
                                            :convex.aws.loadnet/ssh-ready?
                                            false)))
                          ;;
                          (catch Exception _ex
                            (if (zero? i)
                              (do
                                (log/error (format "Unable to establish SSH connection to %s"
                                                   ip))
                                (reduced (assoc env-2
                                                :convex.aws.loadnet/ssh-ready?
                                                false)))
                              (do
                                (Thread/sleep 2000)
                                nil))))
                        (recur (dec i)))))
                (assoc env
                       :convex.aws.loadnet/ssh-ready?
                       true)
                (concat (env :convex.aws.ip/load+)
                        (env :convex.aws.ip/peer+)))]
    (when (env-2 :convex.aws.loadnet/ssh-ready?)
      ;; Extra delay just in case.
      (Thread/sleep 5000))
    env-2))



(defn ssh

  "Executes a remote command through SSH given the key in `env` where IPs
   are stored (`:convex.aws.ip/load+` or `:convex.aws.ip/peer+`) and the
   index of the IP to use.

   Options are forwarded to `protosens.process/run` (used to run the local
   SSH process)."

  ([env k-ip+ i-ip command]

   (ssh env
        k-ip+
        i-ip
        command
        nil))


  ([env k-ip+ i-ip command option+]

   (P.process/run (concat ["ssh"
                           "-i" (-key env)
                           "-o" "StrictHostKeyChecking=no"
                           (-ip env
                                k-ip+
                                i-ip)]
                          command)
                  option+)))


;;;


(defn- -cvx

  ;; Used by [[cvx]] and [[jcvx]];

  [cvx-cmd env k-ip+ i-ip cell option+]

  (ssh env
       k-ip+
       i-ip
       [cvx-cmd
        (format "'%s'"
                ($.write/string Long/MAX_VALUE
                                cell))]
       option+))



(defn cvx

  "Executes CVX code (`cell`) on a remote instance using the Convex Shell (native).
  
   See [[ssh]] for arguments."


  ([env k-ip+ i-ip cell]

   (cvx env
        k-ip+
        i-ip
        cell
        nil))


  ([env k-ip+ i-ip cell option+]

   (-cvx "cvx"
         env
         k-ip+
         i-ip
         cell
         option+)))



(defn jcvx

  "Executes CVX code (`cell`) on a remote instance using the Convex Shell (JVM).
  
   See [[ssh]] for arguments."

  ([env k-ip+ i-ip cell]

   (jcvx env
         k-ip+
         i-ip
         cell
         nil))


  ([env k-ip+ i-ip cell option+]

  ;; TODO. Remove `rlwrap` from `jcvx` in AMIs.
  ;
  ; (-cvx "jcvx"
  ;       env
  ;       k-ip+
  ;       i-ip
  ;       cell))

   (ssh env
        k-ip+
        i-ip
        ["source" "$HOME/.sdkman/bin/sdkman-init.sh"
         "&&"
         "java"
         "-jar" "~/repo/convex.cljc/private/target/shell.uber.jar"
         (format "'%s'"
                 ($.write/string Long/MAX_VALUE
                                 cell))]
        option+)))



(defn kill-process

  "Kills a remote process through [[ssh]]."

  [env k-ip+ i-ip file-pid]

  (ssh env
       k-ip+
       i-ip
       ["kill"
        "-9"
        (format "`cat %s`"
                file-pid)]))



(defn rsync

  "Performs RSYNC on a remote instance.
  
   Uses [[ssh]] as a remote shell."

  ([env k-ip+ i-ip src dest]

   (rsync env
          k-ip+
          i-ip
          src
          dest
          nil))


  ([env k-ip+ i-ip src dest option+]

   (let [process (P.process/run (reduce (fn [cmd path]
                                          (conj cmd
                                                "--exclude"
                                                path))
                                        ["rsync"
                                         "-azrv"
                                         "-e"    (format "ssh -i %s -o StrictHostKeyChecking=no"
                                                         (-key env))
                                         (str (-ip env
                                                   k-ip+
                                                   i-ip)
                                              (format ":%s"
                                                      src))
                                         dest]
                                        (:exclude option+)))]
     (or (= 0
            (:exit @process))
         (do
           (log/error (format "Rsync over peer %d failed: %s"
                              [k-ip+
                               i-ip]
                              (slurp (:err process))))
           false)))))



(defn worker

  "Executes CVX code (`cell`) on a remote instance using the Convex Shell (native)
   to connect to the peer worker.
  
   See [[ssh]] for arguments."

  [env k-ip+ i-ip cell]

  (let [process (cvx env
                     k-ip+
                     i-ip
                     ($.cell/*
                       (let [[in
                              out] (.worker.pipe+ "peer")]
                         (.worker.exec {:in  (.file.stream.out in)
                                        :out (.file.stream.in out)}
                                       (quote ~cell)))))]
    (delay
      (when-not (zero? (:exit @process))
        (throw (ex-info "SSH error while executing remote CVX command"
                        {:convex.aws.loadnet/worker [k-ip+
                                                     i-ip]
                         :convex.aws.process/err    (slurp (:err process))})))
      (let [[ok?
             _
             x]  (seq (first ($.read/string (slurp (:out process)))))]
        (when-not ($.std/true? ok?)
          (throw (ex-info "CVM exception while executing remote CVX command"
                          {:convex.aws.loadnet/worker [k-ip+
                                                       i-ip]
                           :convex.cvm/exception      x})))
        x))))
