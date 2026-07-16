(ns hhproductionops.advisor-test
  "Unit tests of `hhproductionops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [hhproductionops.advisor :as adv]
            [hhproductionops.store :as store]))

(def db (store/seed-db))

(deftest propose-production-record-shape
  (testing "production-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-household-production-record
                           :household-id "household-1"
                           :patch {:crop "maize" :estimated-yield-kg 180}})]
      (is (= :log-household-production-record (:op p)))
      (is (= "household-1" (:household-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :household-id)))))

(deftest propose-survey-visit-shape
  (testing "survey-visit proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-survey-visit
                           :household-id "household-2"
                           :patch {:enumerator "extension-worker-4" :date "2026-08-05"}})]
      (is (= :schedule-survey-visit (:op p)))
      (is (= "household-2" (:household-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-programme-support-shape
  (testing "programme-support proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-programme-support
                           :household-id "household-1"
                           :patch {:item "maize seed packet" :quantity 5 :estimated-cost 45.0}})]
      (is (= :coordinate-programme-support (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-food-security-concern-shape
  (testing "food-security-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-food-security-concern
                           :household-id "household-1"
                           :patch {:concern "drought stress on maize plot"}})]
      (is (= :flag-food-security-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-household-production-record :schedule-survey-visit :coordinate-programme-support
                :flag-food-security-concern]]
      (let [p (adv/infer db {:op op :household-id "household-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-household-production-record :schedule-survey-visit :coordinate-programme-support
                :flag-food-security-concern]]
      (let [p (adv/infer db {:op op :household-id "household-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
