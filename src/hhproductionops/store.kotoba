(ns hhproductionops.store
  "SSoT for the ISIC-9810 own-account-household-production TRACKING
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every `cloud-itonami-isic-*` actor in this
  fleet uses.

  ISIC Rev.4 class 9810 ('Undifferentiated goods-producing activities
  of private households for own use') is a UN System of National
  Accounts BOOKKEEPING category for own-account, non-market household
  production (subsistence farming, home food processing, household
  construction/repair done for the household's OWN consumption, not
  for sale) -- it exists so GDP/production statistics can account for
  non-market household output. There is no real-world 'business' that
  IS a 9810 entity the way a restaurant or publisher is. This actor is
  therefore NOT a marketplace or a production business -- it is an
  OPERATIONS COORDINATION actor for a structured own-account-production
  TRACKING PROGRAMME (the genuine, precedented real-world activity
  pattern: a rural-development / agricultural-extension programme, in
  the shape of an FAO / national-statistics-office household-production
  survey, that registers and tracks participating households' own-
  account production for statistical / food-security purposes). See
  README.md's Scope section for the full honest framing.

  This actor coordinates the back-office operations of such a
  programme: participating-household own-account production-quantity/
  type logging, extension-worker/enumerator household-visit scheduling,
  seed/tool/training in-kind support-coordination requests, and food-
  security-concern flagging. It never finalizes a welfare/aid-
  eligibility determination, never directly executes field/production
  work on a household's behalf, and never suspends or terminates a
  household's programme enrollment -- see `hhproductionops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable
  block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `households` directory keyed by `:household-id`
  STRING (never a keyword -- consistent keying from the start, avoiding
  the silent-miss bug that plagued an earlier shepherd attempt).

  A registered/verified household (programme-enrollment) record must
  exist before ANY proposal for that household may ever commit or
  escalate -- `hhproductionops.governor`'s `household-unverified-
  violations` re-derives this from the household's own `:registered?`/
  `:verified?` fields, never from proposal self-report, the SAME
  'ground truth, not self-report' discipline every sibling actor's own
  governor uses.

  The ledger stays append-only: which household a proposal targeted,
  which operation, on what basis, committed/held/escalated and
  approved by whom is always a query over an immutable log.")

(defprotocol Store
  (household [s household-id] "Registered household record, or nil.
    Household map: {:household-id .. :name .. :registered? bool :verified? bool}.")
  (all-households [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-households [s households] "replace/seed the household directory (map household-id->household)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained household directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline. Names are illustrative programme-participant labels,
  not real households."
  []
  {:households
   {"household-1" {:household-id "household-1" :name "Amina K. household (maize/cassava smallholding)"
                    :registered? true :verified? true}
    "household-2" {:household-id "household-2" :name "Rai T. household (rice/vegetable garden + poultry)"
                    :registered? true :verified? true}
    "household-3" {:household-id "household-3" :name "Novak household (recently applied, intake in progress)"
                    :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (household [_ household-id] (get-in @a [:households household-id]))
  (all-households [_] (sort-by :household-id (vals (:households @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-households [s households] (when (seq households) (swap! a assoc :households households)) s))

(defn seed-db
  "A MemStore seeded with the demo household directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `households` map (household-id
  string -> household map) -- the primary test/dev entry point.
  `households` may be empty (an unregistered-everywhere store)."
  [households]
  (->MemStore (atom {:households (or households {}) :ledger [] :coordination-log []})))
