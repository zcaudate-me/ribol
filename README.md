# ribol

`ribol` is a conditional restart library for clojure inspired by `errorkit`, having a more readable syntax, and designed with the base `clojure.lang.ExceptionInfo` type in mind.

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.1.5"]

### Rational:
In the author's experience, there are two forms of 'exceptions' that a programmer will encounter:

 1. Programming Mistakes - These are a result of logic and reasoning errors, should not occur in normal operation and should be eliminated as soon as possible. The best strategy for dealing with this is to write unit tests and have functions fail early with a clear message to the programmer what the error is.
    - Null pointers
    - Wrong inputs to functions

 2. Exceptions due to circumstances - These are circumstancial and should be considered part of the normal operation of the program.
    - A database connection going down
    - A file not found
    - User input not valid

The common method of `try` and `catch` is not really needed when dealing with the Type 1 of exceptions and a little too weak when dealing with the second. There are numerous resources that explain why this is the case but the net effect is that in order to mitigate these Type 2 exceptions, there requires alot of defensive programming that makes for spegetti code. Conditional restarts provide a way for the top-level application to specify strategies to deal with Type 2 exceptions much more cleanly.

### Why use ribol?

There are two other conditional restart libraries for clojure - `errorkit` and `swell`

  - `errorkit` provided the guiding architecture for `ribol`. However, ribol updates `errorkit` with more options for controlling exceptions, uses `ex-info` which is part of core and has an updated and more understandable syntax.

  - `swell` was written specifically to work with the `slingshot` `try+/catch+` packages and I thought that the two together carried too much baggage. `ribol` has no dependencies.

## Tutorial

Because we are dealing with exceptions, the best way to do this is to use a test framework. In this case, we are using midje

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

#### 1 - raise

The keyword `raise` is used to raise an 'issue' which . At the simplest, `raise` just throws an ExceptionInfo object stating what the error is:

```clojure
(fact "Raise by itself throws an ExceptionInfo"
  ;; Issues are raise in the form of a hashmap
  (raise {:error true})
  => (throws clojure.lang.ExceptionInfo
             " :unmanaged - {:error true}"))
```

The data is accessible as the 'content' of the raised 'issue'

```clojure
(fact "The content is accessible through ex-data"
  (raise {:error true})
  => (throws (has-signal :unmanaged)
             (has-content {:error true})))
```

The 'content' of the issue can be a hash-map, a keyword or a vector of keywords and hash-maps

```clojure
(facts
  "A shortcut is to use a keyword to create a map with the value `true`"
  (raise :error)
  => (throws (has-signal :unmanaged)
             (has-content {:error true}))

  "A vector can be create a map with more powerful descriptions about the issue"
  (raise [:flag1 :flag2 {:data 10}])

  => (throws (has-signal :unmanaged)
             (has-content {:flag1 true
                           :flag2 true
                           :data 10})))
```

#### 2 - manage

Firstly we define a function `half-int-a` to test. It basically checks to see if the input is odd, if it is, it raises an exception

```clojure
(defn half-int-a [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}])))

(fact "Testing half-int-a"
  (half-int-a 2) => 1
  (half-int-a 3)
  => (throws (has-signal :unmanaged)
             (has-content {:odd-number true
                           :value 3}))

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
     (choose :use-empty (/ value 2)))
 (option :use-empty [] [])
=> []
```

#### 2.4 - on (default handler)

The keyword `default` is used to specify what happens if the issue has not been handled. It the case below, the default option is to choose the :use-custom option with argument :odd.

```clojure
(defn half-int-c [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (option :use-nil [] nil)
      (option :use-custom [n] n)
      (default :use-custom :odd))))

(half-int-c 3)
=> :odd
```

The default form in

```clojure
(manage
 (mapv half-int-b [1 2 3 4])
 (on :odd-number [value] (default)))
=> [:odd 1 :odd 2]
```

The default form can even refer to an option that has to be implemented higher up in scope

```clojure
(defn half-into-d [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
      (default :use-empty))))

(manage
 (mapv half-int-d [1 2 3 4])
 (on :odd-number [value]
   (option :use-empty [] [])))
=> []
```

#### 2.5 - on (escalate handler)



## License

Copyright © 2013 Chris Zheng

Distributed under the MIT License
