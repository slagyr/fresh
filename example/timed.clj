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