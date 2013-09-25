# ribol

`ribol` is a conditional restart library for clojure inspired by `errorkit`, having a more readable syntax, and designed with the base `clojure.lang.ExceptionInfo` type in mind.

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.2.1"]

### Introduction

`ribol` provides a conditional restart system. It can also be thought of as an issue resolution system or `try++/catch++`. The library provides an alternative channel for resolving 'issues' (we use 'issues' here to differentiate from 'exceptions', although they are pretty much the same thing). It models a management structure, in which issues are reported to management and each level of management can then decide what course of action to take depending upon the issue and their own level of expertise:

- When circumstances arise that need the attention of higher level processes, an 'issue' would be raised that can be managed by any higher level process.

- An issue must have data as well as additional information attached:
  - options that can be taken to resolve the issue
  - a default option if there is no management intervention.

- Issues are managed through handlers that check for the nature of the issue and come up with the proper resolution process. There are six ways that a manager can deal with a raised issue:

  - directly (same as try/catch)
  - using `continue` to keep going with a specified value
  - using `choose` to specify an option
  - using `escalate` to notify higher level managers
  - using `default` to allow the issue to resolve itself
  - using `fail` to throw an exception

Using these six different different issue resolution commands, a programmer has the richness of language beyond the simple 'try/catch' statement at his/her command to be able to craft very complex process control flow strategies without mixing logic handling code in the middle tier. It can also create new ways of thinking about the problem beyond the standard throw/catch mechanism and offer more elegant ways to build programs.

Apart from the tutorial, interested users can peruse the [strategies](https://github.com/zcaudate/ribol/blob/master/test/ribol/test_ribol_strategies.clj) document (still a work in progress) to go through common restart strategies.

### Other Libraries

There are three other conditional restart libraries for clojure - [errorkit](https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/error_kit.clj), [swell](https://github.com/hugoduncan/swell) and [conditions](https://github.com/bwo/conditions)

  - `errorkit` provided the guiding architecture for `ribol`. However, ribol updates `errorkit` with more options for controlling exceptions, uses `ex-info` which is part of core and has an updated and more understandable syntax.

  - `swell` and `conditions` are written to work with [slingshot](https://github.com/scgilardi/slingshot) and `try+/catch+`.

### Novel Features

  - In addition to the other conditional restart Libraries, `ribol` offers three more ways of handling error: `escalate`, `fail` and `default`. As of version `0.2` of ribol, handlers are now much more flexible. As far as I can tell, it is the only library that allows this type of resolution switching (having an 'if' form in the 'on' handler to switch between `escalate` and `continue` depending on the value of `data`:

```clojure
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
  [1 2 3 4 5 -6 -7 -8])
```

 - Additionally, the follow macros `raise-on`, `raise-on-all` and `anticipate` offer ways to hook into the java exceptions. Its use can be seen here: [integer division](https://github.com/zcaudate/ribol/wiki/Robust-Integer-Divide)

### Rational:
In the author's experience, there are two forms of 'exceptions' that a programmer will encounter:

 1. Programming Mistakes - These are a result of logic and reasoning errors, should not occur in normal operation and should be eliminated as soon as possible. The best strategy for dealing with this is to write unit tests and have functions fail early with a clear message to the programmer what the error is.
    - Null pointers
    - Wrong inputs to functions

 2. Exceptions due to circumstances - These are circumstancial and should be considered part of the normal operation of the program.
    - A database connection going down
    - A file not found
    - User input not valid

The common method of `try` and `catch` is not really needed when dealing with the Type 1 exceptions and a little too weak when dealing with those of Type 2. There are numerous resources that explain why this is the case. This is from a question I asked on [stackoverflow](http://stackoverflow.com/questions/18008935/is-there-a-book-guide-for-implementing-a-conditional-restart-system).

The net effect of using only the `try/catch` paradigm in application code is that in order to mitigate these Type 2 exceptions, there requires a lot of defensive programming. This turns the middle level of the application into spagetti code with program control flow (`try/catch`) mixed in with program logic . Conditional restarts provide a way for the top-level application to specify strategies to deal with Type 2 exceptions much more cleanly.


## Tutorial

Because we are dealing with exceptions, the best way to do this is to use a test framework so that exceptions can be seen. In this case, we are using midje:

#### 0 - setup

We setup midje and define the checker `has-data` which strips out keys within the thrown `ExceptionInfo` exception

```clojure
(ns example.ribol
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

(defn has-data [data]
  (fn [ex]
    (-> ex ex-data (= data))))
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
=> (throws (has-data {:error true}))
```

The 'contents' of the issue can be a hash-map, a keyword or a vector of keywords and hash-maps. This is a shortcut is to use a keyword to create a map with the value `true`:

```clojure
(raise :error)
=> (throws (has-data{:error true}))
```

A vector can be create a map with more powerful descriptions about the issue:

```clojure
(raise [:flag1 :flag2 {:data 10}])
=> (throws (has-data {:flag1 true
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
 => (throws (has-data {:error true}))
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
(half-int-a 3)
=> (throws (has-data {:odd-number true
                      :value 3}))
```

This is the output of `half-int-a` used within a higher-order function:

```clojure
(mapv half-int-a [2 4 6]) => [1 2 3]
(mapv half-int-a [2 3 6])
=> (throws (has-data {:odd-number true
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
