(ns hhproductionops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean household-production-
  record logging request through intake -> advise -> govern -> decide
  -> approval -> commit at phase 1 (assisted-logging, always
  approval), then re-runs the same op at phase 3 (supervised-auto,
  clean + high confidence -> auto-commit), then a survey-visit-
  scheduling request and a low-cost programme-support coordination
  (both auto-commit clean at phase 3), then a high-cost programme-
  support coordination (ALWAYS escalates regardless of phase), then a
  food-security-concern flag (ALWAYS escalates, at any phase --
  approve, then commit), then HARD-hold scenarios: an unregistered
  household, a household registered but not yet verified, a proposal
  whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded welfare/aid-eligibility-
  determination / direct-field-work-execution scope."
  (:require [langgraph.graph :as g]
            [hhproductionops.advisor :as advisor]
            [hhproductionops.store :as store]
            [hhproductionops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "programme-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :programme-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :programme-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-household-production-record household-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-household-production-record :household-id "household-1"
                                  :patch {:crop "maize" :estimated-yield-kg 180 :period "2026-Q2"}} coordinator-phase-1)]
      (println r)
      (println "-- human programme coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-household-production-record household-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-household-production-record :household-id "household-1"
                                  :patch {:crop "cassava" :estimated-yield-kg 90 :period "2026-Q3"}} coordinator-phase-3))

    (println "\n== schedule-survey-visit household-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-survey-visit :household-id "household-1"
                                  :patch {:enumerator "extension-worker-4" :date "2026-08-05" :window "09:00-10:30"}} coordinator-phase-3))

    (println "\n== coordinate-programme-support household-1, low cost (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-programme-support :household-id "household-1"
                                  :patch {:item "maize seed packet" :quantity 5 :estimated-cost 45.0}} coordinator-phase-3))

    (println "\n== coordinate-programme-support household-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-programme-support :household-id "household-1"
                                 :patch {:item "hand tractor share + training" :quantity 1 :estimated-cost 900.0}} coordinator-phase-3)]
      (println r)
      (println "-- human programme coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-food-security-concern household-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-food-security-concern :household-id "household-1"
                                 :patch {:concern "maize plot showing drought stress, estimated yield down 40% vs last season" :confidence 0.9}} coordinator-phase-3)]
      (println r)
      (println "-- human programme coordinator reviews & approves --")
      (println (approve! actor "t6")))

    (println "\n== log-household-production-record household-99 (unregistered household -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-household-production-record :household-id "household-99"
                                  :patch {:crop "unknown"}} coordinator-phase-3))

    (println "\n== log-household-production-record household-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-household-production-record :household-id "household-3"
                                  :patch {:crop "rice" :estimated-yield-kg 20}} coordinator-phase-3))

    (println "\n== schedule-survey-visit household-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t9" {:op :schedule-survey-visit :household-id "household-1"
                                           :patch {:enumerator "extension-worker-5" :date "2026-08-12"}} coordinator-phase-3)))

    (println "\n== log-household-production-record household-1, advisor drifts into eligibility-determination/field-work-execution scope -> HARD hold, permanent ==")
    (println (exec-op actor "t10" {:op :log-household-production-record :household-id "household-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
