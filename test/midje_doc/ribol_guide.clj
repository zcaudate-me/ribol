(ns midje-doc.ribol-guide
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Installation"}]]

"In `project.clj`, add to dependencies:"

[[{:numbered false}]]
(comment  [im.chit/ribol "0.2.1"])

"All functionality is found contained in `ribol.core`"

[[{:numbered false}]]
(comment  (use 'ribol.core))

[[:chapter {:title "Overview"}]]
"
This is quite a comprehensive guide to `ribol` and how conditional restart libraries may be used. There are currently three other conditional restart libraries for clojure:

  - [`errorkit`](https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/error_kit.clj) was the first and provided the guiding architecture for this library.


  - [`swell`](https://github.com/hugoduncan/swell) and [`conditions`](https://github.com/bwo/conditions) have been written to work with [`slingshot`](https://github.com/scgilardi/slingshot).

A walkthough and guide for `ribol` and its special forms can be found in [Chapter {{syntax-walkthrough}}](#syntax-walkthrough).

For those that wish to know more about conditional restarts, a comparison of different strategies that can be implemented is done in [Chapter {{control-strategies}}](#control-strategies). 

"

[[:section {:title "Introduction"}]]
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

[[:section {:title "raise vs throw"}]]

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

[[:section {:title "manage/on vs try/catch"}]]

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

[[:chapter {:title "Syntax Walkthrough"}]]

[[:section {:title "raise"}]]
"The keyword `raise` is used to raise an 'issue'. At the simplest, when there is no `manage` blocks, `raise` just throws a `clojure.lang.ExceptionInfo` object ([e.{{raise-type}}](#raise-type)). The payload of the issue can be extracted using `ex-data` ([e.{{raise-data}}](#raise-data))."


(facts
  [[{:title "raise is of type clojure.lang.ExceptionInfo" :tag "raise-type"}]]
  (raise {:error true})
  => (throws clojure.lang.ExceptionInfo)

  [[{:title "`ex-data` extracts the issue payload" :tag "raise-data"}]]
  (try
    (raise {:error true})
    (catch clojure.lang.ExceptionInfo e
      (ex-data e)))
  => {:error true})

"The payload can be expressed as a `hash-map`, a `keyword` or a `vector`. We define the `raises-issue` ([e.{{payload-macro}}](#payload-macro)) macro to help explore this a little further"

[[{:title "`payload` helper definition" :tag "payload-macro"}]]
(defmacro raises-issue [payload]
 `(throws (fn [e#] (= (ex-data e#) ~payload))))


[[:subsection {:title "hash-map payloads"}]]
(facts
  "Because the issue can be expressed as a hash-map, it is more general than using a class to represent exceptions."
 (raise {:error true :data "data"})
  => (raises-issue {:error true :data "data"}))

[[:subsection {:title "keyword payloads"}]]

"When a `keyword` is used, it is shorthand for a map with having the specified keyword with value `true`."
(facts
  (raise :error)
  => (raises-issue {:error true}))

[[:subsection {:title "vector payloads"}]]
"Vectors can contain only keywords ([e.{{vector-keyword-payload}}](#vector-keyword-payload)) or both maps and keywords ([e.{{vector-mixed-payload}}](#vector-mixed-payload)). They are there mainly for syntacic sugar"

(facts
  [[{:title "vector keyword payload"}]]
   (raise [:lvl-1 :lvl-2 :lvl-3])
  => (raises-issue {:lvl-1 true :lvl-2 true :lvl-3 true})

  [[{:title "vector mixed payload"}]]
  (raise [:error {:data "data"}])
   => (raises-issue {:error true :data "data"}))

[[:section {:title "raise - options"}]]

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

[[:section {:title "manage"}]]

"Manage blocks set up execution scope and provide handlers and options to manage abnormal program flow.

A function `half-int` is defined ([e.{{half-int-definition}}](#half-int-definititon)). It checks to see if the `input` is even. If the `input` is even, the function returns the `input` divided by `2`. if it is not, it raises an `:odd-number` issue."

(facts

  [[{:title "half-int definition"}]]
  (defn half-int [n]
    (if (= 0 (mod n 2))
      (quot n 2)
      (raise [:odd-number {:value n}])))
  
  "The output of `half-int` can be seen below for even ([e.{{half-int-even}}](#half-int-even)) and odd ([e.{{half-int-odd}}](#half-int-odd))numbers"

  [[{:title "half-int even"}]]
  (half-int 2) => 1

  [[{:title "half-int odd"}]]
  (half-int 3)
  => (raises-issue {:odd-number true :value 3}))

"Outputs of `half-int` used within a higher-order function are seen for normal flow ([e.{{half-int-normal-flow}}](#half-int-normal-flow)) and abnormal flow ([e.{{half-int-abnormal-flow}}](#half-int-aabnormal-flow))"
(facts
  [[{:title "half-int normal flow"}]]
  (mapv half-int [2 4 6]) => [1 2 3]

  [[{:title "half-int abnormal flow"}]]
  (mapv half-int [2 3 6])
  => (raises-issue {:odd-number true :value 3}))

[[:section {:title "on"}]]

(facts
  "Within the `manage` form, issue handlers are specified with `on`:"

  [[{:title "handling issues"}]]
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number []
       "odd-number-exception"))

  => "odd-number-exception"

  "This resembles the classical `try/catch` situation:"

  [[{:title "try/catch version"}]]
  (try
    (mapv half-int [1 2 3 4])
    (catch Throwable t
      "odd-number-exception"))
  => "odd-number-exception"

  "However, we can retrieve the contents of the issue by providing map keys that we wish to retrieve:"

  [[{:title "accessing issue data"}]]
  (manage
   (mapv half-int [1 2 3 4])
   (on :odd-number [odd-number value]
       (str "odd-number: " odd-number ", value: " value)))
  => "odd-number: true, value: 1")

[[:section {:title "on - continue"}]]

(facts
  "The `continue` special form is used to continue the operation from the point that the `issue` was raised ([e.{{continue-using-nan}}](#continue-using-nan)). It must be pointed out that this is impossible to do using the `try/catch` paradigm because the all the information from the stack will be lost. 
  
  The `on` handler can take keys of the `payload` of the raised `issue` as parameters. In [e.{{continue-using-str}}](#continue-using-str), a vector containing strings of the odd numbers are formed. Whereas in [e.{{continue-using-fractions}}](#continue-using-fractions), the on handler puts in fractions instead."

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
  

[[:section {:title "on - fail"}]]

"`fail` special form will forcibly cause an exception to be thrown. It is used when there is no need to advise managers of situation. More data can be added to the failure ([e.{{fail-example}}](#fail-example))."

(facts
  [[{:title "fail example"}]]
  (manage
    (mapv half-int [1 2 3 4])
    (on :odd-number []
      (fail [:unhandled :error])))
  => (raises-issue {:value 1 :odd-number true :unhandled true :error true})
  )

[[:section {:title "on - choose"}]]

"The `choose` special form is used to jump to a `option`. A new function `half-int-b` ([e.{{half-int-b-definition}}](#half-int-b-definition)) is defined giving options to jump to within the `raise` form. Its usage can be seen in [e.{{choose-ex-1}}](#choose-ex-1) where different paths can be chosen depending upon `:value`. An option can also be specified in the manage block ([e.{{choose-ex-2}}](#choose-ex-2)). Options can also be overridden when specified in higher manage blocks ([e.{{choose-ex-3}}](#choose-ex-3)). "

[[{:title "half-int-b definition" }]]
(defn half-int-b [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (option :use-nil [] nil)
      (option :use-nan [] :nan)
      (option :use-custom [n] n))))

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
  => nil
  )


[[:section {:title "on - default"}]]


" The `default` handler short-circuits the raise process and skips managers further up to use an issue's default option. A function is defined ([e.{{half-int-c-definition}}](#half-int-c-definition)) and used ([e.{{half-int-c-example}}](#half-int-c-example)) to show how the `default` form behaves. "


(facts
  [[{:title "half-int-c definition"}]]
  (defn half-int-c [n]
    (if (= 0 (mod n 2))
      (quot n 2)
      (raise [:odd-number {:value n}]
             (option :use-nil [] nil)
             (option :use-custom [n] n)
             (default :use-custom :odd))))

  [[{:title "half-int-c example"}]]
  (manage
   (mapv half-int-c [1 2 3 4])
   (on :odd-number [value] (default)))
  => [:odd 1 :odd 2]


  "The default form can even refer to an option that has to be implemented higher up in scope. Another function is defined ([e.{{half-int-d-definition}}](#half-int-d-definition)) and used ([e.{{d-alone}}](#d-alone) and [e.{{d-higher}}](#d-higher)) to show this particular case."

  [[{:tag "half-int-d-definition"}]]
  (defn half-int-d [n]
    (if (= 0 (mod n 2))
      (quot n 2)
      (raise [:odd-number {:value n}]
             (default :use-empty))))

  [[{:title "half-int-d alone" :tag "d-alone"}]]
  (half-int-d 3)
  => (throws java.lang.Exception "RAISE_CHOOSE: the label :use-empty has not been implemented")

  [[{:title "half-int-d inside handler" :tag "d-higher"}]]
  (manage
   (mapv half-int-d [1 2 3 4])
   (option :use-empty [] [])
   (on :odd-number []
       (default)))
  => []

  )

[[:section {:title "on - escalate"}]]

"The `escalate` form is used to add additional information to the issue. In the following example, if a `3` or a `5` is seen, then the flag `:three-or-five` is added to the issue and the `:odd-number` flag is set false. "

(facts
  [[{:tag "half-array-e-definition"}]]
  (defn half-array-e [arr]
    (manage
      (mapv half-int-d arr)
      (on {:value (fn [v] (#{3 5} v))} [value]
          (escalate [:three-or-five {:odd-number false}]))))

  [[{:title "escalation example" :tag "escalate-ex-1"}]]
  (manage
    (half-array-e [1 2 3 4 5])
    (on :odd-number [value]
        (continue (* value 10)))
    (on :three-or-five [value]
        (continue (* value 100))))
    => [10 1 300 2 500]
)

"Program decision points can be changed by higher level managers through `escalate`"

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

[[:file {:src "test/midje_doc/strategies.clj"}]]

[[:chapter {:title "End Notes"}]]

"For any feedback, requests and comments, please feel free to lodge an issue on github or contact me directly.

Chris.
"