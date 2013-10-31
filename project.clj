(defproject im.chit/ribol "0.3.3"
  :description "Conditional Restart Library for Clojure"
  :url "http://www.github.com/zcaudate/ribol"
  :license {:name "The MIT License"
            :url "http://http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}}
  :documentation {:files {"docs/index"
                          {:input "test/midje_doc/ribol_guide.clj"
                           :title "ribol"
                           :sub-title "conditional restarts for clojure"
                           :author "Chris Zheng"
                           :email  "z@caudate.me"
                           :tracking "UA-31320512-2"}}})
