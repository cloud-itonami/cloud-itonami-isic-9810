(ns hhproductionops.store-contract-test
  "Contract tests for `hhproductionops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [hhproductionops.store :as store]))

(deftest mem-store-household-lookup
  (testing "MemStore can store and retrieve households by ID (string keys)"
    (let [households {"h1" {:household-id "h1" :name "Alice's household" :registered? true :verified? true}}
          s (store/mem-store households)]
      (is (some? (store/household s "h1")))
      (is (nil? (store/household s "h99"))))))

(deftest mem-store-all-households
  (testing "MemStore returns all households in sorted order"
    (let [households {"h2" {:household-id "h2" :name "Bob's household"}
                       "h1" {:household-id "h1" :name "Alice's household"}
                       "h3" {:household-id "h3" :name "Carol's household"}}
          s (store/mem-store households)
          all-h (store/all-households s)]
      (is (= 3 (count all-h)))
      (is (= "h1" (:household-id (first all-h))))
      (is (= "h3" (:household-id (last all-h)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-household-production-record :household-id "h1" :value {:estimated-yield-kg 100}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-households
  (testing "MemStore with-households replaces the household directory"
    (let [s (store/mem-store {})
          new-households {"h1" {:household-id "h1" :name "Alice's household"}}]
      (is (= 0 (count (store/all-households s))))
      (store/with-households s new-households)
      (is (= 1 (count (store/all-households s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo households"
    (let [s (store/seed-db)]
      (is (> (count (store/all-households s)) 0))
      (is (some? (store/household s "household-1")))
      (is (some? (store/household s "household-2")))
      (is (some? (store/household s "household-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for household-id"
    (let [demo (store/demo-data)
          households (:households demo)]
      (doseq [[k v] households]
        (is (string? k) "keys must be strings")
        (is (string? (:household-id v)) "household-id must be string")
        (is (= k (:household-id v)) "key must match household-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
