(ns convex.client

  "Interacting with a peer server via the binary protocol.

   After creating a client with [[connect]], main interactions are [[query]] and [[transact]].

   All IO functions return a future which ultimately resolves to a result received from the peer.
   Information from result can be extracted using:

   - [[result->error-code]]
   - [[result->trace]]
   - [[result->value]]
  
   Clients need an access to Etch. See `convex.db` from `:module/cvm`."

  {:author "Adam Helinski"}

  (:import (convex.api Convex)
           (convex.core Result)
           (convex.core.crypto AKeyPair)
           (convex.core.data ACell
                             AVector
                             SignedData)
           (convex.core.lang Symbols)
           (convex.core.transactions ATransaction)
           (convex.peer Server)
           (java.net InetSocketAddress)
           (java.util.concurrent CompletableFuture)
           (java.util.function Function))
  (:refer-clojure :exclude [resolve])
  (:require [convex.db       :as $.db]
            [convex.clj      :as $.clj]
            [convex.key-pair :as $.key-pair]))


(set! *warn-on-reflection*
      true)


;;;;;;;;;; Lifecycle


(defn close

  "Closes the given `client`.
  
   See [[connect]]."

  [^Convex connection]

  (.close connection)
  nil)



(defn connect

  "Opens a new client connection to a peer server using the binary protocol.

   Will use the Etch instance found with `convex.db/current`. It important keeping the client
   on a thread that has always access to the very same instance.
  
   A map of options may be provided:

   | Key                   | Value           | Default       |
   |-----------------------|-----------------|---------------|
   | `:convex.server/host` | Peer IP address | \"localhost\" |
   | `:convex.server/port` | Peer port       | 18888         |"


  (^Convex []

   (connect nil))


  (^Convex [option+]

   (Convex/connect (InetSocketAddress. (or ^String (:convex.server/host option+)
                                                   "localhost")
                                       (long (or (:convex.server/port option+)
                                                 Server/DEFAULT_PORT)))
                   nil
                   nil
                   ($.db/current))))



(defn connect-local

  "Like [[connect]] but the returned client is optimized to talk to a peer `server` running
   in the same process.

   It is important the client is always on a thread that has the same store being returned on
   `convex.db/current` (from `:module/cvm`) as the store used by the `server`.
  
   See [[convex.server]]."

  ^Convex

  [^Server server]

  (Convex/connect server
                  nil
                  nil))



(defn connected?

  "Returns true if the given `client` is still connected.
   
   Attention. Currently, does not detect severed connections (eg. server shutting down).
  
   See [[close]]."

  [^Convex client]

  (.isConnected client))


;;;;;;;;;; Networking - Performed directly by the client


(defn peer-status

  "Advanced feature. The peer status is a vector of blobs which are hashes to data about the peer.
   For instance, blob 4 is the hash of the state. That is how [[state]] works, retrieving the hash
   from the peer status and then using [[resolve]]."

  ^CompletableFuture

  [^Convex client]

  (.requestStatus client))



(defn resolve

  "Sends the given `hash` to the peer to resolve it as a cell using its Etch instance.

   See `convex.db` from `:module/cvm` for more about hashes and values in the context of Etch.
  
   Returns a future resolving to a result."


  ^CompletableFuture
  
  [^Convex client hash]

  (.acquire client
            hash))



(defn query

  "Performs a query, `cell` representing code to execute.
  
   Queries are a dry run: executed only by the peer, without consensus, and any state change is discarded.
   They do not incur fees.
  
   Returns a future resolving to a result."

  ^CompletableFuture

  [^Convex client address cell]

  (.query client
          cell
          address))



(defn state

  "Requests the currrent network state from the peer.

   Returns a future resolving to a result."

  ^CompletableFuture

  [^Convex client]

  (.acquireState client))



(defn transact

  "Performs a transaction which is one of the following (from `:module/cvm`):

   - `convex.cell/call` for an actor call
   - `convex.cell/invoke` for executing code
   - `convex.cell/transfer` for executing a transfer of Convex Coins

   Transaction must be either pre-signed beforehand or a key pair must be provided to sign it.
   See the [[convex.key-pair]] namespace to learn more about key pairs.

   It is important that transactions are created for the account matching the key pair and that the right
   sequence ID is used. See [[sequence-id]]."


  (^CompletableFuture [^Convex client ^SignedData signed-transaction]

   (.transact client
              signed-transaction))


  (^CompletableFuture [client ^AKeyPair key-pair ^ATransaction transaction]

   (transact client
             ($.key-pair/sign key-pair
                              transaction))))


;;;;;;;;;; Results


(defn result->error-code

  "Given a result dereferenced from a future, returns the error code (a cell, typically a CVX keyword).
  
   Returns nil if no error occured."

  ^ACell

  [^Result result]

  (.getErrorCode result))



(defn result->trace

  "Given a result dereferenced from a future, returns the stacktrace (a CVX vector of strings).
  
   Returns nil if no error occured."

  ^AVector

  [^Result result]

  (.getTrace result))



(defn result->value 

  "Given a result dereferenced from a future, returns its value (a cell).

   In case of error, this will be the error message (often a CVX string but can be any value)."

  ^ACell

  [^Result result]

  (.getValue result))


;;;;;;;;;; Networking - Higher-level


(defn sequence-id

  "Uses [[query]] to retrieve the next sequence ID required for a transaction.
  
   Eacht account has a sequence ID, a number being incremented on each successful transaction to prevent replay
   attacks. Providing a transaction (eg. `convex.cell/invoke` from `:module/cvm`) with a wrong sequence ID
   number will fail."

  [^Convex client address]

  (.thenApply (query client
                      address
                      Symbols/STAR_SEQUENCE)
               (reify Function

                 (apply [_this result]
                   (if-some [ec (result->error-code result)]
                     (throw (ex-info "Unable to fetch next sequence ID"
                                     {:convex.cell/address address
                                      :convex.error/code   ec
                                      :convex.error/trace  (result->trace result)}))
                     (inc ($.clj/long (result->value result))))))))
