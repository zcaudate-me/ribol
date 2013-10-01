(ns midje-doc.ribol-unlucky-numbers
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))
            
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

For the still sceptical, I'm proposing a **challenge**: to reimplement `ints-to-strs-christian` without changing `unlucky-numbers` or `int-to-str` using only `try` and `catch`. I am scared just thinking about it..."
