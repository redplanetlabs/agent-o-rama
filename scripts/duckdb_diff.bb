#!/usr/bin/env bb
;; DUCKDB DIFF: Compare two DuckDB databases with GF(3) trifurcation
;;
;; Usage: bb scripts/duckdb_diff.bb <db1.duckdb> <db2.duckdb>
;;
;; MINUS (-1): Schema diff (tables, views, columns)
;; ERGODIC (0): Row count diff per table  
;; PLUS (+1): Data diff (new/deleted/changed rows)

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(defn duckdb-query [db sql]
  (let [result (p/shell {:out :string :err :string}
                        "duckdb" db "-json" sql)]
    (when (zero? (:exit result))
      (try (json/parse-string (:out result) true)
           (catch Exception _ nil)))))

(defn duckdb-exec [sql]
  (let [result (p/shell {:out :string :err :string}
                        "duckdb" "-json" sql)]
    (when (zero? (:exit result))
      (try (json/parse-string (:out result) true)
           (catch Exception _ nil)))))

;;; MINUS (-1): Schema Diff
(defn minus-schema-diff [db1 db2]
  (let [sql (format "
    ATTACH '%s' AS db1 (READ_ONLY);
    ATTACH '%s' AS db2 (READ_ONLY);
    
    SELECT 'db1_only' as location, type, name 
    FROM db1.sqlite_master WHERE type IN ('table','view')
    EXCEPT 
    SELECT 'db1_only', type, name 
    FROM db2.sqlite_master WHERE type IN ('table','view')
    
    UNION ALL
    
    SELECT 'db2_only' as location, type, name 
    FROM db2.sqlite_master WHERE type IN ('table','view')
    EXCEPT 
    SELECT 'db2_only', type, name 
    FROM db1.sqlite_master WHERE type IN ('table','view')
    
    ORDER BY location, type, name;
    " db1 db2)]
    {:agent :MINUS
     :trit -1
     :operation :schema-diff
     :differences (duckdb-exec sql)}))

;;; ERGODIC (0): Row Count Diff  
(defn ergodic-count-diff [db1 db2]
  (let [tables1 (duckdb-query db1 "SELECT name FROM sqlite_master WHERE type='table';")
        tables2 (duckdb-query db2 "SELECT name FROM sqlite_master WHERE type='table';")
        common-tables (clojure.set/intersection 
                       (set (map :name tables1))
                       (set (map :name tables2)))
        count-diffs (for [tbl common-tables]
                      (let [c1 (-> (duckdb-query db1 (format "SELECT COUNT(*) as cnt FROM \"%s\"" tbl))
                                   first :cnt)
                            c2 (-> (duckdb-query db2 (format "SELECT COUNT(*) as cnt FROM \"%s\"" tbl))
                                   first :cnt)]
                        {:table tbl :db1 c1 :db2 c2 :delta (- (or c1 0) (or c2 0))}))]
    {:agent :ERGODIC
     :trit 0
     :operation :count-diff
     :tables (sort-by #(Math/abs (:delta %)) > count-diffs)}))

;;; PLUS (+1): Data Diff (sample)
(defn plus-data-diff [db1 db2 table-name & {:keys [limit] :or {limit 10}}]
  (let [sql-new (format "
    ATTACH '%s' AS db1 (READ_ONLY);
    ATTACH '%s' AS db2 (READ_ONLY);
    SELECT * FROM db1.\"%s\" EXCEPT SELECT * FROM db2.\"%s\" LIMIT %d;
    " db1 db2 table-name table-name limit)
        sql-del (format "
    ATTACH '%s' AS db1 (READ_ONLY);
    ATTACH '%s' AS db2 (READ_ONLY);
    SELECT * FROM db2.\"%s\" EXCEPT SELECT * FROM db1.\"%s\" LIMIT %d;
    " db1 db2 table-name table-name limit)]
    {:agent :PLUS
     :trit +1
     :operation :data-diff
     :table table-name
     :new-in-db1 (duckdb-exec sql-new)
     :deleted-from-db1 (duckdb-exec sql-del)}))

;;; Main
(defn -main [& args]
  (if (< (count args) 2)
    (do (println "Usage: bb scripts/duckdb_diff.bb <db1.duckdb> <db2.duckdb> [table]")
        (System/exit 1))
    (let [[db1 db2 table] args]
      (println "╔════════════════════════════════════════════════════════════╗")
      (println "║              DUCKDB TRIFURCATED DIFF                       ║")
      (println "╚════════════════════════════════════════════════════════════╝")
      (println (format "\n  DB1: %s\n  DB2: %s" db1 db2))
      
      (println "\n=== MINUS (-1): Schema Diff ===")
      (let [schema (minus-schema-diff db1 db2)]
        (if (seq (:differences schema))
          (doseq [d (:differences schema)]
            (println (format "  [%s] %s: %s" (:location d) (:type d) (:name d))))
          (println "  Schemas identical")))
      
      (println "\n=== ERGODIC (0): Row Count Diff ===")
      (let [counts (ergodic-count-diff db1 db2)]
        (doseq [t (:tables counts)]
          (when (not= 0 (:delta t))
            (println (format "  %s: %d → %d (Δ %+d)" 
                             (:table t) (:db2 t) (:db1 t) (:delta t))))))
      
      (when table
        (println (format "\n=== PLUS (+1): Data Diff [%s] ===" table))
        (let [data (plus-data-diff db1 db2 table)]
          (println "  New rows:" (count (:new-in-db1 data)))
          (println "  Deleted:" (count (:deleted-from-db1 data))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
