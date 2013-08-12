(ns ribol.test-ribol
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

;;#doc {:section "one"}

(defn has-data [sigtype]
  (fn [ex]
    (-> ex ex-data (= sigtype))))


(fact "Raise by itself throws an ExceptionInfo"

 ;; #doc {:label "one" :caption "Raising an exception"}
  (raise {:error true})
  => (throws clojure.lang.ExceptionInfo
             " - {:error true}")

  "It contains information that can be accessed"
  (raise {:error true})
  => (throws (has-data {:error true}))

  "A shortcut is to use a keyword to create a map with the value `true`"
  (raise :error)
  => (throws (has-data {:error true}))

  "A vector can be create a map with more powerful descriptions about the issue"
  (raise [:flag1 :flag2 {:data 10}])
  => (throws (has-data {:flag1 true
                        :flag2 true
                        :data 10})))


(defn half-int-a [n]
  (if (= 0 (mod n 2))
    (quot n 2)
    (raise [:odd-number {:value n}]
           (option :use-nil [] nil)
           (option :use-odd [] :odd)
           (option :use-custom [n] n))))

(fact "Testing half-int-a"
  (half-int-a 2) => 1
  (half-int-a 3)
  => (throws (has-data {:odd-number true
                        :value 3}))

  (mapv half-int-a [2 4 6]) => [1 2 3]
  (mapv half-int-a [2 3 6])
  => (throws (has-data {:odd-number true
                           :value 3})))

(fact
  (map half-int-a [1 2 3 4])
  => (throws (has-data {:odd-number true
                           :value 1}))

  (manage
   (mapv half-int-a [1 2 3 4])
   (on :odd-number [odd-number value]
       (str "odd-number: " odd-number ", value: " value)))
  => "odd-number: true, value: 1"


  (manage
   (mapv half-int-a [1 2 3 4])
   (on :odd-number [value]
       (str "hello " value)))
  => "hello 1"

  (manage
   (mapv half-int-a [1 2 3 4])
   (on :odd-number [value]
       (continue (str value))))
  => ["1" 1 "3" 2]

  (manage
   (mapv half-int-a [1 2 3 4])
   (on :odd-number [value]
       (choose :use-custom (/ value 2))))
  => [1/2 1 3/2 2]

  (manage
   (mapv half-int-a [1 2 3 4])
   (on :odd-number [value]
       (fail {:fail-with (* 10 value)})))
  => (throws (has-data {:odd-number true :fail-with 10 :value 1})))


(fact "Raise can specify its own options and a default"
  (raise :error
         (option :return-nil [] nil)
         (default :return-nil))
  => nil


  ;; If the default option is not there, it will throw.
  (raise :error
         (option :return-nil [] nil))
  => (throws (has-data {:error true}))

  ;; Options also accept arguments
  (raise :error
         (option :return-nil [] nil)
         (option :use-custom [n] n)
         (default :use-custom 10))
  => 10)
