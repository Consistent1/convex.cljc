(ns convex.aws.loadnet.peer

  (:require [babashka.fs            :as bb.fs]
            [convex.aws.loadnet.rpc :as $.aws.loadnet.rpc]
            [convex.cell            :as $.cell]
            [taoensso.timbre        :as log]))


;;;;;;;;;;


(defn- -await-ready

  [env i-peer]

  (when-not (= ($.cell/* :ready)
               @($.aws.loadnet.rpc/worker env
                                          :convex.aws.ip/peer+
                                          i-peer
                                          ($.cell/* :ready)))
    (throw (Exception. (format "Problem while testing if peer %d was ready"
                               i-peer))))
  (log/info (format "Peer process %d ready"
                    i-peer)))



(defn- -cvx

  [env i-peer cell]

  (let [*stopped? (env :convex.aws.loadnet/*stopped?)]
    ((if (env :convex.aws.loadnet.peer/native?)
       $.aws.loadnet.rpc/cvx
       $.aws.loadnet.rpc/jcvx)
     env
     :convex.aws.ip/peer+
     i-peer
     cell
     {:exit-fn (fn [process]
                 (let [exit (:exit process)]
                   (if (zero? exit)
                     (log/info (format "SSH process of Peer %d stopped normally"
                                       i-peer))
                     (if (and @*stopped?
                              (= exit
                                 255))
                       (log/info (format "SSH process of Peer %d terminated"
                                         i-peer))
                       (log/warn (format "SSH process of Peer %d stopped with status %d, STDERR = %s"
                                         i-peer
                                         exit
                                         (slurp (:err process))))))))})))


;;;;;;;;;;


(defn start-genesis

  [env]

  (log/info "Starting peer 0 (genesis)")
  (let [env-2
        (assoc env
               :convex.aws.loadnet.cvx/peer+
               [(-cvx env
                      0
                      ($.cell/*
                        (do
                          (.project.dir.set "/home/ubuntu/repo/lab.cvx")
                          (let [dep+ (.dep.deploy '[$.net.test  (lib net test)
                                                    scenario    ~(env :convex.aws.loadnet.scenario/path)])]
                            (def $.net.test
                                 (get dep+
                                      '$.net.test))
                            (def scenario
                                 (get dep+
                                      'scenario))
                            ($.net.test/start.genesis {:dir   "/tmp/peer"
                                                       :state (:state (scenario/state
                                                                        ($.net.test/state.genesis
                                                                          {:peer+
                                                                           ~($.cell/vector (map (fn [ip]
                                                                                                  ($.cell/* {:host ~($.cell/string ip)}))
                                                                                                (env :convex.aws.ip/peer+)))})
                                                                        ~(env :convex.aws.loadnet.scenario/param+)))})
                            (loop []
                              (.worker.start {:pipe "peer"})
                              (recur))))))])]
    (-await-ready env-2
                  0)
    env-2))



(defn start-syncer+

  [env]

  (log/info "Starting syncers")
  (let [ip+ (env :convex.aws.ip/peer+)]
    (loop [process+ []
           ready+   [(first ip+)]
           todo+    (rest ip+)]
      (if (seq todo+)
        (let [n-ready (count ready+)]
          (recur (into process+
                       (map (fn [[i-peer process]]
                              (-await-ready env
                                            i-peer)
                              process))
                       (mapv (fn [i-batch ip-ready _ip-todo]
                               (let [i-peer (+ n-ready
                                               i-batch)]
                                 (log/info (format "Starting peer process %d"
                                                   i-peer))
                                 [i-peer
                                  (-cvx env
                                        i-peer
                                        ($.cell/*
                                          (do
                                            (.project.dir.set "/home/ubuntu/repo/lab.cvx")
                                            (let [dep+ (.dep.deploy '[$.net.test (lib net test)])]
                                              (def $.net.test
                                                   (get dep+
                                                        '$.net.test))
                                              ($.net.test/start.sync ~($.cell/long i-peer)
                                                                     {:dir         "/tmp/peer"
                                                                      :remote.host ~($.cell/string ip-ready)}))
                                            (loop []
                                              (.worker.start {:pipe "peer"})
                                              (recur)))))]))
                             (range)
                             ready+
                             todo+))
                 (into ready+
                       (take n-ready
                             todo+))
                 (drop n-ready
                       todo+)))
        (update env
                :convex.aws.loadnet.cvx/peer+
                into
                process+)))))


;;;


(defn start

  [env]

  (let [env-2 (-> env
                  (start-genesis)
                  (start-syncer+))]
    (log/info "All peer processes ready to receive transactions")
    env-2))



(defn stop

  [env]

  (doseq [[i-peer
           d*result] (mapv (fn [i-peer]
                             (log/info (format "Stopping peer server %d"
                                               i-peer))
                             [i-peer
                              ($.aws.loadnet.rpc/worker env
                                                        :convex.aws.ip/peer+
                                                        i-peer
                                                        ($.cell/*
                                                          (do
                                                            (.peer.stop peer)
                                                            (.db.flush)
                                                            :ok)))])
                           (range (count (env :convex.aws.ip/peer+))))]
    (when-not (= @d*result
                 ($.cell/* :ok))
      (log/error (format "Peer server %d might not have been stopped properly"
                         i-peer))))
  (log/info "All peer servers have been stopped")
  env)


;;;;;;;;;;


(defn log+

  [env]

  (let [dir (format "%s/log/peer"
                    (env :convex.aws.loadnet/dir))]
    (log/info (format "Collecting peer logs to '%s'"
                      dir))
    (bb.fs/create-dirs dir)
    (run! deref
          (mapv (fn [i-peer]
                  (future
                    ($.aws.loadnet.rpc/rsync env
                                             :convex.aws.ip/peer+
                                             i-peer
                                             "/tmp/peer/log.cvx"
                                             (format "%s/%d.cvx"
                                                     dir
                                                     i-peer))))
                (range (count (env :convex.aws.ip/peer+))))))
  (log/info "Done collecting peer logs")
  env)
