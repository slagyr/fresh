(defproject fresh "1.1.0-SNAPSHOT"
  :description "A library to keep your clojure runtime 'Fresh'."
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:dev {:dependencies [[speclj "3.0.2"]]
                   :resource-paths ["sample_src"]}}
  :plugins [[speclj "3.0.2"]]
  :test-paths ["spec"])
