(ns ribol.core)

(def ^:dynamic *managers* [])
(def ^:dynamic *optmap* {})

(defmacro error
  ([e] `(throw (Exception. (str ~e))))
  ([e & more]
     `(throw (Exception. (str ~e ~@more)))))

(defn- hash-map?
 [x] (instance? clojure.lang.IPersistentMap x))

(defn- hash-set?
 [x] (instance? clojure.lang.PersistentHashSet x))

(defn- assoc-if
 ([m k v]
   (if (nil? v) m
      (assoc m k v)))
 ([m k v & more]
   (apply assoc-if (assoc-if m k v) more)))

(defn- parse-contents [contents]
  (cond (hash-map? contents) contents
        (keyword? contents) {contents true}
        (vector? contents)  (apply merge (map parse-contents contents))
        :else (error "PARSE_CONTENTS: " contents " should be a keyword, hash-map or vector")))

(defn- check-contents [contents chk]
  (cond (hash-map? chk)
        (every? (fn [[k vchk]]
                  (let [vcnt (get contents k)]
                    (cond (keyword? vchk) (= vchk vcnt)
                          (fn? vchk) (vchk vcnt)
                          :else (= vchk vcnt))))
                chk)

        (vector? chk)
        (every? #(check-contents contents %) chk)

        (or (fn? chk) (keyword? chk) (hash-set? chk))
        (chk contents)

        :else (error "CHECK_CONTENTS: " chk " cannot be founde")))

(defn create-issue
  [contents msg options default]
  (let [contents (parse-contents contents)
        id (keyword (gensym))
        options (or options {})
        optmap (zipmap (keys options) (repeat id))]
    {:id id
     :contents contents
     :msg msg
     :options options
     :optmap optmap
     :default default}))

(defn- create-signal
  [issue signal & args]
  (let [contents (:contents issue)
        data (apply assoc {::contents contents} ::signal signal args)
        msg  (str (:msg issue) " " signal " - " contents)]
    (ex-info msg data)))

(defn- create-catch-signal
  [target value]
  (ex-info "catch" {::signal :catch ::target target ::value value}))

(defn- create-choose-signal
  [target label args]
  (ex-info "choose" {::signal :choose ::target target ::label label ::args args}))

(defn- create-exception
  ([issue]
     (let [contents (:contents issue)
           msg (str (:msg issue) " - " contents)]
       (ex-info msg contents)))
  ([issue contents]
     (create-exception (update-in issue [:contents] merge contents))))

(defn- raise-valid-handler [issue handlers]
  (if-let [h (first handlers)]
    (if (check-contents (:contents issue) (:checker h))
      h
      (recur issue (next handlers)))))

(defn- default-unhandled-fn [issue]
  (let [ex (create-exception issue)]
    (throw ex)))

(declare raise-loop)

(defn- raise-catch [manager value]
  (throw (create-catch-signal (:id manager) value)))

(defn- raise-choose [issue label args optmap]
  (let [target (get optmap label)]
    (cond (nil? target)
          (error "RAISE_CHOOSE: the label " label
                 " has not been implemented")

          (= target (:id issue))
          (try
            (apply (-> issue :options label) args)
            (catch clojure.lang.ArityException e
              (error "RAISE_CHOOSE: Wrong number of arguments to option key " label)))

          :else
          (throw (create-choose-signal target label args)))))

(defn- raise-unhandled [issue optmap]
  (if-let [[label & args] (:default issue)]
    (raise-choose issue label args optmap)
    (default-unhandled-fn issue)))

(defn- raise-fail [issue contents]
  (throw (create-exception issue (parse-contents contents))))

(defn- raise-escalate [issue res managers optmap]
  (let [ncontents (parse-contents (::contents res))
        noptions  (::options res)
        noptmap   (zipmap (keys noptions) (repeat (:id issue)))
        ndefault  (::default res)
        nissue (-> issue
                   (update-in [:contents] merge ncontents)
                   (update-in [:options] merge noptions)
                   (assoc-if :default ndefault))]
    (raise-loop nissue (next managers) (merge noptmap optmap))))

(defn raise-loop [issue managers optmap]
  (if-let [mgr (first managers)]
    (if-let [h (raise-valid-handler issue (:handlers mgr))]
      (let [ctns (:contents issue)
            res  ((:fn h) ctns)]
        (condp = (::type res)
          :continue (::value res)
          :choose (raise-choose issue (::label res) (::args res) optmap)
          :default (raise-unhandled issue optmap)
          :fail (raise-fail issue (::contents res))
          :escalate (raise-escalate issue res managers optmap)
          (raise-catch mgr res)))
      (recur issue (next managers) optmap))
    (raise-unhandled issue optmap)))


(def #^{:doc "Special form to be used inside a 'manage' block.  When
  any issue is 'raised' from within the manage block, if that error satisfies
  the checker, then it will either do a 'catch' operation or process the contents
  of the issue with the following special forms: 'continue', 'escalate', 'choose' or 'default'"
        :arglists '([checker params special-form]
                    [checker params & body])}
  on)

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

(def #^{:doc "Special form to be used inside 'manage' 'raise' or 'escalate' blocks. It
  provides a label and a function body to be set up within a managed scope."
        :arglists '[label args & body]}
  option)

(def #^{:doc "Special form to be used inside 'manange', 'raise-on', 'raise-on-all' and 'anticipate' blocks."
        :arglists '[label args & body]}
  finally)

(def sp-forms {:anticipate #{#'finally}
               :raise #{#'option #'default}
               :raise-on #{#'option #'default #'finally}
               :manage #{#'on #'option #'finally}})

(defn- is-special-form [k form]
  (and (list? form)
       (symbol? (first form))
       (contains? (sp-forms k) (resolve (first form)))))

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

(defmacro raise
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

(defn- manage-apply [f args label]
  (try
    (apply f args)
    (catch clojure.lang.ArityException e
      (error "MANAGE-APPLY: Wrong number of arguments to option key: " label))))

(defn manage-signal [manager ex]
  (let [data (ex-data ex)]
    (cond (not= (:id manager) (::target data))
          (throw ex)

          (= :choose (::signal data))
          (let [label (::label data)
                f (get (:options manager) label)
                args (::args data)]
            (manage-apply f args label))

          (= :catch (::signal data))
          (::value data)

          :else (throw ex))))

(defn- parse-on [chk params body]
  {:checker chk
   :fn `(fn [{:keys ~params}] ~@body)})

(defn- parse-handler-forms [forms]
  (vec (for [[type chk params & body] forms
             :when (= (resolve type) #'on)]
         (parse-on chk params body))))

(defn- parse-finally-forms [forms]
  (for [[type & body :as fform] forms
        :when (= (resolve type) #'finally)]
    (cons 'finally body)))

(defmacro manage
  "This creats the 'manage' dynamic scope form. The body will be executed
  in a dynamic context that allows handling of issues with 'on' and 'option' forms."
  [& forms]
  (let [sp-fn #(is-special-form :manage %)
        body-forms (vec (filter (complement sp-fn) forms))
        sp-forms (filter sp-fn forms)
        id (keyword (gensym))
        options  (parse-option-forms sp-forms)
        handlers (parse-handler-forms sp-forms)
        finally-forms (parse-finally-forms sp-forms)
        optmap (zipmap (keys options) (repeat id))
        manager {:id id :handlers handlers :options options}]
    `(binding [*managers* (cons ~manager *managers*)
               *optmap* (merge ~optmap *optmap*)]
       (try
         ~@body-forms
         (catch clojure.lang.ExceptionInfo ~'ex
           (manage-signal ~manager ~'ex))
         ~@finally-forms))))

(defn- make-catch-forms [exceptions sp-forms]
  (cons
   `(catch clojure.lang.ExceptionInfo e#
      (raise (ex-data e#) ~@sp-forms))
   (map (fn [ex]
          `(catch ~(:type ex) t#
             (raise ~(:content ex) ~@sp-forms)))
        exceptions)))

(defn- make-catch-elem [[ex content]]
  (cond (symbol? ex) [{:type ex :content content}]
        (vector? ex) (map (fn [t] {:type t :content content})
                                  ex)
        :else (error "RAISE_ON: " ex
                     " can only be a classname or vector of classnames")))

(defn- make-catch-list [bindings]
  (mapcat make-catch-elem (partition 2 bindings)))

(defmacro raise-on
  "Raises an issue with options and defaults when an exception is encountered
  when the body has been evaluated"
  [bindings form & forms]
  (let [exceptions (make-catch-list bindings)
        raise-on-fn #(is-special-form :raise-on %)
        raise-fn    #(is-special-form :raise %)
        forms (cons form forms)
        body-forms (filter (complement raise-on-fn) forms)
        raise-on-forms (filter raise-on-fn forms)
        finally-forms (filter (complement raise-fn) raise-on-forms)
        raise-forms (filter raise-fn raise-on-forms)
        catch-forms (make-catch-forms exceptions raise-forms)]
    `(try ~@body-forms ~@catch-forms ~@finally-forms)))

(defmacro raise-on-all [content form & forms]
  "Raises an issue with options and defaults when any exception is encountered
  as the body is being evaluated"
  `(raise-on [Throwable ~content] ~form ~@forms))

(defn- parse-anticipate-pair [[extype res]]
  (cond (hash-set? extype)
        (mapcat #(parse-anticipate-pair [% res]) extype)

        (symbol? extype)
        `((catch ~extype t# ~res))

        (or (keyword? extype) (hash-map? extype) (vector? extype))
        `((catch clojure.lang.ExceptionInfo t#
            (if (check-contents (ex-data t#)
                                ~extype)
              ~res
              (throw t#))))))

(defmacro anticipate [exvec & body]
  "Anticipates exceptions and decides what to do with them"
  (let [pairs (partition 2 exvec)
        anticipate-fn    #(is-special-form :anticipate %)
        body-forms (filter (complement anticipate-fn) body)
        finally-forms (filter anticipate-fn body)
        catches (mapcat parse-anticipate-pair pairs)]
    `(try ~@body-forms
          ~@catches
          ~@finally-forms)))
