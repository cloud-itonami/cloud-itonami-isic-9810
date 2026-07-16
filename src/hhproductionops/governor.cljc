(ns hhproductionops.governor
  "HouseholdProductionGovernor -- the independent compliance layer
  that earns the HouseholdProductionAdvisor the right to commit. The
  advisor has no notion of whether a household is actually registered
  and verified in the programme, whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (household production-record logging, survey-visit scheduling,
  programme-support coordination, food-security-concern flagging). It
  NEVER performs or authorizes:
    - finalizing a welfare/aid-eligibility determination
    - directly executing field/production work on a household's behalf
    - suspending or terminating a household's programme enrollment

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Household unverified       -- the target household (programme-
                                      enrollment) record must exist AND
                                      be independently confirmed
                                      `:registered?`/`:verified?` in
                                      the store before ANY proposal for
                                      it may commit or even escalate.
                                      Never trusts a proposal's own
                                      claim about the household --
                                      re-derived from the household's
                                      own store record, the same
                                      'ground truth, not self-report'
                                      discipline every sibling actor's
                                      governor uses.
    2. Effect not :propose        -- every proposal's `:effect` MUST
                                      be `:propose`. Any other effect
                                      value is, by construction, a
                                      claim to directly actuate/commit
                                      outside governance -- HARD block,
                                      not merely low-confidence.
    3. Scope exclusion            -- ANY proposal (regardless of op)
                                      whose op, rationale, summary,
                                      citations or draft value touches
                                      finalizing-a-welfare/aid-
                                      eligibility-determination /
                                      directly-executing-field-
                                      production-work /
                                      suspending-or-terminating-
                                      programme-enrollment territory is
                                      a HARD, PERMANENT block -- this
                                      actor's charter excludes that
                                      territory structurally, not as a
                                      rollout milestone. Evaluated
                                      UNCONDITIONALLY on every
                                      proposal. An op outside the
                                      closed four-op allowlist is the
                                      SAME failure mode (an advisor
                                      proposing something it was never
                                      authorized to propose) and is
                                      folded into this same check.

  KNOWN BUG CLASS this ns deliberately avoids: `scope-excluded-terms`
  below is phrased ENTIRELY as finalization/execution ACTION phrases
  ('finalize the eligibility determination', 'directly perform the
  harvest', 'suspend programme enrollment'), never as a bare noun
  ('eligibility', 'harvest', 'enrollment') -- several sibling actors in
  this fleet independently discovered that a bare-noun exclusion term
  can accidentally match inside the mock advisor's OWN default
  rationale text for a legitimate, allowed proposal (e.g. a household-
  production-record log entry that legitimately discusses 'harvest
  quantity', or a programme-support proposal that legitimately
  discusses 'programme enrollment status'), causing the actor to
  self-block on its own happy path. `default-mock-advisor-proposals-
  never-self-trip-scope-exclusion` in `governor_test.clj` is a
  dedicated regression test asserting every allowlisted op's own
  default (clean) advisor-generated proposal clears this scan.

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-food-security-concern` -- ALWAYS escalates to a
      human, regardless of confidence, regardless of how clean the
      proposal otherwise is. `hhproductionops.phase` independently
      agrees: `:flag-food-security-concern` is never a member of any
      phase's `:auto` set either -- two layers, not one.
    - A `:coordinate-programme-support` whose drafted `:value` names
      an `:estimated-cost` above `support-cost-threshold` -- a
      large-value in-kind support proposal always needs a human
      sign-off, even when the governor and phase would otherwise allow
      auto-commit."
  (:require [clojure.string :as str]
            [hhproductionops.store :as store]))

(def confidence-floor 0.6)

(def support-cost-threshold
  "Example rural-development/agricultural-extension programme in-kind
  support-coordination threshold (USD-equivalent units, domain-
  illustrative -- not a universal cross-domain constant). A
  `:coordinate-programme-support` proposal citing an `:estimated-cost`
  above this value ALWAYS escalates to human sign-off, regardless of
  confidence or rollout phase."
  250.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-household-production-record :schedule-survey-visit
    :coordinate-programme-support :flag-food-security-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-food-security-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- finalizing a welfare/aid-
  eligibility determination, directly executing field/production work
  on a household's behalf, or suspending/terminating a household's
  programme enrollment. Every entry is deliberately phrased as the
  finalization/execution ACTION, never a bare noun -- see this ns's
  own docstring on the fleet-known bare-noun self-tripping bug class.
  Scanned across the proposal's op/summary/rationale/cites/value,
  never trusting the advisor's own framing of its intent."
  ["finalize the eligibility determination" "finalized the eligibility determination"
   "finalize eligibility determination" "finalizes the eligibility determination"
   "finalize the aid eligibility determination" "finalize the welfare determination"
   "finalized the welfare determination" "finalize the benefit determination"
   "finalized the benefit determination" "determine eligibility for aid"
   "determined eligibility for aid" "determining eligibility for aid"
   "grant aid eligibility" "granted aid eligibility" "grant welfare eligibility"
   "granted welfare eligibility" "eligibility determination decision"
   "福祉受給資格の確定" "受給資格を確定" "給付資格の確定" "給付資格を確定"
   "directly perform the harvest" "directly performed the harvest" "directly performs the harvest"
   "directly execute the production work" "directly executed the production work"
   "execute production on the household's behalf" "executed production on the household's behalf"
   "execute the harvest on the household's behalf" "executed the harvest on the household's behalf"
   "physically carry out the field work" "physically carried out the field work"
   "conduct the planting on the household's behalf" "conducted the planting on the household's behalf"
   "世帯に代わって生産作業を実施" "世帯の代わりに収穫作業を実施" "世帯に代わり収穫作業を代行"
   "suspend programme enrollment" "suspended programme enrollment" "suspends programme enrollment"
   "terminate programme enrollment" "terminated programme enrollment" "terminates programme enrollment"
   "revoke programme enrollment" "revoked programme enrollment" "revokes programme enrollment"
   "programme enrollment suspension" "programme enrollment termination"
   "プログラム登録の停止" "プログラム登録を停止" "プログラム登録の取消"])

;; ----------------------------- checks -----------------------------

(defn- household-unverified-violations
  "The target household (programme-enrollment record) must exist AND
  be independently `:registered?`/`:verified?` in the store -- never
  trust the proposal's own `:household-id` claim without a store
  lookup."
  [{:keys [household-id]} st]
  (let [h (store/household st household-id)]
    (when-not (and h (:registered? h) (:verified? h))
      [{:rule :household-unverified
        :detail (str household-id " は未登録または未検証の世帯(プログラム登録) -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches finalizing-a-welfare/aid-eligibility-
  determination / directly-executing-field-production-work /
  suspending-or-terminating-programme-enrollment territory, regardless
  of confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "福祉/給付資格判定の確定、世帯に代わる生産作業の直接実施、プログラム登録の停止/取消の判断領域に触れる提案は永久に禁止"}])))

(defn- high-cost-support?
  "A `:coordinate-programme-support` proposal citing an
  `:estimated-cost` above `support-cost-threshold` -- always needs
  human sign-off (SOFT escalate, not a hard block: the support
  coordination itself is in scope, only its size requires a human)."
  [proposal]
  (and (= :coordinate-programme-support (:op proposal))
       (some-> proposal :value :estimated-cost (> support-cost-threshold))))

(defn check
  "Censors a HouseholdProductionAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [household-id (or (:household-id proposal) (:household-id request))
        hard (into []
                   (concat (household-unverified-violations {:household-id household-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-support? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :household-id (:household-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
