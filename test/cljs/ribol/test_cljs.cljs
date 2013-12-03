(ns ribol.test_cljs
  (:require [ribol.cljs :refer [hash-map? hash-set? assoc-if 
                                parse-contents check-contents 
                                create-issue create-signal
                                create-catch-signal create-choose-signal
                                create-exception
                                raise-valid-handler]]
            [purnam.native :refer [js-type]]
            [purnam.core])
  (:use-macros [purnam.test :only [fact]]))

(defn truthy? [x]
   (= false (not x)))

(defn falsey? [x]
  (= true (not x)))
  
(fact "hash-map?"
  (hash-map? {:a 0}) => true)

(fact "hash-set?"
  (hash-set? #{0 1 2}) => true)

(fact "assoc-if"
  (assoc-if {} :a nil :b 1) => {:b 1})

(fact "parse-contents"
  (parse-contents {:a 0}) => {:a 0}
  (parse-contents :a) => {:a true}
  (parse-contents [:a {:b 0}]) => {:a true :b 0})

(fact "check-contents"
  (check-contents {:a 0} '_) => true
  (check-contents {:a 0} :a) => truthy?
  (check-contents {:a 0} [:a]) => true
  (check-contents {:a 0} [:b]) => falsey?
  (check-contents {:a 0} {:a 0}) => true
  (check-contents {:a 0} {:a zero?}) => true
  (check-contents {:a 0} [{:a zero?}]) => true
  (check-contents {:a 0} #{:a :b}) => truthy?
  (check-contents {:a 0} [{:a zero?} :b]) => falsey?)
  
(fact "create-issue"
  (create-issue :error "error" {} nil) 
  => {:id :G__1 
      :contents {:error true} 
      :msg "error"
      :options {} 
      :optmap {} 
      :default nil})
      
(fact "create-signal"
  (create-signal {:contents :a :msg "error"} :catch)
  => (ex-info "error :catch - :a"
       {:ribol.cljs/contents :a
        :ribol.cljs/signal :catch}))
        
