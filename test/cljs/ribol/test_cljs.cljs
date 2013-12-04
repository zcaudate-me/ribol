(ns ribol.test_cljs
  (:require [ribol.cljs :refer [hash-map? hash-set? assoc-if 
                                parse-contents check-contents 
                                create-issue create-signal
                                create-catch-signal create-choose-signal
                                create-exception
                                raise-valid-handler raise-loop]]
            [purnam.native :refer [js-type]]
            [purnam.core])
  (:use-macros [purnam.test :only [fact]]
               [ribol.cljs :only [raise manage choose escalate fail default continue anticipate raise-on raise-on-all]]))

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
  (create-signal {:contents :a :msg "error"} :catch :other :stuff)
  => (ex-info "error :catch - :a"
       {:ribol.cljs/contents :a
        :ribol.cljs/signal :catch
        :other :stuff})
        
  (create-catch-signal :G__1 {:a 1 :b 2})
  => (ex-info "catch"
       {:ribol.cljs/target :G__1
        :ribol.cljs/signal :catch
        :ribol.cljs/value {:a 1 :b 2}})

  (create-choose-signal :G__1 :hello [1 2 3])
  => (ex-info "choose"
       {:ribol.cljs/target :G__1
        :ribol.cljs/signal :choose
        :ribol.cljs/label :hello
        :ribol.cljs/args [1 2 3]}))
        
(fact "raise-vaild-handler"
  (raise-valid-handler {:contents {:error true}}
                       [{:checker :error}])
  => {:checker :error})

(fact "raise-loop"
  (raise-loop {:contents {:error true :value 10}}
              [{:handlers [{:checker :error
                            :fn (fn [x] {:ribol.cljs/type :continue 
                                         :ribol.cljs/value x})}]}]
              {})
  => {:error true :value 10})

(fact "manage"
  (raise :error
    (option :zero [] 0)
    (default :zero)) => 0
    
  (manage
    (raise :error)
    (on :error [] 0)) => 0
    
  (manage
    [1 2 (raise :error)]
    (on :error [] 
      (continue 3))) => [1 2 3])
      
(fact "strategies"
  (manage                          ;; L2
    [1 2 (manage 3)])              ;; L1 and L0
  => [1 2 3]
  
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => [1 2 :A]
  
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => :B

  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3A]

  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3B]
  
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
          (raise :A                  ;; L0
                 (option :X [] :3X)) ;; X
          (on :A []                  ;; H1A
              (choose :X))
          (option :Y [] :3Y))]       ;; Y
     (option :Z [] :3Z))              ;; Z
  => [1 2 :3X]

  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :Z))
         (option :Y [] :3Y))]       ;; Y
   (option :Z [] :3Z))              ;; Z
  => :3Z

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (option :X [] :3X0)) ;; X0 - This is ignored
         (on :A []                   ;; H1A
           (choose :X))
         (option :X [] :3X1))]       ;; X1 - This is chosen
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X1]

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X))  ;; X
         (option :Y [] :3Y))]        ;; Y
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X]


  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X0)) ;; X0
         (option :X [] :3X1))]       ;; X1
    (option :X [] :3X2))             ;; X2
  => :3X2

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
            (escalate :B)))]
   (on :B []                         ;; H2B
       (continue :3B)))
  => [1 2 :3B]
  
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
             (escalate
              :B
              (option :X [] :3X))))] ;; X
    (on :B []                        ;; H2B
        (choose :X)))
  => [1 2 :3X]

  (try
   (manage                         ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                 ;; H1A
             (fail :B)))])
    (catch js/Error e
      (.-message e))) 
  => " - {:A true, :B true}"
      

  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                  ;; H1A
             (default)))]
   (on :A [] (continue 3)))
  => [1 2 :X]

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
           (option :X [] :X))        ;; X
         (on :A []                   ;; H1A
             (escalate
              :B
              (default :X))))]       ;; D1
    (on :B []                        ;; H2B
        (default)))
  => [1 2 :X]

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
  => [1 2 3 4 5 -6 -7 -8])
  
(fact
  (manage
    (try (throw (js-obj))
            (catch ExceptionInfo e#
              (ribol.cljs/raise [(ex-data e#) {:origin e#}]))
            (catch js/Object t#
              (ribol.cljs/raise [:divide-by-zero {:origin t#}])))
    (on :divide-by-zero [] 2))
  => 2
  
  (manage
    (raise-on [js/Object :divide-by-zero]
      (throw (js-obj)))
    (on :divide-by-zero [] 2))
  => 2
  
  (manage
      (raise-on-all :divide-by-zero
        (throw (js-obj)))
      (on :divide-by-zero [] 2))
  => 2
  
  (anticipate [js/Object :hello]
              (throw (js-obj)))
  => :hello
  )