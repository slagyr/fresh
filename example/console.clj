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







