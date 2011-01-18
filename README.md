# fresh
### A library to keep your clojure runtime 'Fresh' ###

## About

Clojure's dynamic nature allows you to add definitions or even change existing definition in a live runtime.
It's brilliant and powerful.  Yet, to do so you typically have to run in the REPL and it can become a tedious task
to reload files with every change you make, not to mention the dependencies of changes you make.

Fresh simplifies it all.  The source code was extracted from [Speclj's](https://github.com/slagyr/speclj) vigilant runner
(autotest) because of it's usefulness.  Simply tell Fresh which source code you want to keep fresh and it'll take care
of the rest for you.  It saves tons of time when used during development to:

* rerun tests without loading the JVM
* reload routes without restarting the server
* etc...

## Usage

The primary function is `freshener`.

    user=> (doc freshener)
    -------------------------
    fresh.core/freshener
    ([provider] [provider auditor])
      Returns a freshener function that, when invoked, will ensure
    the freshness of all files provided by the provider function.
    The provider must be a no-arg function that returns a seq of java.io.File
    objects.  If any of the files have been modified, they (and all
    thier dependent files), will be reloaded. New files will be loaded and
    tracked.  Deleted files will be unloaded along with any dependant files
    that are no longer referenced. The freshener function returns a report map
    of seqs containings File objects: {:new :modified :deleted :reloaded}.
    The optional auditor function is called, passing in the report map,
    before the state of the runtime has been modified.  Only when the auditor
    returns a truthy value will the runtime be modified.

## Examples

### Example #1

Below is a script that will reload all the Clojure source files in the src and spec directories.  It sits in an infinite
loop on the console waiting for you to press Enter.  Each time you press Enter, it printes a report and reloads. Simple!

    (ns console
      (:use
        [fresh.core :only (clj-files-in freshener)]
        [clojure.java.io :only (file)]))

    (defn files-to-keep-fresh []
      (clj-files-in (file "src") (file "spec")))

    (defn report-refresh [report]
      (println "Refreshing...")
      (println "(:new report):      " (:new report))
      (println "(:modified report): " (:modified report))
      (println "(:deleted report):  " (:deleted report))
      (println "(:reloaded report): " (:reloaded report))
      (println "")
      true)

    (def refresh-src (freshener files-to-keep-fresh report-refresh))

    (loop [key nil]
      (refresh-src)
      (println "Press any RETURN to reload, CTR-C to quit.")
      (recur (.read System/in)))

Assuming you have the Fresh source code checked out on your filesystem you can execute this command like so:

    $ java -cp src:spec:lib/clojure-1.2.0.jar:lib/dev/speclj-1.2.0.jar:path/to/fresh/src clojure.main path/to/fresh/example/console.clj

### Example #2

This example show two new techniques.  First notice how it produces a list of all the Clojure source files currently
loaded in the runtime.  Second, it uses the `ScheduledThreadPoolExecutor` to refresh every second.  Reloaded files
are printed.

    (ns timed
      (:use
        [fresh.core :only (ns-to-file freshener)])
      (:import
        [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

    (defn files-to-keep-fresh []
      (filter identity (map #(ns-to-file (.name %)) (all-ns))))

    (defn report-refresh [report]
      (when-let [reloaded (seq (:reloaded report))]
        (println "Refreshing...")
        (doseq [file reloaded] (println file))
        (println ""))
      true)

    (def refresh! (freshener files-to-keep-fresh report-refresh))
    (refresh!)
    (def scheduler (ScheduledThreadPoolExecutor. 1))
    (.scheduleWithFixedDelay scheduler refresh! 0 1000 TimeUnit/MILLISECONDS)
    (.awaitTermination scheduler Long/MAX_VALUE TimeUnit/SECONDS)

Now typically, you might include similar code in your dev environment.  This script, nor Example #1, are very useful
by them selves.  But to make this script interesting we'll have to load some of our code using the -i option.

    java -cp src:spec:lib/clojure-1.2.0.jar:lib/dev/speclj-1.2.0.jar:path/to/fresh/src clojure.main -i spec/your_package/core.clj path/to/fresh/example/timed.clj

## License

Copyright (C) 2011 Micah Martin All Rights Reserved.

Distributed under the The MIT License.
