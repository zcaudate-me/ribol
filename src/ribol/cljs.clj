(ns ribol.cljs)

(defmacro error
  ([e] `(throw (js/Error. (str ~e))))
  ([e & more]
     `(throw (js/Error. (str ~e ~@more)))))

(def on-any)

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

(def sp-forms {:anticipate #{'catch 'finally}
               :raise #{'option 'default 'catch 'finally}
               :raise-on #{'option 'default 'catch 'finally}
               :manage #{'on 'on-any 'option}})

(defn- is-special-form
  ([k form]
     (and (list? form)
          (symbol? (first form))
          (contains? (sp-forms k) (first form))))
  ([k form syms]
     (if (list? form)
       (or (get syms (first form)) (is-special-form k form)))))

(defn- parse-option-forms [forms]
  (into {}
        (for [[type key & body] forms
              :when (= type 'option)]
          [key `(fn ~@body)])))

(defn- parse-default-form [forms]
  (if-let [default (->> forms
                        (filter
                         (fn [[type]]
                           (= type 'default)))
                        (last)
                        (next))]
    (vec default)))

(defmacro escalate [contents & forms]
  (let [[contents forms]
        (if (is-special-form :raise contents)
          [nil (cons contents forms)]
          [contents forms])]
    `{::type :escalate
      ::contents ~contents
      ::options ~(parse-option-forms forms)
      ::default ~(parse-default-form forms)}))


#_(defmacro raise
  "Raise an issue with the content to be either a keyword, hashmap or vector, optional message
  and raise-forms - 'option' and 'default'"
  [content & [msg & forms]]
  (let [[msg forms] (if (is-special-form :raise msg)
                      ["" (cons msg forms)]
                      [msg forms])
        options (parse-option-forms forms)
        default (parse-default-form forms)]
    `(let [issue# (create-issue ~content ~msg ~options ~default)]
       (raise-loop issue#  *managers*
                   (merge (:optmap issue#) *optmap*)))))
