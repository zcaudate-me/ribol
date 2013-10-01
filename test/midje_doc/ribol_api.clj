(ns midje-doc.ribol-api
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

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
  `(throws (fn [e#] 
             ((just ~payload) (ex-data e#)))))

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
  => (raises-issue {:divide-by-zero true 
                    :origin #(instance? ArithmeticException %)})

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
  => (raises-issue {:a 1 :b 2
                    :origin #(= (ex-data %) {:a 1 :b 2})}))


"Multiple exceptions are supported, as well as the finally clause."

[[{:numbered false}]]
(fact
  (raise-on [[NumberFormatException ArithmeticException] :divide-by-zero
             Throwable :throwing]
            (throw (Throwable. "oeuoeu"))
            (finally (println 1)))
  => (raises-issue {:throwing true
                    :origin #(instance? Throwable %)}) ;; prints 1
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
            