(defproject im.chit/ribol "0.4.0-SNAPSHOT"
  :description "Conditional Restart Library for Clojure"
  :url "http://www.github.com/zcaudate/ribol"
  :license {:name "The MIT License"
            :url "http://http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [im.chit/purnam "0.3.0-SNAPSHOT"]]}}
  :test-paths ["test/clj"]
  :documentation {:files {"docs/index"
                          {:input "test/midje_doc/ribol_guide.clj"
                           :title "ribol"
                           :sub-title "conditional restarts for clojure"
                           :author "Chris Zheng"
                           :email  "z@caudate.me"
                           :tracking "UA-31320512-2"}}}
                           
  :cljsbuild {:builds [{:source-paths ["src/cljs" "test/cljs"],
                        :id "ribol-test",
                        :compiler {:pretty-print true,
                                   :output-to "harness/ribol-test.js",
                                   :optimizations :whitespace}}]})
