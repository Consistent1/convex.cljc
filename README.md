# convex.lisp.cljc

[![Clojars](https://img.shields.io/clojars/v/helins/convex.lisp.cljc.svg)](https://clojars.org/helins/convex.lisp.cljc)

[![Cljdoc](https://cljdoc.org/badge/helins/convex.lisp.cljc)](https://cljdoc.org/d/helins/convex.lisp.cljc)

Working with Convex Lisp and testing the CVM (Convex Virtual Machine).

Toolset consist of:

- Reading, expanding, compiling, and executing Convex Lisp code
- Translating between Convex object and Clojure data structures
- Describing Convex Lisp code as Clojure forms
- Specyfing Convex Lisp using Malli, providing:
    - Some basic level of validation
    - Generation of random forms
- Writing generative tests against the CVM


## Usage

The toolset already provides quite a lot of useful features for interacting with Convex Lisp. Currently, the API is unstable and unannounced breaking changes are to be expected.

More examples will follow as the API stabilizes.

Current examples are located in the [example directory](../main/src/example/convex/lisp/example).

Requiring namespaces for current examples:

```clojure
(require 'convex.lisp           ;; Reading source code and translate CVM objects to Clojure data
         'convex.lisp.ctx       ;; Expanding, compiling, and executing code
         'convex.lisp.eval      ;; Helpers for quickly evaluating forms (dev + tests)
         'convex.lisp.eval.src  ;; Ditto, but when working with source strings
         'convex.lisp.form      ;; Writing Convex Lisp as Clojure forms
         'convex.lisp.schema)   ;; Malli schemas for Convex Lisp
```

### Handling Convex Lisp code

Convex Lisp source code goes through 4 steps: reading, expanding, compiling, and executing. A context is needed for handling such operations. The result of an operation is either a valid result or a handled error. There should never be an unhandled exception coming from the CVM.

Going through the whole cycle (context could be reused for further execution):

```clojure
(let [;; It is convenient writing Convex Lisp as Clojure data
      form   '(+ 2 2)
      
      ;; Converting Clojure data to source code (a string)
      source (convex.lisp.form/src form)
      
      ;; Reading source code as Convex object
      code   (convex.lisp/read source)
      
      ;; Creating a test context
      ctx    (convex.lisp.ctx/create-fake)
      
      ;; Using context for expanding, compiling, and running code
      ctx-2  (-> (convex.lisp.ctx/expand ctx
                                         code)
                 convex.lisp.ctx/compile
                 convex.lisp.ctx/run)]
                 
  ;; Getting result and converting to Clojure data
  (-> ctx-2
      convex.lisp.ctx/result
      convex.lisp/datafy))
```

There are shortcuts and it is easy to write a helper function as needed. For instance, leveraging other utilities:

```clojure
(->> '(+ 2 2)
     convex.lisp/read-form
     (convex.lisp.ctx/eval (convex.lisp.ctx/create-fake))
     convex.lisp.ctx/result
     convex.lisp/datafy)
```

For development and testing, the [convex.lisp.eval](../main/src/main/convex/lisp/eval.clj) namespace as well as [convex.lisp.eval.src](../main/src/main/convex/lisp/eval/src.clj) have convenient functions which remove that kind of boilerplate:

```clojure
(= 4
   (convex.lisp.eval/result (convex.lisp.ctx/create-fake)
                            '(+ 2 2))
   (convex.lisp.eval.src/result (convex.lisp.ctx/create-fake)
                                "(+ 2 2)"))
```


### Templating Convex Lisp code

It is particularly convenient writing Convex Lisp code as Clojure data since Clojure data is so easy handle.

The following macro provides a templating experience close to Clojure's syntax quote by leveraging `~` and `~@`, unquoting and unquote-splicing.

Convex Lisp also uses those symbols. For avoiding confusing, symbols are used to inject values from Clojure whereas the form notation (`(unquote x)` and `(unquote-splicing x)`) is left as such for Convex Lisp. Unlike the syntax quote, symbols are not qualified automatically since this is about templating Convex Lisp code.

For example:

```clojure
(let [kw :foo
      xs [2 3]
      y  42]
  (convex.lisp.form/templ* [~kw 1 ~@xs 4 ~y y (unquote y) (unquote-splicing xs)]))
```

Produces the following vector:

```clojure
[:foo 1 2 3 4 42 y (unquote y) (unquote-splicing xs)]
```


### Validating and generating Convex Lisp

The fact that Convex Lisp can be written as Clojure data means we can leverage the [Malli](https://github.com/metosin/malli) library for describing the language:

```clojure
(require '[malli.core      :as malli]
         '[malli.generator :as malli.gen])


(def registry

  "Malli registry containing everything that is needed."

  (convex.lisp.schema/registry (malli/default-schemas)))     


(malli.gen/generate :convex/vector
                    {:registry registry
                     :size     5})
```

Generative tests targeting the CVM extensively relies on such a registry.

## Testing the CVM

Current strategy consists of ensuring first that the very basic building blocks
of transactions and smart contracts work perfectly. It entails using generative
testing for proving that the core namespace and core language utilities work as
intended and fail as intended.

Second step will be about reusing what has been previously built for generating
common types of multi-form transactions. The purpose is to prove that the core
utilities interact with each other as intended. A simple example would be a
transaction where an account is created, some random amount of coin is sent to
that account, and balance as well as *balance* are used to reflected that all
involved accounts were indeed balanced out. Another simple example would be the
fact that get-holding should reflect set-holding. Once again, proving graceful
failure is just as important.

Third step will happen when the CVM has been proven resilient and robust. Only
then network tests can be envisioned. Loosely, the purpose will be to setup a
test network of peers and some number of automated client generating random
transactions. Never should peers diverge. If they do, it means that something
is wrong at the consensus/protocol level probably since the execution itself
has been proven flawless. Generating those transaction will leverage previous
work while the network part could possibly leverage [Jepsen](https://github.com/jepsen-io/jepsen),
a notorious Clojure tool used for analyzing and breaking distributed systems.


## Running tests

Depending on hardware, tests usually takes a few minutes to run.

On the JVM, using [Kaocha](https://github.com/lambdaisland/kaocha):

```bash
$ ./bin/test/jvm/run
```
On NodeJS, using [Shadow-CLJS](https://github.com/thheller/shadow-cljs):

```bash
$ ./bin/test/node/run

# Or testing an advanced build:
$ ./bin/test/node/advanced
```


## Development

Starting in Clojure JVM mode with NREPL on port `14563`:

```bash
$ ./bin/dev/clojure :nrepl
```

Starting in CLJS mode using [Shadow-CLJS](https://github.com/thheller/shadow-cljs):
```bash
$ ./bin/dev/cljs
```


## License

Currently unlicensed.

Copyright © 2021 Adam Helinski
