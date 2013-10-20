(ns ribol.test-ribol-strategies
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Overview" :tag "overview" :numbered false}]]

"
Ribol provides a conditional restart system. It can be thought of as an issue resolution system or `try++/catch++`. The library provides a channel for resolving issues. We use the term `issues` to differentiate them from `exceptions`. `Issues` are `managed` whilst `exceptions` are `caught`
although they pretty much refer to abnormal program flow.

Restart systems are somewhat analogous to a management structure. A lower tier function will do work until they encounter an abnormal situation. An `issue` is `raised` to higher-tier processes that can `manage` the `issue` depending upon the type that is raised.

- An issue must have data about what it is. An issue can have additional information attached:
  - options that can be taken to resolve the issue
  - a default option if there is no management intervention.

- Issues are managed through `on` handlers within a `manage` block. The `issue` is passed to each handler and the handler checks that it is able to process the issue. There are six ways that a handler can deal with a raised issue:
  - directly (same as `try/catch`)
  - using `continue` to keep going with a given value
  - using `choose` to specify an option
  - using `escalate` to notify higher level managers
  - using `default` to allow the issue to resolve itself
  - using `fail` to throw an exception

Using these six different different issue resolution directives, the programmer has the richness of language beyond the simple `try/catch` statement at his/her command to be able to craft very complex process control flow strategies without mixing logic handling code in the middle tier. It can also create new ways of thinking about the problem beyond the standard throw/catch mechanism and offer more elegant ways to build programs.
"

[[{:numbered false}]]
(defn has-data [sigtype]
  (fn [ex]
    (-> ex ex-data (= sigtype))))

[[:chapter {:title "Standard Exception Flow"}]]

[[:section {:title "Normal Operation"}]]

(fact
  "The most straightforward code is one where no issues raised. This
   can be seen in [e.{{ribol-normal-eq}}](#ribol-normal-eq)."
   
  [[{:tag "ribol-normal-eq" :title "No Issues"}]]
  (manage                          ;; L2
    [1 2 (manage 3)])              ;; L1 and L0
  => [1 2 3]    ;; This is fun!
  
  [[:image {:src "ribol-normal.png" :width "100px" :title "Normal Flow Diagram"}]]
  
  "If there is an issue raised and
   there are no 'on' handlers, an ExceptionInfo
   object is thrown with issue as its data"
  
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise {:A true}))])      ;; L0
  => (throws clojure.lang.ExceptionInfo
             (has-data {:A true}))
             
  [[:image {:src "ribol-normal-ex.png"}]]

  "The issue can be given in the form of a
   keyword, which is a shortcut for `{<keyword> true}`"
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
        (raise :A))])              ;; L0
  => (throws clojure.lang.ExceptionInfo
             (has-data {:A true}))

  "The issue can be given in the form of a
   vector, which allows defining multiple
   tags within the issue"
  (manage                                 ;; L2
   [1 2 (manage                           ;; L1
         (raise [:A :B :C {:data 10}]))]) ;; L0
  => (throws clojure.lang.ExceptionInfo
             (has-data {:A true
                        :B true
                        :C true
                        :data 10})))

[[:section {:title "Manage Blocks"}]]

(fact
  "Once an issue has been raised, it can be
   handled within a managed scope through the use
   of 'on'. 'manage/on' is the equivalent to
   'try/catch' in the following two cases"
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
  => :B)

[[:section {:title "Issue checking with 'on'"}]]

(fact
  "An additional advantage using manage/on offers
   over try/catch is the fact that checkers can be
   used to do fine-grain flow control."

  "In the first case, the handler is checking that
   and that the :data is odd -> which it is and then
   returns a three"
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise [:B {:data 3}])    ;; L0
         (on {:data odd?}     ;; H1B
             []
             3))])
  => [1 2 3]

  "In the second case, the condition that :data is even
  does not satisfy and so this block throws an exception."

  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise [:B {:data 3}])    ;; L0
         (on {:data even?}     ;; H1B
             [] 3))])
  => (throws (has-data {:B true
                        :data 3})))

[[:section {:title "Accessing issue data"}]]
(fact
  "Data from a raised issue can be accessible
   by specifying the name of the key in the 'on'
   parameter section:"

  (manage                     ;; L2
   [1 2 (manage               ;; L1
         (raise {:data 3})    ;; L0
         (on {:data odd?}     ;; H1B
             [data]
             (inc data)))])
  => [1 2 4]

  "Multiple values of the issue can be used"

  (manage                     ;; L2
   [1 2 (manage               ;; L1
         (raise [:issue {:x 1 :y 2 :z 3}])    ;; L0
         (on :issue
             [x y z]
             (+ x y z)))])
  => [1 2 6])

[[:chapter {:title "Conditional Restart Forms"}]]
[[:section {:title "Continue"}]]

(fact
  "The 'continue' form signals that the program
   should resume at the point that the issue
   was raised."

  "In the first case, this gives the same result as try/catch."
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3A]

  "However, it can be seen that when 'continue' is used
   on the outer manage blocks, it provides the 'manage/on'
   a way for top tier forms to affect the bottom tier forms
   without manipulating logic in the middle tier"
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3B])


[[:section {:title "Choose and Option"}]]

(fact
  "'choose' and 'option' work together within manage
   scopes. A raised issue can have options attached to it,
   just a worker might give their manager certain options
   to choose from when an unexpected issue arises."
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :X)))])
  => [1 2 :3X]

  "An option can take in arguments and these are fed in when
   an option is chosen"
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise [:A {:data 3}]      ;; L0
                (option :X [a b c] (+ a b c)))  ;; X
         (on :A [data]              ;; H1A
             (choose :X
                     (dec data)
                     data
                     (inc data))))])
  => [1 2 9]

  "Options can be chosen that lie anywhere within the
   manage blocks. Most of the time, options are taken
   from the code  "
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :X))
         (option :Y [] :3Y))]       ;; Y
   (option :Z [] :3Z))              ;; Z
  => [1 2 :3X]

  "However in some cases, upper level options can be
   accessed as in this case. This can be used to set
   global strategies to deal with very issues that have
   serious consequences if it was to go ahead:

     ie: mine worker finds a gas-leak,
         he raises issue to manager,
         manager bypasses his manager and shuts down the plant."
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :Z))
         (option :Y [] :3Y))]       ;; Y
   (option :Z [] :3Z))              ;; Z
  => :3Z
  )

[[:section {:title "Choose - More Restart Strategies"}]]

(fact
  "If there are two options with the same label,
  choose will take the option specified at the highest
  management level. This means that managers at higher
  levels can over-ride lower level strategies."
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (option :X [] :3X0)) ;; X0 - This is ignored
         (on :A []                   ;; H1A
           (choose :X))
         (option :X [] :3X1))]       ;; X1 - This is chosen
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X1]

  "Specifying a 'default' option allows the raiser to
   have autonomous control of the situation if the issue
   remains unhandled."
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X))  ;; X
         (option :Y [] :3Y))]        ;; Y
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X]

  "This is an example of higher-tier managers
   overriding options"
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X0)) ;; X0
         (option :X [] :3X1))]       ;; X1
    (option :X [] :3X2))             ;; X2
  => :3X2)

[[:section {:title "Escalate"}]]
(fact
  "When issues are escalated, more information
   can be added and this then is passed on to
   higher-tier managers"
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
            (escalate :B)))]
   (on :B []                         ;; H2B
       (continue :3B)))
  => [1 2 :3B]

  "More options can be added to escalate. When
  these options are chosen, it will continue at the
  point in which the issue was raised."
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
             (escalate
              :B
              (option :X [] :3X))))] ;; X
    (on :B []                        ;; H2B
        (choose :X)))
  => [1 2 :3X])


[[:section {:title "Fail"}]]
(fact
  "Fail forces a failure."
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A []                 ;; H1A
             (fail :B)))])
  => (throws (has-data {:A true :B true}))

  "It is used where there is already a default
   option and the manager really needs it to fail."
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                 ;; H1A
             (fail :B)))])
  => (throws (has-data {:A true :B true})))


[[:section {:title "Default"}]]
(fact
  "Default short-circuits higher managers so that
   the issue is resolved internally."
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                  ;; H1A
             (default)))]
   (on :A [] (continue 3)))
  => [1 2 :X])

[[:section {:title "Combining Strategies"}]]
(fact
  "Ribol strategies can also be combined within the handler"
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

