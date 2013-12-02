(use 'ribol.core)

(raise [:error {:input 1}])

(defn check-unlucky [n]
  (if (#{4 13 14 24 666} n)
    (raise [:unlucky-number {:value n}])
    n))

(check-unlucky 1) ;;=> 1



(mapv check-unlucky [1 2 3])
;;=> [1 2 3]


(mapv check-unlucky [1 2 3 4 5])

(manage
 (mapv check-unlucky [1 2 3 4 5])
 (on-any [value]
         (continue value)))


(macroexpand-1
 '(raise :error))

(clojure.core/let [issue__1710__auto__ (ribol.core/create-issue :error nil {} nil)]
  (ribol.core/raise-loop issue__1710__auto__ ribol.core/*managers*
                         (clojure.core/merge (:optmap issue__1710__auto__)
                                             ribol.core/*optmap*)))


(defmacro ex1 []
  [1 2 3])

(ex1) ;;=> [1 2 3]

(defn ex2 []
  [1 2 3])

(ex2) ;;=> [1 2 3]


(defn ex3 [n]
  (+ n 1))

(ex3 2) ;;=> 3

(defmacro ex4 [n]
  `(+ ~n 1))

(ex4 2) ;;=> 3

(restuarant
 :fastfood
 [:burger :fries :coke])

(+ 1 (+ 2 3))
(+ 1 5)
6

(defn order [orders]
  (println "Ordering: " orders))

(defn pay [orders]
  (println "Paying For: " orders))

(defn service [orders]
  (println "Servicing: " orders))

(defn total [orders]
  (println (count orders) "item"))

(defmacro restuarant [type orders]
  (cond (= type :fastfood)
        `(do
           (order ~orders)
           (pay (total ~orders))
           (service ~orders))

        (= type :yumcha)
        `(do
           (doseq [o# ~orders]
             (order [o#])
             (service [o#]))
           (pay (total ~orders)))))

order -> pay -> service

(restuarant
 :yumcha
 [:chicken-feet :dim-sim :spring-roll])


(restuarant
 :fastfood
 [:chicken-feet :dim-sim :spring-roll])

order ->  service -> order -> service -> order -> service -> pay

(macroexpand-1
 '(manage
   (mapv check-unlucky [1 2 3 4 5])
   (on-any [value]
           (continue value))))

(clojure.core/binding [ribol.core/*managers*
                       (clojure.core/cons {:id :G__5894,
                                           :handlers [{:checker (quote _),
                                                       :fn (clojure.core/fn [{:keys [value]}] (continue value))}],
                                           :options {}} ribol.core/*managers*)
                       ribol.core/*optmap* (clojure.core/merge {} ribol.core/*optmap*)]
  (try (mapv check-unlucky [1 2 3 4 5])
       (catch clojure.lang.ExceptionInfo ex
         (ribol.core/manage-signal {:id :G__5894, :handlers
                                    [{:checker (quote _), :fn (clojure.core/fn [{:keys [value]}] (continue value))}],
                                    :options {}} ex))))


(+ 1 (+ 1 2))
=> (+ 1 3)
=> 4

(try
 (mapv check-unlucky [1 2 3 4 5])
 (catch [value]
     "oeuoeu"))



00 whitespace " ,"
01 open "{ [ ("
10 open "} ] )"
11 any letter "abcdefg"
