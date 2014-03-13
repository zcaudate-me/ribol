(defproject im.chit/ribol "0.4.0"
  :description "Conditional Restart Library for Clojure"
  :url "http://www.github.com/zcaudate/ribol"
  :license {:name "The MIT License"
            :url "http://http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojurescript "0.0-2080"]
                                  [im.chit/purnam "0.3.0-SNAPSHOT"]
                                  [midje "1.6.0"]]
                    :plugins [[lein-midje "3.1.3"]
                              [lein-cljsbuild "1.0.0"]]}}
  :test-paths ["test/clj"]
  :documentation {:files {"docs/index"
                          {:input "test/clj/midje_doc/ribol_guide.clj"
                           :title "ribol"
                           :sub-title "conditional restarts for clojure"
                           :author "Chris Zheng"
                           :email  "z@caudate.me"
                           :tracking "UA-31320512-2"}}}
                           
  :cljsbuild {:builds [{:source-paths ["src" "test/cljs"],
                        :id "ribol-test",
                        :compiler {:pretty-print true,
                                   :output-to "harness/ribol-test.js",
                                   :optimizations :whitespace}}]})
