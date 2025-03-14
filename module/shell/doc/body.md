One-size fits all tool for sophisticated development, testing, and analysis of
Convex Lisp in a fun and highly productive environment. Meant users with notions
about [Convex Lisp and the Convex Virtual Machine](https://convex.world/cvm),
wanting to prototype and build actual smart contracts.

Convex Shell extends the original Convex Virtual Machine with many features,
such as:

- File IO
- Unit testing
- System utils
- Time travel, various manipulations on the Global State
- Using Etch, the fast database for Convex data 
- Dependency management for Convex Lisp projects
- Miscellaneous helpers, such as switching accounts
- And much more

Executes Convex Lisp code provided as command-line arguments. If none is given,
starts the Convex Lisp REPL where users can work interactively.

In the future, Convex Shell will also integrate client capabilities,
allowing for scripted or dynamic interactions with networks of peers.


---


## Usage

Install the [latest
release](https://github.com/Convex-Dev/convex.cljc/releases/tag/stable%2F2023-01-18)
on your system.

The native version is highly recommended, but an uberjar file is provided in case
your operating system is not supported.

Ensure the binary is executable on your system. Let us suppose it is aliased in
your terminal as `cvx`.

Either provide Convex Lisp code as an argument:

    cvx '(def x 42)  (inc x)'

Or do not provide any argument, which will start a REPL where you can type code
interactively:

    cvx

If your environment does not support ANSI colors, they can be disabled as such:

    cvx '(.term.style.enable? false)  (.repl)'

In the grand tradition of Lisp languages, Convex Shell is self-documented.
Whenever in doubt, call the `?` function which should guide you and help you:

    cvx '(?)  (.repl)'


## Improved REPL experience

It is highly advised using [rlwrap](https://github.com/hanslub42/rlwrap) when
working at the REPL since it provides command history and navigation using arrow
keys, among other features. Linux package managers typically host this common
program.

For instance, on Ubuntu:

    sudo apt install rlwrap

Or MacOs:

    brew install rlwrap

Start a comfortable REPL:

    rlwrap -c cvx

Note: `-c` argument provides filename completion on tab.


---


## Build

For building Convex Shell locally, commands must be issued from the root of
this repository and it is assumed tools mentioned in the [general
README](../../README.md) are available. 

First, an uberjar with direct-linking must be created:

    bb build :module/shell

Uberjar should now be located under `./private/target/shell.uber.jar`. It is
usable with any JVM:

    java -jar ./private/target/shell.uber.jar

For native compilation, [GraalVM](https://www.graalvm.org/docs/getting-started/)
must be installed as well as its companion tool [Native
Image](https://www.graalvm.org/reference-manual/native-image/#install-native-image).

We recommend [SDKMan](https://sdkman.io) for easy installation of GraalVM tools.

Assuming everything is ready and the uberjar has been built:

    bb native:image :module/shell

After a few minutes of work and quite a bit of memory usage, the native binary
for your system will be available under `./private/target/shell`.
