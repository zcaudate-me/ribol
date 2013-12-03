(ns ribol.test-ribol-cljs
  (:require [ribol.cljs :refer :all]
            [midje.sweet :refer :all]))

(macroexpand-1
 '(manage
   (raise :error)
   (on :error [] 0)))
 '(binding [ribol.cljs/*managers* (cons {:id :G__8185, :handlers [{:checker :error, :fn (clojure.core/fn [{:keys []}] 0)}], :options {}} ribol.cljs/*managers*) ribol.cljs/*optmap* (merge {} ribol.cljs/*optmap*)] (try (raise :error) (catch ExceptionInfo ex# (ribol.cljs/manage-signal {:id :G__8185, :handlers [{:checker :error, :fn (clojure.core/fn [{:keys []}] 0)}], :options {}} ex#))))
