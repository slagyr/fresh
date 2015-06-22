(defproject fresh "1.1.2"
            :description "A library to keep your clojure runtime 'Fresh'."
            :dependencies [[org.clojure/clojure "1.7.0-RC2"]]
            :profiles {:dev {:dependencies [[speclj "3.2.0"]]
                             :resource-paths ["sample_src"]}}
            :plugins [[speclj "3.2.0"]]
            :source-paths ["src"]
            :test-paths ["spec"])
