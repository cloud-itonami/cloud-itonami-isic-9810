(ns hhproductionops.governor-test
  "Pure unit tests of `hhproductionops.governor/check` against
  hand-built proposals -- the fast, focused complement to
  `governor-contract-test`'s full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [hhproductionops.advisor :as advisor]
            [hhproductionops.governor :as gov]
            [hhproductionops.store :as store]))

(def household-1 {:household-id "household-1" :name "Amina K. household" :registered? true :verified? true})
(def household-3 {:household-id "household-3" :name "Novak household" :registered? true :verified? false})

(defn- clean-proposal [op household-id]
  {:op op :household-id household-id :summary "s" :rationale "routine programme coordination"
   :cites [household-id] :effect :propose :value {} :confidence 0.85})

(deftest household-unregistered-is-hard
  (testing "no household record at all -> HARD hold"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (clean-proposal :log-household-production-record "unknown-household") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:household-unverified} (map :rule (:violations verdict)))))))

(deftest household-unverified-is-hard
  (testing "household registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"household-3" household-3})
          verdict (gov/check {} nil (clean-proposal :log-household-production-record "household-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:household-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-survey-visit "household-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (clean-proposal :finalize-eligibility-determination "household-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest eligibility-determination-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches finalizing a welfare/aid-eligibility determination is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :log-household-production-record "household-1")
                          :rationale "finalized the eligibility determination for this household's aid application"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest direct-field-work-execution-content-is-hard
  (testing "a proposal touching direct execution of field/production work on the household's behalf is HARD-blocked, same as eligibility determination"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :schedule-survey-visit "household-1")
                          :rationale "the enumerator directly performed the harvest for the household during the visit"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest programme-enrollment-suspension-content-is-hard
  (testing "a proposal touching suspension/termination of a household's programme enrollment is HARD-blocked"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :coordinate-programme-support "household-1")
                          :summary "recommend to suspend programme enrollment for household-1 pending review")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-food-security-concern-is-not-scope-excluded
  (testing "flagging observed drought/harvest-shortfall concerns as a FOOD SECURITY CONCERN (not an eligibility determination) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"household-1" household-1})
          concern (assoc (clean-proposal :flag-food-security-concern "household-1")
                         :value {:concern "maize plot showing drought stress, estimated harvest down 40% vs last season"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (drought/harvest-shortfall) is exactly what this op exists to surface"))))

(deftest food-security-concern-always-escalates-clean
  (testing ":flag-food-security-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-food-security-concern "household-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-support-always-escalates
  (testing "a :coordinate-programme-support above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"household-1" household-1})
          expensive (assoc (clean-proposal :coordinate-programme-support "household-1")
                           :value {:item "hand tractor share + training" :estimated-cost 900.0}
                           :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-support-does-not-force-escalate
  (testing "a :coordinate-programme-support at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"household-1" household-1})
          cheap (assoc (clean-proposal :coordinate-programme-support "household-1")
                       :value {:item "maize seed packet" :estimated-cost 45.0}
                       :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "every allowlisted op's own DEFAULT (clean) advisor-generated proposal, for a registered+verified household, never trips :scope-excluded -- the fleet-known bare-noun self-tripping bug class this build avoided from the start by phrasing every scope-excluded term as a finalization/execution ACTION phrase, never a bare noun (see hhproductionops.governor ns docstring)"
    (let [s (store/mem-store {"household-1" household-1})
          db (store/seed-db)]
      (doseq [op gov/allowed-ops]
        (let [proposal (advisor/infer db {:op op :household-id "household-1"
                                          :patch {:crop "maize" :estimated-yield-kg 100
                                                  :enumerator "extension-worker-4" :date "2026-08-05"
                                                  :item "maize seed packet" :quantity 5 :estimated-cost 45.0
                                                  :concern "drought stress observed on maize plot"}})
              verdict (gov/check {} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for op " op " must never self-trip scope-exclusion; violations: "
                   (:violations verdict))))))))
