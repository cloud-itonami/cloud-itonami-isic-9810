(ns hhproductionops.advisor
  "HouseholdProductionAdvisor -- the *contained intelligence node* for
  the ISIC-9810 own-account-household-production tracking-programme
  operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: household own-account production-record logging,
  extension-worker/enumerator survey-visit scheduling, programme
  in-kind support-coordination (seed/tool/training), and food-
  security-concern flagging. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation --
  every proposal's `:effect` is always `:propose`. Every output is
  censored downstream by `hhproductionops.governor` before anything
  touches the SSoT.

  This advisor NEVER drafts a welfare/aid-eligibility-determination
  decision, a direct execution of field/production work on a
  household's behalf, or a programme-enrollment suspension/termination
  decision -- those are permanently out of scope for this actor, not
  merely un-implemented. `hhproductionops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal
  for exactly this failure mode (a compromised or confused advisor
  drifting into scope it must never touch) and HARD-holds it,
  regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :household-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-production-record
  "Draft an own-account household-production log entry. Pure logging
  of the household's own self-reported production quantity/type
  (e.g. an estimated crop yield, livestock head count, or home
  food-processing volume) for statistical/food-security tracking --
  never a welfare/aid-eligibility judgement."
  [_db {:keys [household-id patch]}]
  {:op         :log-household-production-record
   :household-id household-id
   :summary    (str household-id " の自家消費生産記録を記録: " (pr-str (keys patch)))
   :rationale  "世帯からの自己申告に基づく自家消費用生産量/品目の観察記録のみ。給付資格の判断なし。"
   :cites      [household-id]
   :effect     :propose
   :value      (merge {:household-id household-id} patch)
   :confidence 0.92})

(defn- propose-survey-visit
  "Draft an extension-worker/enumerator household-visit scheduling
  proposal (a calendar entry, never a direct field intervention)."
  [_db {:keys [household-id patch]}]
  {:op         :schedule-survey-visit
   :household-id household-id
   :summary    (str household-id " への調査訪問予定を提案: " (pr-str (keys patch)))
   :rationale  "普及員/調査員の訪問日程調整のみ。現地での生産作業の代行は行わない。"
   :cites      [household-id]
   :effect     :propose
   :value      (merge {:household-id household-id} patch)
   :confidence 0.88})

(defn- propose-programme-support
  "Draft an in-kind programme-support coordination request (seed,
  small tools, training-session enrollment -- never a finalized aid/
  benefit determination; a human always confirms and disburses any
  support)."
  [_db {:keys [household-id patch]}]
  {:op         :coordinate-programme-support
   :household-id household-id
   :summary    (str household-id " への支援調整案を提案: " (pr-str (keys patch)))
   :rationale  "種子・小型農具・研修受講などの現物支援調整の提案のみ。支給の可否は人間が判断する。"
   :cites      [household-id]
   :effect     :propose
   :value      (merge {:household-id household-id} patch)
   :confidence 0.90})

(defn- propose-food-security-concern
  "Surface a food-insecurity/production-shortfall concern (drought
  damage, failed harvest, unusually low observed production) for
  HUMAN triage. This op ALWAYS escalates in `hhproductionops.governor`
  -- never auto-committed at any phase -- regardless of how confident
  the advisor is that the concern is real."
  [_db {:keys [household-id patch]}]
  {:op         :flag-food-security-concern
   :household-id household-id
   :summary    (str household-id " の食料安全保障懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "干ばつ被害・収穫不良・生産量の急減等の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [household-id]
   :effect     :propose
   :value      (merge {:household-id household-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-household-production-record (propose-production-record _db request)
                   :schedule-survey-visit (propose-survey-visit _db request)
                   :coordinate-programme-support (propose-programme-support _db request)
                   :flag-food-security-concern (propose-food-security-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually finalized the eligibility determination and directly performed the harvest on the household's behalf")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :household-id (:household-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
