(ns fresh.core-spec
  (:use
    [speclj.core]
    [fresh.core]
    [clojure.java.io :only (file copy make-input-stream delete-file make-parents)]))

(def sample-dir (.getCanonicalFile (file "sample_src")))

(defn clean-sample-files []
  (let [all (remove #(= sample-dir %) (file-seq sample-dir))
        files (filter #(.isFile %) all)
        dirs (filter #(.isDirectory %) all)
        dirs (reverse (sort #(compare (.length (.getPath %1)) (.length (.getPath %2))) dirs))]
    (doseq [file files] (delete-file file))
    (doseq [dir dirs] (delete-file dir))))

(defn sample-file [name]
  (file sample-dir name))

(defn write-file [name content]
  (let [file (sample-file name)]
    (make-parents file)
    (copy (make-input-stream (.getBytes content) {}) file)
    file))

(defn establish-sample-files []
  (clean-sample-files)
  (write-file "sample/core.clj" "(ns sample.core) (def sample-core :sample-core)")
  (write-file "sample/a/one.clj" "(ns sample.a.one (:use [sample.core])) (def sample-a-one :sample-a-one)")
  (write-file "sample/b/one.clj" "; comment\n(ns sample.b.one (:use [sample.core])) (def sample-b-one :sample-b-one)"))

(defn tweak-mod-time [file tweak]
  (let [mod-time (+ (.lastModified file) (* 1000 tweak))]
    (.setLastModified file mod-time)))

(describe "Fresh"

  (it "converts ns names into filenames"
    (should= "foo.clj" (ns-to-filename "foo"))
    (should= "bar.clj" (ns-to-filename "bar"))
    (should= "foo/bar.clj" (ns-to-filename "foo.bar"))
    (should= "foo_bar.clj" (ns-to-filename "foo-bar"))
    (should= "foo_bar/fizz_bang.clj" (ns-to-filename "foo-bar.fizz-bang")))

  (it "recognizes ns forms"
    (should= true (ns-form? '(ns blah)))
    (should= true (ns-form? '(ns foo.bar (:use [sample.core]))))
    (should= false (ns-form? '(not-ns blah)))
    (should= false (ns-form? [])))

  (it "pulls ns name from ns form"
    (should= 'foo (ns-name-from '(ns foo)))
    (should= 'foo.bar (ns-name-from '(ns foo.bar))))

  (it "pulls dependencies out of ns form"
    (should= '#{blah} (depending-ns-names-from '(ns foo (:use [blah]))))
    (should= '#{bar} (depending-ns-names-from '(ns foo (:use [bar]))))
    (should= '#{fizz} (depending-ns-names-from '(ns foo (:use fizz))))
    (should= '#{fizz} (depending-ns-names-from '(ns foo (:require fizz))))
    (should= '#{one two three} (depending-ns-names-from '(ns foo (:use [one] [two] [three]))))
    (should= '#{one two three} (depending-ns-names-from '(ns foo (:require [one] [two] [three]))))
    (should= '#{root.one root.two} (depending-ns-names-from '(ns foo (:use [root [one] [two]]))))
    (should= '#{root.one root.two} (depending-ns-names-from '(ns foo (:require [root [one] [two]]))))
    (should= '#{one two} (depending-ns-names-from '(ns foo (:use [one :only (foo)] [two :exclude (bar)]))))
    (should= '#{one two} (depending-ns-names-from '(ns foo (:require [one :as o] [two :as t]))))
    (should= '#{one.two one.three} (depending-ns-names-from '(ns foo (:use [one [two :only (foo)] [three :exclude (bar)]]))))
    (should= '#{one.two one.three} (depending-ns-names-from '(ns foo (:require [one [two :as t] [three :as tr]]))))
    (should= '#{root.one.child.grandchild root.two} (depending-ns-names-from '(ns foo (:use [root [one [child [grandchild]]] [two]]))))
    (should= '#{fizz} (depending-ns-names-from '(ns foo (:require [fizz] :reload))))
    (should= '#{fizz} (depending-ns-names-from '(ns foo (:use [fizz] :verbose)))))

  (context "using files"

    (before (clean-sample-files))

    (it "reads no ns form from src files that don't contain them"
      (should= nil (read-ns-form (write-file "test/one.clj" "()")))
      (should= nil (read-ns-form (write-file "test/one.clj" "; hello")))
      (should= nil (read-ns-form (write-file "test/one.clj" "; (ns blah)"))))

    (it "pulls read ns form from files"
      (should= '(ns blah) (read-ns-form (write-file "test/one.clj" "(ns blah)")))
      (should= '(ns foo) (read-ns-form (write-file "test/one.clj" "; blah\n(ns foo)")))
      (should= '(ns blah (:use [foo]) (:require [bar])) (read-ns-form (write-file "test/one.clj" "(ns blah (:use [foo])(:require [bar]))"))))
    )

  (context "using sample files"

    (before (establish-sample-files))

    (it "finds src files from ns name"
      (should= (sample-file "sample/core.clj") (ns-to-file "sample.core"))
      (should= (sample-file "sample/a/one.clj") (ns-to-file "sample.a.one")))

    (it "finds depending files form ns form"
      (should= [] (depending-files-from '(ns foo)))
      (should= [] (depending-files-from '(ns foo (:use [clojure.set]))))
      (should= [(sample-file "sample/core.clj")] (depending-files-from '(ns foo (:use [sample.core]))))
      (should= #{(sample-file "sample/core.clj") (sample-file "sample/a/one.clj")}
        (set (depending-files-from '(ns foo (:use [sample.core]) (:require [sample.a.one]))))))

    (it "first freshening adds files to listing"
      (let [listing (atom {})]
        (make-fresh listing (clj-files-in sample-dir) (fn [_] true))
        (should= 3 (count @listing))
        (should= true (contains? @listing (sample-file "sample/core.clj")))
        (should= true (contains? @listing (sample-file "sample/a/one.clj")))
        (should= true (contains? @listing (sample-file "sample/b/one.clj")))))

    (it "new files are detected and added to listing"
      (let [listing (atom {})]
        (make-fresh listing (clj-files-in sample-dir) (fn [_] true))
        (write-file "sample/a/two.clj" "(ns sample.a.two)")
        (make-fresh listing (clj-files-in sample-dir) (fn [_] true))
        (should= 4 (count @listing))
        (should= true (contains? @listing (sample-file "sample/a/two.clj")))))

    (it "deleted files are removed from listing"
      (let [listing (atom {})]
        (make-fresh listing (clj-files-in sample-dir) (fn [_] true))
        (delete-file (sample-file "sample/a/one.clj"))
        (make-fresh listing (clj-files-in sample-dir) (fn [_] true))
        (should= 2 (count @listing))
        (should= false (contains? @listing (sample-file "sample/a/one.clj")))))

    (context "with freshener"

      (with audit-value (atom true))
      (with refresh-sample (freshener #(clj-files-in (file sample-dir "sample")) (fn [_] @@audit-value)))

      (it "reports new files in result map"
        (let [result (@refresh-sample)
              new-files #{(sample-file "sample/core.clj") (sample-file "sample/a/one.clj") (sample-file "sample/b/one.clj")}]
          (should= new-files (:new result))))

      (it "includes empty files"
        (write-file "sample/a/new.clj" "")
        (let [result (@refresh-sample)]
          (should= true (contains? (:new result) (sample-file "sample/a/new.clj")))))

      (it "reports deleted files in result map"
        (@refresh-sample)
        (delete-file (sample-file "sample/a/one.clj"))
        (let [result (@refresh-sample)]
          (should= #{(sample-file "sample/a/one.clj")} (:deleted result))))

      (it "reports modified files in result map"
        (@refresh-sample)
        (tweak-mod-time (sample-file "sample/a/one.clj") 1)
        (let [result (@refresh-sample)]
          (should= #{(sample-file "sample/a/one.clj")} (:modified result))))

      (it "reports reloaded files in result map"
        (let [result (@refresh-sample)
              reloaded [(sample-file "sample/a/one.clj") (sample-file "sample/b/one.clj") (sample-file "sample/core.clj")]]
          (should= reloaded (:reloaded result))))

      (it "tracks dependencies not provided by provider"
        (write-file "sample/a/one.clj" "(ns sample.a.one (:use [other.one]))")
        (write-file "other/one.clj" "(ns other.one)")
        (let [result (@refresh-sample)]
          (should= true (contains? (:new result) (sample-file "other/one.clj")))))

      (it "deletes unused dependencies not provided by provider"
        (write-file "sample/a/one.clj" "(ns sample.a.one (:use [other.one]))")
        (write-file "other/one.clj" "(ns other.one)")
        (@refresh-sample)
        (delete-file (sample-file "sample/a/one.clj"))
        (let [result (@refresh-sample)]
          (should= #{(sample-file "sample/a/one.clj") (sample-file "other/one.clj")} (:deleted result))))

      (it "reloads new src files"
        (@refresh-sample)
        (should= :sample-core (eval '(do (require 'sample.core) sample.core/sample-core)))
        (should= :sample-a-one (eval '(do (require 'sample.a.one) sample.a.one/sample-a-one)))
        (should= :sample-b-one (eval '(do (require 'sample.b.one) sample.b.one/sample-b-one))))

      (it "reloads modifies src files"
        (tweak-mod-time (sample-file "sample/a/one.clj") -1)
        (@refresh-sample)
        (write-file "sample/a/one.clj" "(ns sample.a.one (:use [sample.core])) (def sample-a-one :another-value)")
        (@refresh-sample)
        (should= :another-value (eval '(do (require 'sample.a.one) sample.a.one/sample-a-one))))

      (it "reloads dependencies of modified files"
        (write-file "sample/core.clj" "(ns sample.core (:use [other.one]))")
        (write-file "other/one.clj" "(ns other.one) (def other-one :other)")
        (@refresh-sample)
        (write-file "sample/a/one.clj" "(ns sample.a.one) (def sample-a-one :new-value)")
        (tweak-mod-time (sample-file "other/one.clj") 1)
        (let [result (@refresh-sample)
              reloaded [(sample-file "other/one.clj") (sample-file "sample/a/one.clj") (sample-file "sample/b/one.clj") (sample-file "sample/core.clj")]]
          (should= reloaded (:reloaded result))
          (should= :new-value (eval '(do (require 'sample.a.one) sample.a.one/sample-a-one)))))

      (it "unloads deleted files"
        (@refresh-sample)
        (delete-file (sample-file "sample/a/one.clj"))
        (@refresh-sample)
        (should= false (contains? @@#'clojure.core/*loaded-libs* 'sample.a.one)))

      (it "wont modify any state if the auditor return false"
        (reset! @audit-value false)
        (let [new-tag (.substring (str (rand)) 2)
              new-ns (format "sample.new%s" new-tag)]
          (write-file (format "sample/new%s.clj" new-tag) (format "(ns %s)" new-ns))
          (@refresh-sample)
          (should= false (contains? @@#'clojure.core/*loaded-libs* (symbol new-ns)))))
      )
    )

  )

(run-specs)
