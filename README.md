# ribol

`ribol` is a conditional restart library for clojure inspired by `errorkit`, having a more readable syntax, and designed with the base `clojure.lang.ExceptionInfo` type in mind.

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.1.5"]

### Provides

 - Issue (exception) handling using maps for data as opposed to typed classes
 - Passing data along with exceptions
 - Tight integration with `ex-info` and `ex-data`
 - Five different issue handlers - `catch`, `continue`, `choose`, `escalate` and `default`
 - A syntax that is not so confusing (for me anyways)

### Rational:
In the author's experience, there are two forms of 'exceptions' that a programmer will encounter:

 1. Programming Mistakes - These are a result of logic and reasoning errors, should not occur in normal operation and should be eliminated as soon as possible. The best strategy for dealing with this is to write unit tests and have functions fail early with a clear message to the programmer what the error is.
    - Null pointers
    - Wrong inputs to functions

 2. Exceptions due to circumstances - These are circumstancial and should be considered part of the normal operation of the program.
    - A database connection going down
    - A file not found
    - User input not valid

The common method of `try` and `catch` is not really needed when dealing with the Type 1 exceptions and a little too weak when dealing with those of Type 2. There are numerous resources that explain why this is the case (will put in links). The net effect of using only the `try/catch` paradigm in application code is that in order to mitigate these Type 2 exceptions, there requires a lot of defensive programming. This turns the middle level of the application into spagetti code with program control flow (`try/catch`) mixed in with program logic . Conditional restarts provide a way for the top-level application to specify strategies to deal with Type 2 exceptions much more cleanly.

### Other Libraries

There are two other conditional restart libraries for clojure - `errorkit` and `swell`

  - `errorkit` provided the guiding architecture for `ribol`. However, ribol updates `errorkit` with more options for controlling exceptions, uses `ex-info` which is part of core and has an updated and more understandable syntax.

  - `swell` was written specifically to work with the `slingshot` `try+/catch+` packages and I thought that the two together carried too much baggage. `ribol` has no such dependencies.

## Tutorial

Because we are dealing with exceptions, the best way to do this is to use a test framework so that exceptions can be seen. In this case, we are using midje:

#### 0 - setup

We setup midje and define two checkers, `has-signal` and `has-content` which strips out keys within the thrown `ExceptionInfo` exception

```clojure
(ns example.ribol
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

(defn has-signal [sigtype]
  (fn [e]
    (-> e ex-data :ribol.core/signal (= sigtype))))

(defn has-content [content]
  (fn [ex]
    (-> e ex-data :ribol.core/contents (= content))))
```

### IMPORTANT: For brevity reasons, we will be omitting the fact form

`(fact <expression> => <result>)` will be written as `<expression> => <result>` it will be up to the user to put the `(fact)` form around the expression

#### 1 - raise

The keyword `raise` is used to raise an 'issue'. At the simplest, when there is no `manage` blocks, `raise` just throws an ExceptionInfo object stating what the error is:

```clojure
(raise {:error true})
=> (throws clojure.lang.ExceptionInfo
           " :unmanaged - {:error true}")
```

The data is accessible as the 'content' of the raised 'issue':

```clojure
(raise {:error true})
=> (throws (has-signal :unmanaged)
           (has-content {:error true}))
```

The 'contents' of the issue can be a hash-map, a keyword or a vector of keywords and hash-maps. This is a shortcut is to use a keyword to create a map with the value `true`:

```clojure
(raise :error)
=> (throws (has-signal :unmanaged)
           (has-content {:error true}))
```

A vector can be create a map with more powerful descriptions about the issue:

```clojure
(raise [:flag1 :flag2 {:data 10}])
=> (throws (has-signal :unmanaged)
           (has-content {:flag1 true
                         :flag2 true
                         :data 10}))
```

#### 1.1 - raise (option and default)

Strategies for an unmanaged issue can be specified within the raise form. The first specifies two options and the specifies the default option as :use-nil

```clojure
(raise :error
  (option :use-nil [] nil)
  (option :use-custom [n] n)
  (default :use-nil)
=> nil
```

This example sets the default as :use-custom with an argument of 10

```clojure
(raise :error
  (option :use-nil [] nil)
  (option :use-custom [n] n)
  (default :use-custom 10)
=> 10
```

If there is no default selection, then an exception will be thrown as per previously seen:

```clojure
(raise :error
  (option :use-nil [] nil)
  (option :use-custom [n] n))
 => (throws (has-signal :unmanaged)
            (has-content {:error true}))
```

#### 2 - manage

When an issue is raised, the manage blocks set up execution scope and provide handlers and options to manage program flow. We define a function `half-int-a`, checking to see if the input is even, if is it, divides by 2. if it is not, it raises an :odd-number issue.

```clojure
(defn half-int-a [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}])))
```

The output of `half-int-a` can be seen below:

```clojure
(half-int-a 2) => 1
(half-int-a 3)  => (throws (has-signal :unmanaged)
             (has-content {:odd-number true
                           :value 3}))
```

This is the output of `half-int-a` used within a higher-order function:

```clojure
(mapv half-int-a [2 4 6]) => [1 2 3]
(mapv half-int-a [2 3 6])
=> (throws (has-signal :unmanaged)
           (has-content {:odd-number true
                         :value 3})))
```

#### 2.1 - on (catch handler)

Within the manage form, handlers can be specified with `on`:

```clojure
(manage
 (mapv half-int-a [1 2 3 4])
 (on :odd-number []
   "odd-number-exception"))
=> "odd-number-exception"
```

This resembles the classical try/catch situation and can be written as:

```clojure
(try
 (mapv half-int-a [1 2 3 4])
 (catch Throwable t
   "odd-number-exception"))
=> "odd-number-exception"
```

However, we can retrieve the contents of the issue by providing map keys that we wish to retrieve:

```clojure
(manage
 (mapv half-int-a [1 2 3 4])
 (on :odd-number [odd-number value]
   (str "odd-number: " odd-number ", value: " value)))
 => "odd-number: true, value: 1"
```

#### 2.2 - on (continue handler)

The `continue` special-form is used to continue the operation from the point that the issue was raised:

```clojure
(manage
 (mapv half-int-a [1 2 3 4])
 (on :odd-number []
   (continue :nan)))
=> [:nan 1 :nan 2]
```

Again, it can take keys of the 'contents' of the raised 'issue'

```clojure
(manage
 (mapv half-int-a [1 2 3 4])
 (on :odd-number [value]
     (continue (str value))))
=> ["1" 1 "3" 2]
```

#### 2.3 - on (choose handler)

The `choose` special form is used to jump to a `option`:

```clojure
(defn half-int-b [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (option :use-nil [] nil)
      (option :use-nan [] nan)
      (option :use-custom [n] n))))

(manage
 (mapv half-int-b [1 2 3 4])
 (on :odd-number [value]
     (choose :use-custom (/ value 2))))
=> [1/2 1 3/2 2]
```

It options can also be specified in the manage block itself

```clojure
(manage
 (mapv half-int-b [1 2 3 4])
 (on :odd-number [value]
     (choose :use-empty))
 (option :use-empty [] []))
=> []
```

#### 2.4 - on (default handler)

The `default` handler just short-circuits the raise process and skips managers further up to tell the function to use its default option.

```clojure
(defn half-int-c [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (option :use-nil [] nil)
      (option :use-custom [n] n)
      (default :use-custom :odd))))
```

```clojure
(manage
 (mapv half-int-b [1 2 3 4])
 (on :odd-number [value] (default)))
=> [:odd 1 :odd 2]
```

The default form can even refer to an option that has to be implemented higher up in scope.

```clojure
(defn half-int-d [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (default :use-empty))))

(manage
 (mapv half-int-d [1 2 3 4])
 (option :use-empty [] [])
 (on :odd-number [value]
   (default)))
=> []
```

#### 2.5 - on (escalate handler)

The `escalate` form is used to manipulate. In the following example, if a '3' or a '5' is seen, then the flag :three-or-five is added to the issue contents and the :odd-number flag is set to false:

```clojure
(defn half-array-d [arr]
  (manage
    (mapv half-int-d arr)
    (on {:value (fn [v] (#{3 5} v))} [value]
        (escalate [:three-or-five {:odd-number false}]))))


(manage
 (half-array-d [1 2 3 4 5])
 (on :odd-number [value]
     (continue (* value 10)))
 (on :three-or-five [value]
     (continue (* value 100))))
 => [10 1 300 2 500]
```

#### 3 - Overwriting defaults

How the program behaves can be changed by higher level managers through escalate

```clojure
(defn half-int-e [n]
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

(half-int-e 3) => :nan ;; (instead of nil)

(mapv half-int-e [1 2 3 4])
=> [:nan 1 :nan 2] ;; notice that the default is overridden

(manage
 (mapv half-int-e [1 2 3 4])
 (on :odd-number []
   (choose :use-zero)))
 => [0 1 0 2] ;; using an escalated option
```

Options specified higher up are favored:

```clojure
(manage
 (mapv half-int-e [1 2 3 4])
 (on :odd-number []
   (choose :use-nil)))
 => [nil 1 nil 2] ;; using th

 (manage
  (mapv half-int-e [1 2 3 4])
  (on :odd-number []
    (choose :use-nil))
  (option :use-nil [] nil))
=> nil  ;; notice that the :use-nil is overridden by the higher level manager
```

### Todos
- More test cases

## License
Copyright © 2013 Chris Zheng

Distributed under the MIT License
