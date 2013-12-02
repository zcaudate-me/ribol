(ns midje-doc.ribol-implementation
  (:require [ribol.core :refer :all]
            [midje.sweet :refer :all]))

[[:chapter {:title "Implementation"}]]

[[:section {:title "The Workplace"}]]

"Two macros - `raise` and `manage` work together in creating the illusion of allowing code to seemingly jump around between higher and lower level functions. This in reality is not the case at all. We revisit the analogy of the worker who comes across something in their everyday routine that they cannot process."

[[:subsection {:title "The dumb throw"}]]
"
When `(throw .....)` is invoked, the worker basically says: *'Dude, I quit! You deal with it'* and lets higher up managers deal with the consequences.
"
[[:image {:title "Lazy Worker" :src "img/work_throw.png" :height "300px"}]]

[[:subsection {:title "The smart raise"}]]

"
When `(raise ....)` is used, the worker will attempt to notify their manager and ask for instructions. Only when there are no instructions forthcoming will they quit:
"

[[{:title "Continue Example"}]]
(fact
  (defn dislike-odd [n]
    (if (odd? n) (raise :error) n))

  (manage
   (mapv dislike-odd (range 10))
   (on :error [] (continue :odd)))
  => [0 :odd 2 :odd 4 :odd 6 :odd 8 :odd])

"Purely from looking at the example code, it reads:
  - perform `mapv` of `dislike-odd` over the range of 0 to 10
  - if `:error` is raised by `dislike-odd`, tell `dislike-odd` to proceed at the point that the error was raised, using the value `:odd` instead.
"

[[:image {:title "Smart Worker" :src "img/work_raise.png" :height "300px"}]]

"
Anytime there is an odd input to `dislike-odd`, the code seemingly jumps out to the context of the manager and having handled the issue, the code then seemingly jumps back into the function again.

Note the words `seemingly`. This is how we as programmers should be thinking about the problem as we write code to handle these type of issues. We are tricked for our own good because it makes us able to better reason about our programs without having to deal with the implementation details.

However, we are naturally suspicious of this from a performance point of view. If we don't know the mechanism, we ask ourselves... won`t all this jumping around make our code slower?
"

[[:subsection {:title "The proactive workplace"}]]

"
In reality, the program never left the place where `raise` was called. There was no saving of the stack or anything fancy. The `raise/continue` combination was an *illusion* of a jump. There was no jump. Calling `raise/continue` is most likely computationally cheaper than `try/catch`.

Going back to the workplace analogy, another way to manage exceptional circumstances is to have a prearranged noticeboard of what to do when things go wrong. Managers can write/override different ways to handle an issue on this board proactively. The worker, when encountering an issue, would go look at the board first to decide upon the best course of action to take. Only when there are no matching solutions to the issue will they solve it themselves or give up and quit. In this way, managers will not have to be called everytime something came up. This is the same mechanism of control that `ribol` uses.
"
[[:image {:title "Proactive Management" :src "img/work_board.png" :height "300px"}]]


[[:section {:title "The Issue Management Board"}]]

"We look at what happens when there is such an Issue Management Board put in place. `raise` is called. The worker will look at the board, starting with lowest level manager and proceeding up the management chain to see what strategies has to be been put into place. In the case of [e.{{continue-example}}](#continue-example), there would have been a handler already registered on the board to deal with the `:error`. The worker will pass any arguments of the issue to the handler function and then return with the result.
"

[[:image {:src "img/notice_board.png" :height "300px"}]]

"
The management does not even need to know that an exception has occured because they have been proactive.
"

[[:subsection {:title "Control Flow as Data"}]]

"Whilst the `raise/continue` mechanism was decribed in brief, a bit more explanation is required to understand how different forms of jumps occur. Ribol implements the 5 special forms as data-structures:"

[[{:numbered false}]]
[[:code
  "
  (defmacro continue [& body]
    `{::type :continue ::value (do ~@body)})

  (defmacro default [& args]
    `{::type :default ::args (list ~@args)})

  (defmacro choose [label & args]
    `{::type :choose ::label ~label ::args (list ~@args)})

  (defmacro fail
    ([] {::type :fail})
    ([contents]
       `{::type :fail ::contents ~contents}))
"]]

"`escalate` is not shown because it a bit more complex as options and defaults can. Essentially, it is still just a data structure.

The `raise` macro calls `raise-loop` which looks at the `::type` signature of the result returned by the `on` handler."

[[{:numbered false}]]
[[:code
  "
  (defn raise-loop [issue managers optmap]
    (... code ...
         (condp = (::type res)
           :continue (::value res)
           :choose (raise-choose issue (::label res) (::args res) optmap)
           :default (raise-unhandled issue optmap)
           :fail (raise-fail issue (::contents res))
           :escalate (raise-escalate issue res managers optmap)
           (raise-catch mgr res)))

    ... code ...)"]]

"In the case of `:continue`, it can be seen that the function just returns `(::value res)`. The function in which `raise` was called proceeds without ever jumping anywhere.

In the case of other forms, there are different handlers to handle each case. If the `on` handler returns a non-special form value, it will call `raise-catch`. So it is possible to mess with the internals of `ribol` by creating a datastructure of the same format of the special forms. However, please do it for shits and giggles only as I'm not sure what it would do to your program.
"

[[:subsection {:title "Implementing Catch"}]]

"To understand how `catch` is implemented, we have to look at the `manage` macro:"

[[{:numbered false}]]
(comment
  (defmacro manage
    ... code ...

    `(binding [*managers* (cons ~manager *managers*)
               *optmap* (merge ~optmap *optmap*)]
       (try
         ~@body-forms
         (catch clojure.lang.ExceptionInfo ~'ex
           (manage-signal ~manager ~'ex))
         ~@finally-forms))))

"So essentially, it is a `try/catch` block wrapped in an `binding` form."

"When `raise-loop` gets a non-special form value back from a function handler in the manager it will call `raise-catch`, which will create a `catch` signal and actually throw it. The signal is just a clojure.lang.ExceptionInfo. The signal has a `::target`, which is the `:id` of the manager. It also has `::value`, which is the original result from `on`."

[[{:numbered false}]]
[[:code
  "
  (defn- raise-catch [manager value]
    (throw (create-catch-signal (:id manager) value)))

  (defn- create-catch-signal
    [target value]
    (ex-info \"catch\" {::signal :catch ::target target ::value value}))"]]

"Going back to the `manage` block, it can be seen that `manage` will catch any `clojure.lang.ExceptionInfo` objects thrown. When a signal is thrown from lower functions, it will be caught and `manage-signal` is then called. If the target does not match the :id, then the exception is rethrown. If the exception has `::signal` of `:catch` then the manager will return `(::value data)`."

[[{:numbered false}]]
[[:code
  "(defn manage-signal [manager ex]
    (let [data (ex-data ex)]
      (cond (not= (:id manager) (::target data))
            (throw ex)

            ... choose code ....

            (= :catch (::signal data))
            (::value data)

            :else (throw ex))))"]]

[[:subsection {:title "Implementing Choose"}]]

"Choose works with options. It can be seen that apart from the `*managers*` structure, there is also an `*optmap*`. The optmap holds as the key/value pairs the labels of what options are registered and the id of the manager that provided the option.

Choose also requires that a signal be sent, but the target will now be a lookup on the optmap given an option label. The signal is very similar to the `catch` signal.
"
[[{:numbered false}]]
[[:code
  "(defn- create-choose-signal
    [target label args]
     (ex-info \"choose\" {::signal :choose ::target target ::label label ::args args}))"]]

"The part that processes `:choose` is shown in `manage-signal`:"

[[{:numbered false}]]
[[:code
  "
 (defn manage-signal
  ...
       (= :choose (::signal data))
       (let [label (::label data)
             f (get (:options manager) label)
             args (::args data)]
         (manage-apply f args label))
  ...)"]]

[[:subsection {:title "Implementing the Rest"}]]

"`fail`, `default`, `escalate` all use similar *data as control-flow* mechanisms to allow control to be directed to the correct part of the program. It is through this mechanism that branching in the `on` handlers can be achieved."
