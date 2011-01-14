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
  (write-file "sample/core.clj" "(ns sample.core)")
  (write-file "sample/a/one.clj" "(ns sample.a.one (:use [sample.core])")
  (write-file "sample/b/one.clj" "; comment\n(ns sample.b.one (:use [sample.core])"))

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

    (it "creating a code freshener"
      (let [listing (atom {})]
        (make-fresh listing (file-seq sample-dir))
        (should= 3 (count @listing))
        (should= true (contains? @listing (sample-file "sample/core.clj")))
        (should= true (contains? @listing (sample-file "sample/a/one.clj")))
        (should= true (contains? @listing (sample-file "sample/b/one.clj")))))

    )

  )

;
;(defn tweak-mod-time [runner file tweak]
;  (let [listing (.listing runner)
;        tracker (get @listing file)]
;    (if tracker
;      (swap! listing assoc file (new-file-tracker (.ns tracker) (tweak (.mod-time tracker)) (.dependencies tracker))))))
;
;(describe "Vigilant Runner"
;  (before (doseq [dir [tmp-dir test-dir src-dir]] (.mkdir dir)))
;  (after (delete-tmp-dir))
;  (with runner (new-vigilant-runner))
;  (around [_] (binding [ns-to-file fake-ns-to-file] (_)))
;
;  (it "detects no changes with empty directory"
;    (should= 0 (count (updated-files @runner spec-dirs))))
;
;  (it "detects changes on first check"
;    (write-tmp-file "test/one.clj" "(ns one)")
;    (let [updates (updated-files @runner spec-dirs)]
;      (should= 1 (count updates))
;      (should= "one.clj" (.getName (first updates)))))
;
;  (it "detects changes new files"
;    (updated-files @runner spec-dirs)
;    (write-tmp-file "test/one.clj" "()")
;    (let [updates (updated-files @runner spec-dirs)]
;      (should= 1 (count updates))
;      (should= "one.clj" (.getName (first updates)))))
;
;  (it "detects changes on changed files"
;    (let [tmp-file (write-tmp-file "test/one.clj" "(ns one)")]
;      (track-files @runner tmp-file)
;      (tweak-mod-time @runner tmp-file dec))
;    (let [updates (updated-files @runner spec-dirs)]
;      (should= 1 (count updates))
;      (should= "one.clj" (.getName (first updates)))))
;
;  (it "doesn't detect changes on unchanged files"
;    (track-files @runner (write-tmp-file "test/one.clj" "(ns one)"))
;    (should= 0 (count (updated-files @runner spec-dirs))))
;
;  (it "detects file dependencies based on :use"
;    (let [src-file (write-tmp-file "src/core.clj" "(ns core)")
;          test-file (write-tmp-file "test/core-test.clj" "(ns core-test (:use [core]))")]
;      (track-files @runner test-file)
;      (tweak-mod-time @runner src-file dec)
;      (tweak-mod-time @runner test-file dec))
;    (let [updates (updated-files @runner spec-dirs)]
;      (should= 2 (count updates))
;      (should= "core-test.clj" (.getName (first (next updates))))
;      (should= "core.clj" (.getName (first updates)))))
;
;  (it "tracks empty files"
;    (let [test-file (write-tmp-file "test/core-test.clj" "")]
;      (track-files @runner test-file)
;      (let [tracker (get @(.listing @runner) test-file)]
;        (should-not= nil tracker)
;        (should= nil (.ns tracker)))))
;
;  (it "stops tracking files that have been deleted, along with their dependencies"
;    (let [src-file1 (write-tmp-file "src/src1.clj" "(ns src1)")
;          src-file2 (write-tmp-file "src/src2.clj" "(ns src2)")
;          test-file1 (write-tmp-file "test/test1.clj" "(ns test1 (:use [src1][src2]))")
;          test-file2 (write-tmp-file "test/test2.clj" "(ns test2 (:use [src2]))")]
;      (track-files @runner test-file1 test-file2)
;      (should-not= nil (get @(.listing @runner) test-file1))
;      (should-not= nil (get @(.listing @runner) test-file2))
;      (should-not= nil (get @(.listing @runner) src-file1))
;      (should-not= nil (get @(.listing @runner) src-file2))
;      (.delete test-file1)
;      (clean-deleted-files @runner)
;      (should= 0 (count (updated-files @runner spec-dirs)))
;      (should= nil (get @(.listing @runner) test-file1))
;      (should= nil (get @(.listing @runner) src-file1))
;      (should-not= nil (get @(.listing @runner) test-file2))
;      (should-not= nil (get @(.listing @runner) src-file2))))
;
;  (it "stops tracking files that have been deleted, along with their NESTED dependencies"
;    (let [src-file1 (write-tmp-file "src/src1.clj" "(ns src1 (:use [src2]))")
;          src-file2 (write-tmp-file "src/src2.clj" "(ns src2)")
;          test-file1 (write-tmp-file "test/test1.clj" "(ns test1 (:use [src1]))")]
;      (track-files @runner test-file1)
;      (should-not= nil (get @(.listing @runner) test-file1))
;      (should-not= nil (get @(.listing @runner) src-file1))
;      (should-not= nil (get @(.listing @runner) src-file2))
;      (.delete test-file1)
;      (clean-deleted-files @runner)
;      (should= 0 (count (updated-files @runner spec-dirs)))
;      (should= nil (get @(.listing @runner) test-file1))
;      (should= nil (get @(.listing @runner) src-file1))
;      (should= nil (get @(.listing @runner) src-file2))))
;
;  (it "finds dependents of a given file"
;    (let [src-file1 (write-tmp-file "src/src1.clj" "(ns src1)")
;          src-file2 (write-tmp-file "src/src2.clj" "(ns src2)")
;          test-file1 (write-tmp-file "test/test1.clj" "(ns test1 (:use [src1][src2]))")
;          test-file2 (write-tmp-file "test/test2.clj" "(ns test2 (:use [src2]))")]
;      (track-files @runner test-file1 test-file2)
;      (should= #{} (dependents-of @(.listing @runner) [test-file1]))
;      (should= #{} (dependents-of @(.listing @runner) [test-file2]))
;      (should= #{test-file1} (dependents-of @(.listing @runner) [src-file1]))
;      (should= #{test-file1 test-file2} (dependents-of @(.listing @runner) [src-file2]))))
;
;  (it "finds transitive depedants/dependencies of a file"
;    (let [src-file1 (write-tmp-file "src/src1.clj" "(ns src1)")
;          src-file2 (write-tmp-file "src/src2.clj" "(ns src2 (:use [src1]))")
;          test-file1 (write-tmp-file "test/test1.clj" "(ns test1 (:use [src2]))")]
;      (track-files @runner test-file1)
;      (should= 3 (count @(.listing @runner)))
;      (should= #{} (dependents-of @(.listing @runner) [test-file1]))
;      (should= #{test-file1} (dependents-of @(.listing @runner) [src-file2]))
;      (should= #{test-file1 src-file2} (dependents-of @(.listing @runner) [src-file1]))))
;
;
;

;    )
;


;  (it "reloads files"
;    (let [src-file (write-tmp-file "src/src1.clj" "(ns src1) (def foo :foo) (println :haha)")
;          test-file1 (write-tmp-file "test/test1.clj" "(ns test1 (:use [src1]))")]
;      (track-files @runner test-file1)
;      (apply reload-files @runner (keys @(.listing @runner)))
;      (eval '(require 'src1))
;      (should= :foo (eval 'src1/foo))
;      (write-tmp-file "src/src1.clj" "(ns src1)")
;      (apply reload-files @runner (keys @(.listing @runner)))
;      (should= false (bound? (eval 'src1/foo)))))


(run-specs)
