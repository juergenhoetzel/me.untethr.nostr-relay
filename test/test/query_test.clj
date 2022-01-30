(ns test.query-test
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [me.untethr.nostr.app :as app]
            [me.untethr.nostr.metrics :as metrics]
            [me.untethr.nostr.query :as query]
            [test.support :as support]
            [test.test-data :as test-data]))

(defn- query* [db filters & [target-row-id]]
  (mapv :rowid
    (jdbc/execute! db (query/filters->query filters target-row-id)
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn- row-id->id
  [db row-id]
  (:id
    (jdbc/execute-one! db ["select id from n_events where rowid = ?" row-id]
      {:builder-fn rs/as-unqualified-lower-maps})))

(deftest query-test
  (support/with-memory-db [db]
    (support/load-data db (:pool test-data/pool-with-filters))
    (doseq [[filters results] (:filters->results test-data/pool-with-filters)]
      (is (= (set results)
            (into #{} (map (partial row-id->id db)) (query* db filters)))
        (pr-str [filters results])))
    ;; with the well-known data set, let's test some w/ target-row-id..
    (is (= #{1 2 4} (-> (query* db
                          [{:ids ["100" "101"]}
                           {:#e ["100"]}
                           {:#e ["102" "103"]}] 4) set)))))

(deftest regression-test
  (support/with-regression-data [data-vec]
    (support/with-memory-db [db]
      (let [fake-metrics (metrics/create-metrics)
            [_req req-id & req-filters] (#'app/parse (nth data-vec 2))
            raw-evt (nth data-vec 3)
            [_ evt] (#'app/parse raw-evt)]
        (#'app/store-event! fake-metrics db evt raw-evt)
        (= 1 (query* db req-filters))))))
