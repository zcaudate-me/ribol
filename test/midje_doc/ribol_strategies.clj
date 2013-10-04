(ns midje-doc.ribol-strategies
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]
            [midje-doc.ribol-api :refer [raises-issue]]))

[[:chapter {:title "Control Strategies"}]]

"This is a comprehensive (though non-exhaustive) list of program control strategies that can be used with `ribol`. It can be noted that the `try/catch` paradigm can implement sections [{{normal}}](#normal) and [{{catch}}](#catch). Other clojure restart libraries such as `errorkit`, `swell` and `conditions` additionally implement sections [{{continue}}](#continue), [{{choose}}](#choose) and [{{choose-more}}](#choose-more).

`ribol` supports novel (and more natural) program control mechanics through the `escalate` ([{{escalate}}](#escalate)), `fail` ([{{fail}}](#fail)) and  `default` ([{{default}}](#default)) special forms as well as branching support in the `on` special form ([{{on-form}}](#on-form))."

[[:section {:title "Normal"}]]
(facts
  [[:subsection {:title "No Raise"}]]
  "The most straightforward code is one where no issues raised:"

  [[{:tag "ribol-normal-eq" :title "No Issues"}]]
  (manage                          ;; L2
    [1 2 (manage 3)])              ;; L1 and L0
  => [1 2 3]

  [[:image {:src "img/norm_normal.png" :height "300px" :title "No Issues Flow"}]]

  [[:subsection {:title "Issue Raised"}]]

  "If there is an issue raised with no handler, it will `throw` an exception."

  [[{:title "Unmanaged Issue"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise {:A true}))])      ;; L0
  => (raises-issue {:A true})

  [[:image {:src "img/norm_unmanaged.png" :height "300px" :title "Unmanaged Issue Flow"}]])


[[:section {:title "Catch"}]]

(facts
  "Once an issue has been raised, it can be handled within a managed scope through the use of 'on'. 'manage/on' is the equivalent to 'try/catch' in the following two cases:"

  [[:subsection {:title "First Level Catch"}]]
  [[{:title "Catch on :A"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => [1 2 :A]
  [[:image {:src "img/catch_flow_a.png" :height "300px" :title "Catch on :A Flow"}]]

  [[:subsection {:title "Second Level Catch"}]]
  [[{:title "Catch on :B"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A [] :A))]           ;; H1A
   (on :B [] :B))                  ;; H2B
  => :B

  [[:image {:src "img/catch_flow_b.png" :height "300px" :title "Catch on :B Flow"}]]
  )


[[:section {:title "Continue"}]]

(facts
  "The 'continue' form signals that the program
   should resume at the point that the issue
   was raised."

  [[:subsection {:title "First Level Continue"}]]
  "In the first case, this gives the same result as `try/catch`."

  [[{:title "Continue on :A"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3A]
    [[:image {:src "img/continue_flow_a.png" :height "300px" :title "Continue on :A Flow"}]]


  [[:subsection {:title "Second Level Continue"}]]

  "However, it can be seen that when 'continue' is used
   on the outer manage blocks, it provides the 'manage/on'
   a way for top tier forms to affect the bottom tier forms
   without manipulating logic in the middle tier"
  [[{:title "Continue on :B"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :B)                ;; L0
         (on :A []                 ;; H1A
             (continue :3A)))]
   (on :B []                       ;; H2B
       (continue :3B)))
  => [1 2 :3B]

  [[:image {:src "img/continue_flow_b.png" :height "300px" :title "Continue on :B Flow"}]]
)


[[:section {:title "Choose"}]]

(facts


  "`choose` and `option` work together within manage scopes. A raised issue can have options attached to it, just a worker might give their manager certain options to choose from when an unexpected issue arises. Options can be chosen that lie anywhere within the manage blocks."

  [[:subsection {:title "Choose Lower-Level"}]]

  [[{:title "Choose :X"}]]
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
          (raise :A                  ;; L0
                 (option :X [] :3X)) ;; X
          (on :A []                  ;; H1A
              (choose :X))
          (option :Y [] :3Y))]       ;; Y
     (option :Z [] :3Z))              ;; Z
  => [1 2 :3X]

    [[:image {:src "img/choose_flow_x.png" :height "300px" :title "Choose :X Flow"}]]

  "However in some cases, upper level options can be accessed as in this case. This can be used to set global strategies to deal with very issues that have serious consequences if it was to go ahead.

   An example maybe a mine worker who finds a gas-leak. Because of previously conveyed instructions, he doesn't need to inform his manager and shuts down the plant immediately."

  [[:subsection {:title "Choose Upper-Level"}]]


  [[{:title "Choose :Z"}]]
  (manage                           ;; L2
   [1 2 (manage                     ;; L1
         (raise :A                  ;; L0
                (option :X [] :3X)) ;; X
         (on :A []                  ;; H1A
             (choose :Z))
         (option :Y [] :3Y))]       ;; Y
   (option :Z [] :3Z))              ;; Z
  => :3Z

  [[:image {:src "img/choose_flow_z.png" :height "300px" :title "Choose :Z Flow"}]]

  )


[[:section {:title "Choose - More Strategies" :tag "choose-more"}]]
(facts

  [[:subsection {:title "Overridding An Option"}]]
  "If there are two options with the same label,
  choose will take the option specified at the highest
  management level. This means that managers at higher
  levels can over-ride lower level strategies."

  [[{:title "Choose :X1"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (option :X [] :3X0)) ;; X0 - This is ignored
         (on :A []                   ;; H1A
           (choose :X))
         (option :X [] :3X1))]       ;; X1 - This is chosen
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X1]

  [[:image {:src "img/choose_flow_x1.png" :height "300px" :title "Choose :X1 Flow"}]]


  [[:subsection {:title "Default Option"}]]
  "Specifying a 'default' option allows the raiser to
   have autonomous control of the situation if the issue
   remains unhandled."
  [[{:title "Choose Default"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X))  ;; X
         (option :Y [] :3Y))]        ;; Y
   (option :Z [] :3Z))               ;; Z
  => [1 2 :3X]

  [[:image {:src "img/choose_default_x.png" :height "300px" :title "Choose Default Flow"}]]

  [[:subsection {:title "Overriding Defaults"}]]

  "This is an example of higher-tier managers
   overriding options"
   [[{:title "Choose Default :X2"}]]

  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A                   ;; L0
                (default :X)         ;; D
                (option :X [] :3X0)) ;; X0
         (option :X [] :3X1))]       ;; X1
    (option :X [] :3X2))             ;; X2
  => :3X2

  [[:image {:src "img/choose_default_x2.png" :height "300px" :title "Choose Default :X2 Flow"}]]
  )


[[:section {:title "Escalate"}]]

(facts
  [[:subsection {:title "Simple Escalation"}]]

  "When issues are escalated, more information
   can be added and this then is passed on to
   higher-tier managers"
  [[{:title "Escalate :B"}]]
  (manage                            ;; L2
   [1 2 (manage                      ;; L1
         (raise :A)                  ;; L0
         (on :A []                   ;; H1A
            (escalate :B)))]
   (on :B []                         ;; H2B
       (continue :3B)))
  => [1 2 :3B]

    [[:image {:src "img/escalate_norm.png" :height "300px" :title "Escalate :B Flow"}]]

  [[:subsection {:title "Escalation with Options"}]]
  "More options can be added to escalate. When
  these options are chosen, it will continue at the
  point in which the issue was raised."
  [[{:title "Escalate :B, Choose :X"}]]
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

  [[:image {:src "img/escalate_options.png" :height "300px" :title "Escalate :B, Choose :X Flow"}]]
    )


[[:section {:title "Fail"}]]
(facts
  "Fail forces a failure. It is used where there is already a default
   option and the manager really needs it to fail."

  [[{:title "Force Failure"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                 ;; H1A
             (fail :B)))])
  => (raises-issue {:A true :B true})
  [[:image {:src "img/fail.png" :height "300px" :title "Force Fail Flow"}]]
)


[[:section {:title "Default"}]]
(facts
  "Default short-circuits higher managers so that
   the issue is resolved internally."

    [[{:title "Force Default"}]]
  (manage                          ;; L2
   [1 2 (manage                    ;; L1
         (raise :A                 ;; L0
                (option :X [] :X)
                (default :X))
         (on :A []                  ;; H1A
             (default)))]
   (on :A [] (continue 3)))
  => [1 2 :X]


  [[:subsection {:title "Escalation with Defaults"}]]
  "This is `default` in combination with `escalate` to do some very complex jumping around."

  [[{:title "Escalate :B, Choose Default"}]]
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

  [[:image {:src "img/escalate_default_x2.png" :height "300px" :title "Escalate :B, Choose Default Flow"}]])

[[:section {:title "Branch Using On" :tag "on-form"}]]
(facts
  "Ribol strategies can also be combined within the `on` handler. In the following example, it can be seen that the `on :error` handler supports both `escalate` and `continue` strategies."

  [[{:title "Input Dependent Branching"}]]
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
  => [1 2 3 4 5 -6 -7 -8]

  "Using branching strategies with `on` much more complex interactions can be constructed beyond the scope of this document."
  )
