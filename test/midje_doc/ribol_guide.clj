(ns midje-doc.ribol-guide
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Overview"}]]
"
This is quite a comprehensive guide to `ribol` and how conditional restart libraries may be used. There are currently three other conditional restart libraries for clojure:

  - [errorkit](https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/error_kit.clj) was the first and provided the guiding architecture for this library.


  - [swell](https://github.com/hugoduncan/swell) and [conditions](https://github.com/bwo/conditions) have been written to work with [slingshot](https://github.com/scgilardi/slingshot).

A simple use case looking at the advantage in using restarts over exceptions can be seen in [Unlucky Numbers](#unlucky-numbers). There is also a [Core library API](#api-reference) with examples.

For those that wish to know more about conditional restarts, a comparison of different strategies that can be implemented is done in [Control Strategies](#control-strategies).
"

[[:section {:title "Installation"}]]

"In `project.clj`, add to dependencies:"

[[{:numbered false}]]
(comment  [im.chit/ribol "0.3.1"])

"All functionality is found contained in `ribol.core`"

[[{:numbered false}]]
(comment  (use 'ribol.core))


[[:chapter {:title "Unlucky Numbers"}]]

"
In this demonstration, we look at how code bloat problems using `throw/try/catch` could be reduced using `raise/manage/on`. Two functions are defined:
-  `check-unlucky` which takes a number as input, throwing a `RuntimeException` when it sees an unlucky number.
- `int-to-str` which calls `check-unlucky`, pretends to do work and outputs a string represention of the number."

[[{:numbered false}]]

(defn check-unlucky [n]
  (if (#{4 13 14 24 666} n)
    (throw (RuntimeException. "Unlucky Number"))
    n))

(defn int-to-str [n]
  (do (Thread/sleep 10)  ;; Work out something
      (str (check-unlucky n))))

[[{:numbered false}]]
(fact
  (int-to-str 1) => "1"

  (int-to-str 666) => (throws RuntimeException "Unlucky Number"))

"We can then use `int-to-str` to run across multiple numbers:"

[[{:numbered false}]]
(fact
  (mapv int-to-str (range 4))
  => ["0" "1" "2" "3"])


"
#### Exceptions mess up the middle

Except when we try to use it in with a sequence containing an unlucky number"

(facts
  [[{:numbered false}]]
  (mapv int-to-str (range 20))
  => (throws RuntimeException "Unlucky Number")

  "We can try and recover using `try/catch`"

  [[{:numbered false}]]
  (try
    (mapv int-to-str (range 20))
    (catch RuntimeException e
      "Unlucky number in the sequence"))
  => "Unlucky number in the sequence")

"
But we can never get the previous sequence back again because we have blown the stack. The only way to 'fix' this problem is to change `int-to-str` so that it catches the exception:"

[[{:numbered false}]]
(fact
  (defn int-to-str-fix [n]
    (try
      (if (check-unlucky n)    ;;
        (do (Thread/sleep 10)  ;; Work out something
            (str n)))
      (catch RuntimeException e
        "-")))

  (mapv int-to-str-fix (range 20))
  => ["0" "1" "2" "3" "-" "5" "6" "7" "8" "9" "10"
      "11" "12" "-" "-" "15" "16" "17" "18" "19"])

"This is seriously unattractive code. We have doubled our line-count to `int-to-str` without adding too much functionality.

For real world scenarios like batch processing a bunch of files, there are more ways that the program can go wrong. The middle code becomes messy very quickly."

"
#### Raising issues, not throwing exceptions

This problem actually has a very elegant solution if we use `ribol`. Instead of throwing an exception, we can `raise` an issue in `check-unlucky`:
"
[[{:numbered false}]]
(defn check-unlucky [n]
  (if (#{4 13 14 24 666} n)
    (raise [:unlucky-number {:value n}])
    n))

"`int-to-str` does not have to change"

[[{:numbered false}]]
(defn int-to-str [n]
  (do (Thread/sleep 10)  ;; Work out something
      (str (check-unlucky n))))

"We still get the same functionality:"

[[{:numbered false}]]
(fact
  (int-to-str 1) => "1"

  (mapv int-to-str (range 4))
  => ["0" "1" "2" "3"])

"
#### Handling raised issues

What happens when we use this with unlucky numbers? Its almost the same... except that instead of raising a `RuntimeException`, we get a `clojure.lang.ExceptionInfo` object:"

[[{:numbered false}]]
(fact
  (mapv int-to-str (range 20))
  => (throws clojure.lang.ExceptionInfo))

"We can still use `try/catch` to recover from the error"

[[{:numbered false}]]
(fact
  (try
    (mapv int-to-str (range 20))
    (catch clojure.lang.ExceptionInfo e
      "Unlucky number in the sequence"))
  => "Unlucky number in the sequence")

"We set up the `ribol` handlers by replacing `try` with `manage` and `catch` with `on`. This gives the exact same result as before."

[[{:numbered false}]]
(fact
  (manage
    (mapv int-to-str (range 20))
    (on :unlucky-number []
      "Unlucky number in the sequence"))
  => "Unlucky number in the sequence")

"
#### A sleight of code

However, the whole point of this example is that we wish to keep the previous results without ever changing `int-to-str`. We will do this with `continue`:
"

[[{:numbered false}]]
(fact
  (manage
    (mapv int-to-str (range 20))
    (on :unlucky-number []
        (continue "-")))
  => ["0" "1" "2" "3" "-" "5" "6" "7" "8" "9" "10"
      "11" "12" "-" "-" "15" "16" "17" "18" "19"])

"
#### What just happened?

`continue` is a special form that allows higher level functions to jump back into the place where the exception was called. So once the manage block was notified of the issue raised in `check-unlucky`, it did not blow away the stack but jumped back to the back at which the issue was raised and continued on with `-` instead. In this way, the exception handling code instead of being written in `int-to-str`, can now be written at the level that it is required.
"

"
#### Unlucky for whom?

The chinese don't like the number 4 and any number with the number 4, but they don't mind 13 and 666. We can write use the `on` handler to define cases to process:
"

[[{:numbered false}]]
(fact
  (defn ints-to-strs-chinese [arr]
    (manage
     (mapv int-to-str arr)
     (on {:unlucky-number true
          :value #(or (= % 666)
                      (= % 13))}
         [value]
         (continue value))

     (on :unlucky-number []
         (continue "-"))))

  (ints-to-strs-chinese [11 12 13 14])
  => ["11" "12" "13" "-"]

  (ints-to-strs-chinese [1 2 666])
  => ["1" "2" "666"])

"The christians don't mind the numbers with 4, don't like 13 and really don't like 666. In this example, it can be seen that if 666 is seen, it will jump out and return straight away, but will continue on processing with other numbers."

[[{:numbered false}]]
(fact
  (defn ints-to-strs-christian [arr]
    (manage
     (mapv int-to-str arr)
     (on [:unlucky-number] [value]
         (condp = value
           13 (continue "-")
           666 "ARRRGHHH!"
           (continue value)))))

  (ints-to-strs-christian [11 12 13 14])
  => ["11" "12" "-" "14"]

  (ints-to-strs-christian [1 2 666])
  => "ARRRGHHH!")

"It can be seen from this example that the `int-to-str` function can be reused without any changes. this would be extremely difficult to do with just `try/catch`.

For the still sceptical, I'm proposing a **challenge**: to reimplement `ints-to-strs-christian` without changing `unlucky-numbers` or `int-to-str` using only `try` and `catch`."


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


[[:chapter {:title "API Reference"}]]

[[:section {:title "raise" :tag "api-raise"}]]

"The keyword `raise` is used to raise an 'issue'. At the simplest, when there is no `manage` blocks, `raise` just throws a `clojure.lang.ExceptionInfo` object"

[[{:numbered false}]]
(fact
  [[{:title "raise is of type clojure.lang.ExceptionInfo" :tag "raise-type"}]]
  (raise {:error true})
  => (throws clojure.lang.ExceptionInfo))

"The payload of the issue can be extracted using `ex-data`"

[[{:numbered false}]]
(fact
  (try
    (raise {:error true})
    (catch clojure.lang.ExceptionInfo e
      (ex-data e)))
  => {:error true})


"The payload can be expressed as a `hash-map`, a `keyword` or a `vector`. We define the `raises-issue` macro to help explore this a little further:"

[[{:numbered false}]]
(defmacro raises-issue [payload]
  `(throws (fn [e#] (= (ex-data e#) ~payload))))

"Please note that the `raises-issue` macro is only working with `midje`. In order to work outside of midje, we need to define the `payload` macro:"

[[{:numbered false}]]
(defmacro payload [& body]
    `(try ~@body
          (throw (Throwable.))
          (catch clojure.lang.ExceptionInfo e#
            (ex-data e#))
          (catch Throwable t#
            (throw (Exception. "No Issue raised")))))

"Its can be used to detect what type of issue has been raised:"

[[{:numbered false}]]
(fact
  (payload (raise :error))
  => {:error true})


[[:subsection {:title "hash-map"}]]

"Because the issue can be expressed as a hash-map, it is more general than using a class to represent exceptions."
[[{:numbered false}]]
(fact
  (raise {:error true :data "data"})
  => (raises-issue {:error true :data "data"}))

[[:subsection {:title "keyword"}]]

"When a `keyword` is used, it is shorthand for a map with having the specified keyword with value `true`."
[[{:numbered false}]]
(fact
  (raise :error)
  => (raises-issue {:error true}))

[[:subsection {:title "vector"}]]
"Vectors can contain only keywords or both maps and keywords. They are there mainly for syntacic sugar"

[[{:numbered false}]]
(fact

   (raise [:lvl-1 :lvl-2 :lvl-3])
  => (raises-issue {:lvl-1 true :lvl-2 true :lvl-3 true})


  (raise [:lvl-1 {:lvl-2 true :data "data"}])
   => (raises-issue {:lvl-1 true :lvl-2 true :data "data"}))

[[:subsection {:title "option/default"}]]

"Strategies for an unmanaged issue can be specified within the raise form:
- [e.{{option-one}}](#option-one) specifies two options and the specifies the default option as `:use-nil`.
- [e.{{option-two}}](#option-two) sets the default as `:use-custom` with an argument of `10`.
- [e.{{option-none}}](#option-none) shows that if there is no default selection, then an exception will be thrown as per previously seen:"

(facts
  [[{:title "default :use-nil" :tag "option-one"}]]
  (raise :error
         (option :use-nil [] nil)
         (option :use-custom [n] n)
         (default :use-nil))
  => nil

  [[{:title "default :use-custom" :tag "option-two"}]]
  (raise :error
         (option :use-nil [] nil)
         (option :use-custom [n] n)
         (default :use-custom 10))
  => 10

  [[{:title "no default" :tag "option-none"}]]
  (raise :error
         (option :use-nil [] nil)
         (option :use-custom [n] n))
  => (raises-issue {:error true}))


[[:section {:title "manage/on"}]]

"Raised issues can be resolved through use of `manage` blocks set up. The blocks set up execution scope, providing handlers and options to redirect program flow. A manage block looks like this:"

[[{:numbered false}]]
(comment
  (manage

   ... code that may raise issue ...

   (on <chk> <bindings>
       ... handler body ...)

   (option <label> <bindings>
       ... option body ...)

   (finally                   ;; only one
       ... finally body ...))
  )

"We define `half-int` and its usage:"

[[{:numbered false}]]
(defn half-int [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}])))

[[{:numbered false}]]
(fact
  (half-int 2)
  => 1

  (half-int 3)
  => (raises-issue {:odd-number true :value 3}))

[[:subsection {:title "checkers"}]]

"Within the `manage` form, issue handlers are specified with `on`. The form requires a check. "

[[{:numbered false}]]
(fact
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number []
       "odd-number-exception"))
  => "odd-number-exception")

"The checker can be a map with the value"

[[{:numbered false}]]
(fact
  (manage
   (mapv half-int [1 2 3 4])
   (on {:odd-number true} []
       "odd-number-exception"))
  => "odd-number-exception")

"Or it can be a map with a checking function:"

[[{:numbered false}]]
(fact
  (manage
   (mapv half-int [1 2 3 4])
   (on {:odd-number true?} []
       "odd-number-exception"))
  => "odd-number-exception")

[[:subsection {:title "bindings"}]]

"Bindings within the `on` handler allow values in the issue payload to be accessed:"

[[{:numbered false}]]
(fact
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number [odd-number value]
       (str "odd-number: " odd-number ", value: " value)))
  => "odd-number: true, value: 1")

[[:subsection {:title "finally"}]]

"The special form `finally` is supported in the `manage` blocks, just as in `try` blocks so that resources can be cleaned up."

[[{:numbered false}]]
(fact
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number []
       "odd-number-exception")
   (finally
     (println "Hello")))
  => "odd-number-exception" ;; Also prints "Hello"
  )

[[:section {:title "special forms"}]]

"There are five special forms that can be used within the `on` handler:
- continue
- fail
- choose
- default
- escalate"

[[:subsection {:title "continue" :tag "api-continue"}]]

 "The `continue` special form is used to continue the operation from the point that the `issue` was raised ([e.{{continue-using-nan}}](#continue-using-nan)). It must be pointed out that this is impossible to do using the `try/catch` paradigm because the all the information from the stack will be lost.

  The `on` handler can take keys of the `payload` of the raised `issue` as parameters. In [e.{{continue-using-str}}](#continue-using-str), a vector containing strings of the odd numbers are formed. Whereas in [e.{{continue-using-fractions}}](#continue-using-fractions), the on handler puts in fractions instead."

(facts
  [[{:title "continue using nan"}]]
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number []
       (continue :nan)))
  => [:nan 1 :nan 2]

  [[{:title "continue using str"}]]
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number [value]
       (continue (str value))))
  => ["1" 1 "3" 2]

  [[{:title "continue using fractions"}]]
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number [value]
       (continue (/ value 2))))
  => [1/2 1 3/2 2]
  )


[[:subsection {:title "fail" :tag "api-fail"}]]

"The `fail` special form will forcibly cause an exception to be thrown. It is used when there is no need to advise managers of situation. More data can be added to the failure ([e.{{fail-example}}](#fail-example))."

(facts
  [[{:title "failure"}]]
  (manage
    (mapv half-int [1 2 3 4])
    (on :odd-number []
      (fail [:unhandled :error])))
  => (raises-issue {:value 1 :odd-number true :unhandled true :error true})
  )

[[:subsection {:title "choose" :tag "api-choose"}]]

"The `choose` special form is used to jump to a `option`. A new function `half-int-b` ([e.{{half-int-b-definition}}](#half-int-b-definition)) is defined giving options to jump to within the `raise` form."
[[{:numbered false}]]
(defn half-int-b [n]
    (if (= 0 (mod n 2))
      (quot n 2)
      (raise [:odd-number {:value n}]
             (option :use-nil [] nil)
             (option :use-nan [] :nan)
             (option :use-custom [n] n))))

"Its usage can be seen in [e.{{choose-ex-1}}](#choose-ex-1) where different paths can be chosen depending upon `:value`. An option can also be specified in the manage block ([e.{{choose-ex-2}}](#choose-ex-2)). Options can also be overridden when specified in higher manage blocks ([e.{{choose-ex-3}}](#choose-ex-3)). "


(facts
  [[{:title "choosing different paths based on value" :tag "choose-ex-1"}]]
  (manage
   (mapv half-int-b [1 2 3 4])
   (on {:value 1} []
       (choose :use-nil))
   (on {:value 3} [value]
       (choose :use-custom (/ value 2))))
  => [nil 1 3/2 2]


  [[{:title "choosing option within manage form" :tag "choose-ex-2"}]]
  (manage
   (mapv half-int-b [1 2 3 4])
   (on :odd-number []
       (choose :use-empty))
   (option :use-empty [] []))
  => []

  [[{:title "overwriting :use-nil within manage form" :tag "choose-ex-3"}]]
  (manage
   (mapv half-int-b [1 2 3 4])
   (on :odd-number []
       (choose :use-nil))
   (option :use-nil [] nil))
  => nil)


[[:subsection {:title "default" :tag "api-default"}]]


" The `default` special short-circuits the raise process and skips managers further up to use an issue's default option. A function is defined and is usage is shown how the `default` form behaves. "

[[{:numbered false}]]
(fact
  (defn half-int-c [n]
    (if (= 0 (mod n 2))
      (quot n 2)
      (raise [:odd-number {:value n}]
             (option :use-nil [] nil)
             (option :use-custom [n] n)
             (default :use-custom :odd))))

  (manage
   (mapv half-int-c [1 2 3 4])
   (on :odd-number [value] (default)))
  => [:odd 1 :odd 2])


"The `default` form can even refer to an option that has to be implemented higher up in scope. An additional function is defined: "

[[{:numbered false}]]
(defn half-int-d [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
           (default :use-empty))))


"The usage for `half-int-d` can be seen in ([e.{{d-alone}}](#d-alone) and [e.{{d-higher}}](#d-higher)) to show these particular cases."

(facts
  [[{:title "half-int-d alone" :tag "d-alone"}]]
  (half-int-d 3)
  => (throws java.lang.Exception "RAISE_CHOOSE: the label :use-empty has not been implemented")

  [[{:title "half-int-d inside manage block" :tag "d-higher"}]]
  (manage
   (mapv half-int-d [1 2 3 4])
   (option :use-empty [] [])
   (on :odd-number []
       (default)))
  => [])

[[:subsection {:title "escalate" :tag "api-escalate"}]]

"The `escalate` special form is used to add additional information to the issue and raised to higher managers. In the following example, if a `3` or a `5` is seen, then the flag `:three-or-five` is added to the issue and the `:odd-number` flag is set false."

[[{:numbered false}]]
(fact
  (defn half-array-e [arr]
    (manage
      (mapv half-int-d arr)
      (on {:value (fn [v] (#{3 5} v))} [value]
          (escalate [:three-or-five {:odd-number false}]))))

  (manage
    (half-array-e [1 2 3 4 5])
    (on :odd-number [value]
        (continue (* value 10)))
    (on :three-or-five [value]
        (continue (* value 100))))
    => [10 1 300 2 500])

"Program decision points can be changed by higher level managers through `escalate`"

[[{:numbered false}]]
(fact
  (defn half-int-f [n]
   (manage
     (if (= 0 (mod n 2))
       (quot n 2)
       (raise [:odd-number {:value n}]
         (option :use-nil [] nil)
         (option :use-custom [n] n)
         (default :use-nil)))

      (on :odd-number []
        (escalate :odd-number
          (option :use-zero [] 0)
          (default :use-custom :nan)))))

  (half-int-f 3) => :nan  ;; (instead of nil)
  (mapv half-int-f [1 2 3 4])

  => [:nan 1 :nan 2] ;; notice that the default is overridden

  (manage
    (mapv half-int-f [1 2 3 4])
    (on :odd-number []
      (choose :use-zero)))

  => [0 1 0 2]   ;; using an escalated option
)

"Options specified higher up are favored:"

[[{:numbered false}]]
(fact
   (manage
    (mapv half-int-f [1 2 3 4])
    (on :odd-number []
      (choose :use-nil)))

    => [nil 1 nil 2]

    (manage
     (mapv half-int-f [1 2 3 4])
     (on :odd-number []
       (choose :use-nil))
     (option :use-nil [] nil))

    => nil  ;; notice that the :use-nil is overridden
)




[[:section {:title "hooks"}]]

"When working with jvm libraries, there will always be exceptions. ribol provides macros to hook into thrown exceptions. Because all of the three methods are macros involving `try`, they all support the `finally` clause."

[[:subsection {:title "raise-on"}]]

"`raise-on` hooks ribol into the java exception system. Once an exception is thrown, it can be turned into an issue:"

[[{:numbered false}]]
(fact
  (raise-on [ArithmeticException :divide-by-zero]
            (/ 4 2))
  => 2

  (raise-on [ArithmeticException :divide-by-zero]
            (/ 1 0))
  => (raises-issue {:divide-by-zero true})

  (manage
   (raise-on [ArithmeticException :divide-by-zero]
             (/ 1 0))
   (on :divide-by-zero []
       (continue :infinity)))
  => :infinity)

"Any thrown clojure.lang.ExceptionInfo objects will be raised as issues."

[[{:numbered false}]]
(fact
  (raise-on []
            (throw (ex-info "" {:a 1 :b 2})))
  => (raises-issue {:a 1 :b 2}))


"Multiple exceptions are supported, as well as the finally clause."

[[{:numbered false}]]
(fact
  (raise-on [[NumberFormatException ArithmeticException] :divide-by-zero
             Throwable :throwing]
            (throw (Throwable. "oeuoeu"))
            (finally (println 1)))
  => (raises-issue {:throwing true}) ;; prints 1
  )




[[:subsection {:title "raise-on-all"}]]

"`raise-on-all` will raise an issue on any `Throwable`"

[[{:numbered false}]]
(fact
  (manage
   (raise-on-all :error (/ 4 2))
   (on :error []
       (continue :none)))
  => 2

  (manage
   (raise-on-all :error (/ nil nil))
   (on :error []
       (continue :none)))
  => :none)

[[:subsection {:title "anticipate"}]]

"`anticipate` is another way to perform try and catch. Instead catching exceptions at the bottom of the block, it is possible to anticipate what exceptions will occur and deal with them directly. Anticipate  also supports the `finally` clause"

[[{:numbered false}]]
(fact
  (anticipate [ArithmeticException :infinity]
              (/ 1 0))
  => :infinity

  (anticipate [ArithmeticException :infinity
               NullPointerException :null]
              (/ nil nil))
  => :null)






(ns midje-doc.strategies
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]
            [midje-doc.ribol-guide :refer [raises-issue]]))


[[:chapter {:title "Control Strategies"}]]

"This is a comprehensive (though non-exhaustive) list of program control strategies that can be used with `ribol`. It can be noted that the `try/catch` paradigm can implement sections [{{normal}}](#normal) and [{{catch}}](#catch). Other clojure restart libraries such as `errorkit`, `swell` and `conditions` additionally implement sections [{{continue}}](#continue), [{{choose}}](#choose) and [{{choose-more}}](#choose-more).

`ribol` supports novel (and more natural) program control mechanics through the `escalate` ([{{escalate}}](#escalate)), `fail` ([{{fail}}](#fail)) and  `default` ([{{default}}](#default)) special forms as well as branching support in the `on` special form ([{{on-form}}](#on-form))."

[[:section {:title "Normal"}]]
(facts
  [[:subsection {:title "No Raise"}]]
  "The most straightforward code is one where no issues raised:"

  [[{:tag "ribol-normal-eq" :title "No Issues"}]]
  (manage                          ;; L2
    [1 2 (manage 3)])              ;; L1 and L0
  => [1 2 3]

  [[:image {:src "img/norm_normal.png" :height "300px" :title "No Issues Flow"}]]

  [[:subsection {:title "Issue Raised"}]]

  "If there is an issue raised with no handler, it will `throw` an exception."

  [[{:title "Unmanaged Issue"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise {:A true}))])      ;; L0
  => (raises-issue {:A true})

  [[:image {:src "img/norm_unmanaged.png" :height "300px" :title "Unmanaged Issue Flow"}]])


[[:section {:title "Catch"}]]

(facts
  "Once an issue has been raised, it can be handled within a managed scope through the use of 'on'. 'manage/on' is the equivalent to 'try/catch' in the following two cases:"

  [[:subsection {:title "First Level Catch"}]]
  [[{:title "Catch on :A"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => [1 2 :A]
  [[:image {:src "img/catch_flow_a.png" :height "300px" :title "Catch on :A Flow"}]]

  [[:subsection {:title "Second Level Catch"}]]
  [[{:title "Catch on :B"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => :B

  [[:image {:src "img/catch_flow_b.png" :height "300px" :title "Catch on :B Flow"}]]
  )


[[:section {:title "Continue"}]]

(facts
  "The 'continue' form signals that the program
   should resume at the point that the issue
   was raised."

  [[:subsection {:title "First Level Continue"}]]
  "In the first case, this gives the same result as `try/catch`."

  [[{:title "Continue on :A"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3A]
    [[:image {:src "img/continue_flow_a.png" :height "300px" :title "Continue on :A Flow"}]]


  [[:subsection {:title "Second Level Continue"}]]

  "However, it can be seen that when 'continue' is used
   on the outer manage blocks, it provides the 'manage/on'
   a way for top tier forms to affect the bottom tier forms
   without manipulating logic in the middle tier"
  [[{:title "Continue on :B"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3B]

  [[:image {:src "img/continue_flow_b.png" :height "300px" :title "Continue on :B Flow"}]]
)


[[:section {:title "Choose"}]]

(facts


  "`choose` and `option` work together within manage scopes. A raised issue can have options attached to it, just a worker might give their manager certain options to choose from when an unexpected issue arises. Options can be chosen that lie anywhere within the manage blocks."

  [[:subsection {:title "Choose Lower-Level"}]]

  [[{:title "Choose :X"}]]
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
          (raise :A                  ;; L0
                 (option :X [] :3X)) ;; X
          (on :A []                  ;; H1A
              (choose :X))
          (option :Y [] :3Y))]       ;; Y
     (option :Z [] :3Z))              ;; Z
  => [1 2 :3X]

    [[:image {:src "img/choose_flow_x.png" :height "300px" :title "Choose :X Flow"}]]

  "However in some cases, upper level options can be accessed as in this case. This can be used to set global strategies to deal with very issues that have serious consequences if it was to go ahead.

   An example maybe a mine worker who finds a gas-leak. Because of previously conveyed instructions, he doesn't need to inform his manager and shuts down the plant immediately."

  [[:subsection {:title "Choose Upper-Level"}]]


  [[{:title "Choose :Z"}]]
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :Z))
         (option :Y [] :3Y))]       ;; Y
   (option :Z [] :3Z))              ;; Z
  => :3Z

  [[:image {:src "img/choose_flow_z.png" :height "300px" :title "Choose :Z Flow"}]]

  )


[[:section {:title "Choose - More Strategies" :tag "choose-more"}]]
(facts

  [[:subsection {:title "Overridding An Option"}]]
  "If there are two options with the same label,
  choose will take the option specified at the highest
  management level. This means that managers at higher
  levels can over-ride lower level strategies."

  [[{:title "Choose :X1"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (option :X [] :3X0)) ;; X0 - This is ignored
         (on :A []                   ;; H1A
           (choose :X))
         (option :X [] :3X1))]       ;; X1 - This is chosen
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X1]

  [[:image {:src "img/choose_flow_x1.png" :height "300px" :title "Choose :X1 Flow"}]]


  [[:subsection {:title "Default Option"}]]
  "Specifying a 'default' option allows the raiser to
   have autonomous control of the situation if the issue
   remains unhandled."
  [[{:title "Choose Default"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X))  ;; X
         (option :Y [] :3Y))]        ;; Y
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X]

  [[:image {:src "img/choose_default_x.png" :height "300px" :title "Choose Default Flow"}]]

  [[:subsection {:title "Overriding Defaults"}]]

  "This is an example of higher-tier managers
   overriding options"
   [[{:title "Choose Default :X2"}]]

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X0)) ;; X0
         (option :X [] :3X1))]       ;; X1
    (option :X [] :3X2))             ;; X2
  => :3X2

  [[:image {:src "img/choose_default_x2.png" :height "300px" :title "Choose Default :X2 Flow"}]]
  )


[[:section {:title "Escalate"}]]

(facts
  [[:subsection {:title "Simple Escalation"}]]

  "When issues are escalated, more information
   can be added and this then is passed on to
   higher-tier managers"
  [[{:title "Escalate :B"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
            (escalate :B)))]
   (on :B []                         ;; H2B
       (continue :3B)))
  => [1 2 :3B]

    [[:image {:src "img/escalate_norm.png" :height "300px" :title "Escalate :B Flow"}]]

  [[:subsection {:title "Escalation with Options"}]]
  "More options can be added to escalate. When
  these options are chosen, it will continue at the
  point in which the issue was raised."
  [[{:title "Escalate :B, Choose :X"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
             (escalate
              :B
              (option :X [] :3X))))] ;; X
    (on :B []                        ;; H2B
        (choose :X)))
  => [1 2 :3X]

  [[:image {:src "escalate_options.png" :height "300px" :title "Escalate :B, Choose :X Flow"}]]
    )


[[:section {:title "Fail"}]]
(facts
  "Fail forces a failure. It is used where there is already a default
   option and the manager really needs it to fail."

  [[{:title "Force Failure"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                 ;; H1A
             (fail :B)))])
  => (raises-issue {:A true :B true})
  [[:image {:src "img/fail.png" :height "300px" :title "Force Fail Flow"}]]
)


[[:section {:title "Default"}]]
(facts
  "Default short-circuits higher managers so that
   the issue is resolved internally."

    [[{:title "Force Default"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                  ;; H1A
             (default)))]
   (on :A [] (continue 3)))
  => [1 2 :X]


  [[:subsection {:title "Escalation with Defaults"}]]
  "This is `default` in combination with `escalate` to do some very complex jumping around."

  [[{:title "Escalate :B, Choose Default"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
           (option :X [] :X))        ;; X
         (on :A []                   ;; H1A
             (escalate
              :B
              (default :X))))]       ;; D1
    (on :B []                        ;; H2B
        (default)))
  => [1 2 :X]

  [[:image {:src "img/escalate_default_x2.png" :height "300px" :title "Escalate :B, Choose Default Flow"}]])

[[:section {:title "Branch Using On" :tag "on-form"}]]
(facts
  "Ribol strategies can also be combined within the `on` handler. In the following example, it can be seen that the `on :error` handler supports both `escalate` and `continue` strategies."

  [[{:title "Input Dependent Branching"}]]
  (manage (manage
           (mapv (fn [n]
                   (raise [:error {:data n}]))
                 [1 2 3 4 5 6 7 8])
           (on :error [data]
               (if (> data 5)
                 (escalate :too-big)
                 (continue data))))
          (on :too-big [data]
              (continue (- data))))
  => [1 2 3 4 5 -6 -7 -8]

  "Using branching strategies with `on` much more complex interactions can be constructed beyond the scope of this document."
  )

[[:chapter {:title "End Notes"}]]

"For any feedback, requests and comments, please feel free to lodge an issue on github or contact me directly.

Chris.
"
