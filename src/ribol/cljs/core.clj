(ns ribol.cljs.core)

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



(defn- is-special-form
  ([k form]
     (and (list? form)
          (symbol? (first form))
          (contains? (sp-forms k) (resolve (first form)))))
  ([k form syms]
     (if (list? form)
       (or (get syms (first form)) (is-special-form k form)))))

(defn- parse-option-forms [forms]
  (into {}
        (for [[type key & body] forms
              :when (= (resolve type) #'option)]
          [key `(fn ~@body)])))

(defn- parse-default-form [forms]
  (if-let [default (->> forms
                        (filter
                         (fn [[type]]
                           (= (resolve type) #'default)))
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
