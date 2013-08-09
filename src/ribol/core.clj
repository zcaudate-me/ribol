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
  [issue target f]
  (create-signal issue :catch ::target target ::fn f))

(defn- create-choose-signal
  [issue target label f]
  (create-signal issue :choose ::target target ::label label ::args-fn f))

(defn- create-default-signal
  [issue target label args]
  (create-signal issue :default ::target target ::label label ::args args))

(defn- create-unmanaged-signal
  [issue]
  (create-signal issue :unmanaged))

(defn- create-failure-signal
  [issue contents]
  (create-signal (update-in issue [:contents] merge contents) :failure))

(defn- raise-valid-handler [issue handlers]
  (if-let [h (first handlers)]
    (if (check-contents (:contents issue) (:checker h))
      h
      (recur issue (next handlers)))))

(defn- default-unhandled-fn [issue]
  (let [sig (create-unmanaged-signal issue)]
    (throw sig)))

(defn- raise-unhandled [issue optmap]
  (if-let [[label & args] (:default issue)]
    (let [target (get optmap label)]
      (cond (nil? target)
            (error "RAISE_UNHANDLED: the label " label
                   " has not been implemented")

            (= target (:id issue))
            (try
              (apply (-> issue :options label) args)
              (catch clojure.lang.ArityException e
                (error "RAISE_UNHANDLED: Wrong number of arguments to option key " label)))

            :else
            (throw (create-default-signal issue target label args))))
    (default-unhandled-fn issue)))

(declare raise-loop)

(defn- raise-escalate [issue handler managers optmap]
  (let [contents  (:contents issue)
        ncontents (parse-contents
                   ((:contents-fn handler) contents))
        noptions  ((:options-fn handler) contents)
        noptmap   (zipmap (keys noptions) (repeat (:id issue)))
        ndefault  ((:default-fn handler) contents)
        nissue (-> issue
                   (update-in [:contents] merge ncontents)
                   (update-in [:options] merge noptions)
                   (assoc-if :default ndefault))]
    (raise-loop nissue (next managers) (merge noptmap optmap))))

(defn- raise-continue [issue handler]
  ((:fn handler) (:contents issue)))

(defn- raise-fail [issue handler]
  (let [contents  (:contents issue)
        ncontents (parse-contents
                   ((:contents-fn handler) contents))
        sig (create-failure-signal issue ncontents)]
    (throw sig)))

(defn- raise-catch [issue handler manager]
  (let [sig (create-catch-signal issue (:id manager) (:fn handler))]
    (throw sig)))

(defn- raise-choose [issue handler manager optmap]
  (let [label (:label handler)
        contents (:contents issue)]
    (if-let [target (get optmap label)]
      (if (= target (:id issue))
        (apply (-> issue :options label) ((:args-fn handler) contents))
        (let [sig (create-choose-signal issue target
                                        label (:args-fn handler))]
          (throw sig)))
      (error "RAISE_CHOOSE: Cannot find " label " in options."))))

(defn raise-loop [issue managers optmap]
  (if-let [mgr (first managers)]
    (if-let [h (raise-valid-handler issue (:handlers mgr))]
      (let [ctns (:contents issue)]
        (condp = (:type h)
          :continue (raise-continue issue h)
          :escalate (raise-escalate issue h managers optmap)
          :catch (raise-catch issue h mgr)
          :choose (raise-choose issue h mgr optmap)
          :fail (raise-fail issue h)
          :default (raise-unhandled issue optmap)))
      (recur issue (next managers) optmap))
    (raise-unhandled issue optmap)))

(def #^{:doc "Special form to be used inside a 'manage' block.  When
  any issue is 'raised' from within the manage block, if that error satisfies
  the checker, then it will either do a 'catch' operation or process the contents
  of the issue with the following special forms: 'continue', 'escalate', 'choose' or 'default'"
        :arglists '([checker params special-form]
                    [checker params & body])}
  on)

(def #^{:doc "Special form to be used inside an 'on' block."
        :arglists '[& body]}
  continue)

(def #^{:doc "Special form to be used inside an 'on' block."
        :arglists '[contents & escalate-forms]}
  escalate)

(def #^{:doc "Special form to be used inside an 'on' block. Jumps to an option with
  the given label and the arguments to that option. It will choose the most top-level
  option if there are two with the same label."
        :arglists '[label & option-args]}
  choose)

(def #^{:doc "Special form to be used inside 'manage' 'raise' or 'escalate' blocks. It
  provides a label and a function body to be set up within a managed scope"
        :arglists '[label args & body]}
  option)

(def #^{:doc "It can be used within 'raise' and 'escalate' forms to override the
   default option of a raised issue. Or it can be used in 'on' forms to indicate
   that the default option of an issue is to be used."
        :arglists '("within on form:" [& args]
                    "within raise and escalate forms:" [label & args ])}
  default)

(def #^{:doc "Special form to be used inside 'on' blocks. It short-circuits higher level managers to throw a failure signal"
        :arglists '([] [contents])}
  fail)

(def sp-forms {:raise #{#'option #'default}
               :manage #{#'on #'option}
               :on #{#'continue #'escalate #'choose #'fail #'default}})

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
          (let [f (get (:options manager) (::label data))
                args ((::args-fn data) (::contents data))]
            (manage-apply f args (::label data)))

          (= :catch (::signal data))
          (let [f (::fn data)]
            (f (::contents data)))

          (= :default (::signal data))
          (let [f (get (:options manager) (::label data))
                args (:args data)]
            (manage-apply f args (::label data)))

          :else (throw ex))))

(defmacro manage-bind [manager optmap & body]
  `(binding [*managers* (cons ~manager *managers*)
             *optmap* (merge ~optmap *optmap*)]
    (try
      ~@body
      (catch clojure.lang.ExceptionInfo ~'ex
        (manage-signal ~manager ~'ex)))))

(defn- parse-on-type [fbody]
  (if (list? fbody)
      (condp = (resolve (first fbody))
        #'continue :continue
        #'escalate :escalate
        #'choose   :choose
        #'default  :default
        #'fail     :fail
        :catch)
      :catch))

(defn- parse-on-continue [params [_ & body]]
  {:fn `(fn [{:keys ~params}] ~@body)})

(defn- parse-on-choose [params [_ label & args]]
  {:label label
   :args-fn `(fn [{:keys ~params}]
               (vector ~@args))})

(defn- parse-on-escalate [params [_ contents & forms]]
  {:contents-fn `(fn [{:keys ~params}]
                   (or ~contents nil))
   :options-fn `(fn [{:keys ~params}]
                  ~(parse-option-forms forms))
   :default-fn `(fn [{:keys ~params}]
                  ~(parse-default-form forms))})

(defn- parse-on-fail [params [_ & [contents]]]
  {:contents-fn `(fn [{:keys ~params}]
                   (or ~contents {}))})

(defn- parse-on [chk params fbody rbody]
  (let [t (parse-on-type fbody)
        h {:type t :checker chk}]
    (if (not= t :catch)
      (assert (nil? rbody)))
    (case t
      :catch (assoc h :fn `(fn [{:keys ~params}] ~fbody ~@rbody))
      :escalate (merge h (parse-on-escalate params fbody))
      :choose  (merge h (parse-on-choose params fbody))
      :continue (merge h (parse-on-continue params fbody))
      :fail (merge h (parse-on-fail params fbody))
      :default h)))

(defn- parse-handler-forms [forms]
  (vec (for [[type chk params & [fbody & rbody]] forms
             :when (= (resolve type) #'on)]
         (parse-on chk params fbody rbody))))

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
        optmap (zipmap (keys options) (repeat id))
        manager {:id id :handlers handlers :options options}]
    `(manage-bind ~manager ~optmap ~@body-forms)))

(defn make-catch-forms [exceptions content sp-forms]
  (map (fn [ex]
         `(catch ~ex t#
            (raise ~content ~@sp-forms)))
       exceptions))

(defmacro raise-on
  [exceptions content form & forms]
  (let [exceptions
        (cond (symbol? exceptions) [exceptions]
              (vector? exceptions) exceptions
              :else (error "RAISE_ON: " exceptions
                           " can only be a classname or vector of classnames"))
        sp-fn #(is-special-form :raise %)
        forms (cons form forms)
        body-forms (filter (complement sp-fn) forms)
        sp-forms (filter sp-fn forms)
        catch-forms (make-catch-forms exceptions content sp-forms)]
    `(try ~@body-forms ~@catch-forms)))

(defmacro raise-on-all [content form & forms]
  `(raise-on Throwable ~content ~form ~@forms))
