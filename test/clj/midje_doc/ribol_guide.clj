(ns midje-doc.ribol-guide
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Overview"}]]
"
This is quite a comprehensive guide to `ribol` and how conditional restart libraries may be used. There are currently three other conditional restart libraries for clojure:

  - [errorkit](https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/error_kit.clj) was the first and provided the guiding architecture for this library.


  - [swell](https://github.com/hugoduncan/swell) and [conditions](https://github.com/bwo/conditions) have been written to work with [slingshot](https://github.com/scgilardi/slingshot).

A simple use case looking at the advantage in using restarts over exceptions can be seen in [Unlucky Numbers](#unlucky-numbers). There is also a [Core library API](#api-reference) with examples.

For those that wish to know more about conditional restarts, a comparison of different strategies that can be implemented is done in [Control Strategies](#control-strategies). While for those curious about how this jumping around has been achieved, look at [Implementation](#implementation).
"

[[:section {:title "Installation"}]]

"Add to `project.clj` dependencies (use double quotes): 

    [im.chit/ribol '{{PROJECT.version}}']"

"All functionality is found contained in `ribol.core`"

[[{:numbered false}]]
(comment  (use 'ribol.core))

[[:file {:src "test/clj/midje_doc/ribol_unlucky_numbers.clj"}]]

[[:chapter {:title "Conditional Restarts"}]]

"
`ribol` provides a conditional restart system. It can be thought of as an issue resolution system or `try++/catch++`. We use the term `issues` to differentiate them from `exceptions`. The difference is purely semantic: `issues` are `managed` whilst `exceptions` are `caught`. They all refer to abnormal program flow.

*Restart systems* are somewhat analogous to a *management structure*. A low level function will do work until it encounter an abnormal situation. An `issue` is `raised` up the chain to higher-level functions. These can `manage` the situation depending upon the type of `issue` raised.
"

"In the author's experience, there are two forms of `exceptions` that a programmer will encounter:

 1. **Programming Mistakes** - These are a result of logic and reasoning errors, should not occur in normal operation and should be eliminated as soon as possible. The best strategy for dealing with this is to write unit tests and have functions fail early with a clear message to the programmer what the error is.
  - Null pointers
  - Wrong inputs to functions

 2. **Exceptions due to Circumstances** - These are circumstancial and should be considered part of the normal operation of the program.
  - A database connection going down
  - A file not found
  - User input not valid

The common method of `try` and `catch` is not really needed when dealing with the *Type 1* exceptions and a little too weak when dealing with those of *Type 2*.

The net effect of using only the `try/catch` paradigm in application code is that in order to mitigate these *Type 2* exceptions, there requires a lot of defensive programming. This turns the middle level of the application into spagetti code with program control flow (`try/catch`) mixed in with program logic.

Conditional restarts provide a way for the top-level application to more cleanly deal with *Type 2* exceptions.
"


[[:section {:title "Raising issues"}]]

"
`ribol` provide richer semantics for resolution of *Type 2* exceptions. Instead of `throw`, a new form `raise` is introduced ([e.{{raise-syntax}}](#raise-syntax)).
"

[[{:title "raise syntax"}]]
(comment
  (raise {:input-not-string true :input-data 3}     ;; issue payload
         (option :use-na [] "NA")                   ;; option 1
         (option :use-custom [n] n)                 ;; option 2
         (default :use-custom "nil"))               ;; default choice
)

"
`raise` differs to `throw` in a few ways:
  - issues are of type `clojure.lang.ExceptionInfo`.
  - the payload is a `hash-map`.
  - **optional**: multiple `option` handlers can be specified.
  - **optional**: a `default` choice can be specified.
"

[[:section {:title "Managing issues"}]]

"Instead of the `try/catch` combination, `manage/on` is used ([e.{{manage-syntax}}](#manage-syntax))."

[[{:title "manage/on syntax" :tag "manage-syntax"}]]
(comment
  (manage (complex-operation)
          (on :node-working [node-name]
              (choose :wait-for-node))
          (on :node-failed [node-name]
              (choose :reinit-node))
          (on :database-down []
              (choose :use-database backup-database))
          (on :core-failed []
              (terminate-everything)))
)


"Issues are managed through `on` handlers within a `manage` block. If any `issue` is raised with the manage block, it is passed to each handler. There are six ways that a handler can deal with a raised issue:
  - directly (same as `try/catch`)
  - using `continue` to keep going with a given value
  - using `choose` to specify an option
  - using `escalate` to notify higher level managers
  - using `default` to allow the issue to resolve itself
  - using `fail` to throw an exception

Using these six different different issue resolution directives, the programmer has the richness of language to craft complex process control flow strategies without mixing logic handling code in the middle tier. Restarts can also create new ways of thinking about the problem beyond the standard `throw/catch` mechanism and offer more elegant ways to build programs and workflows.
"

[[:file {:src "test/clj/midje_doc/ribol_api.clj"}]]

[[:file {:src "test/clj/midje_doc/ribol_strategies.clj"}]]

[[:file {:src "test/clj/midje_doc/ribol_implementation.clj"}]]

[[:chapter {:title "End Notes"}]]

"For any feedback, requests and comments, please feel free to lodge an issue on github or contact me directly.

Chris.
"
