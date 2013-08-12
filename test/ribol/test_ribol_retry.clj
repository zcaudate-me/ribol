(ns ribol.test-ribol
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

"Creating a integer division mapper that is impervious to
"

(defn int-div [n m]
  (prn n 'divide-by m)
  (anticipate
   [ArithmeticException
    (do (println  "- div-error - \n")
        (raise [:div-error {:numer n :denom m}]))
    #{ClassCastException NullPointerException}
    (do (println  "- input-error - \n")
        (raise [:input-error {:numer n :denom m}]))]
   (let [q   (quot n m)
         diff (- n (* q m))]
     (if (zero? diff)
       (do (println  "- CORRECT! - \n")
           q)
       (do (println  "- not-exact - \n")
           (raise [:not-exact {:numer n :denom m :diff diff}]))))))

(defn int-div-pairs [arr]
  (manage
   (mapv #(apply int-div %) arr)
   (on :input-error [numer denom]
       (cond (keyword? numer)
             (continue (int-div (name numer) denom))
             (keyword? denom)
             (continue (int-div numer (name denom)))
             (string? numer)
             (continue
              (int-div (anticipate [NumberFormatException 0]
                                   (Integer/parseInt numer)) denom))
             (string? denom)
             (continue
              (int-div numer (anticipate [NumberFormatException 0]
                                         (Integer/parseInt denom))))
             (nil? denom)
             (continue (int-div numer 0))
             (nil? numer)
             (continue (int-div 0 denom))))
   (on :div-error [numer denom]
       (cond (= 0 denom)
             (do (println "- USING INFINITY -\n")
               (continue :infinity))
             :else :failed))
   (on :not-exact [numer denom diff]
       (continue (int-div (- numer diff) denom)))))

(fact
  (int-div-pairs [[12 8] [:90 :9] [:10 nil] [nil :100] [:hello :hello]])
  => [1 10 :infinity 0 :infinity])


(comment
  12 divide-by 8
  - not-exact -

  8 divide-by 8
  - CORRECT! -

  :90 divide-by :9
  - input-error -

  "90" divide-by :9
  - input-error -

  "90" divide-by "9"
  - input-error -

  90 divide-by "9"
  - input-error -

  90 divide-by 9
  - CORRECT! -

  :10 divide-by nil
  - input-error -

  "10" divide-by nil
  - input-error -

  10 divide-by nil
  - input-error -

  10 divide-by 0
  - div-error -

  - USING INFINITY -

  nil divide-by :100
  - input-error -

  nil divide-by "100"
  - input-error -

  nil divide-by 100
  - input-error -

  0 divide-by 100
  - CORRECT! -

  :hello divide-by :hello
  - input-error -

  "hello" divide-by :hello
  - input-error -

  "hello" divide-by "hello"
  - input-error -

  0 divide-by "hello"
  - input-error -

  0 divide-by 0
  - div-error -

  - USING INFINITY -
)
