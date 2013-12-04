(ns ribol.test-ribol-cljs
  (:require [ribol.cljs :refer :all]
            [midje.sweet :refer :all]))

(fact
  (macroexpand-1
   '(raise-on [js/Object :divide-by-zero]
              (throw (js-obj))))

  => '(try (throw (js-obj))
           (catch ExceptionInfo e#
             (ribol.cljs/raise [(ex-data e#) {:origin e#}]))
           (catch js/Object t#
             (ribol.cljs/raise [:divide-by-zero {:origin t#}]))))
