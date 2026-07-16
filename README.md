# cloud-itonami-isic-9810

**Undifferentiated goods-producing activities of private households for own use** — ISIC Rev.4 class 9810.

## Scope (read this first — this class is not a normal "business")

ISIC 9810 is a UN System of National Accounts **bookkeeping category** for
own-account, non-market household production — subsistence farming, home
food processing, household construction/repair done for the household's
**own consumption**, not for sale. It exists so that GDP / national
production statistics can account for non-market household output. There
is essentially **no real-world "business" that IS a 9810 entity** the way a
restaurant (ISIC 5610) or a publisher (ISIC 5811) is — a household growing
its own food or building its own shed does not thereby become a firm.

This repository does **not** invent a fictitious commercial business for
this class. Instead it models an **OPERATIONS COORDINATION actor for a
structured own-account-production TRACKING PROGRAMME**: the genuine,
precedented real-world activity pattern in which a rural-development or
agricultural-extension programme (in the shape of an FAO / national-
statistics-office household-production survey) registers and tracks
participating households' own-account production for **statistical and
food-security purposes**. This is a **data-coordination / statistical-
support use case, not a marketplace or a production business**. The actor
never grows, builds, harvests, or sells anything itself — it coordinates
the back-office paperwork of a programme that tracks what households
already produce for themselves.

A coordination-only actor for such a programme, behind an independent
Governor that earns advisor trust through structured oversight: proposal →
advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: `log-household-production-record`, `schedule-survey-visit`, `coordinate-programme-support`, `flag-food-security-concern` (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Household unverified** — the target household's programme-enrollment record must exist AND be independently registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing a welfare/aid-eligibility determination, directly executing field/production work on a household's behalf, and suspending/terminating a household's programme enrollment are permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-food-security-concern` — ALWAYS escalates, regardless of confidence or phase. A "flag a concern" op is never auto-commit-eligible and never finalizes any determination itself — it only surfaces the concern for a human.
  - `:coordinate-programme-support` above a cost threshold — a large-value in-kind support proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: production-record logging only (approval-gated)
  - Phase 2: + survey-visit scheduling, programme-support proposals (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals (food-security concerns and high-cost support proposals always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL — never a welfare/aid-eligibility authority

This actor's op allowlist **never** includes an op that directly finalizes
a welfare/aid-eligibility determination. Any content touching that
territory — regardless of which op carries it — is a HARD, permanent,
un-overridable governor block. `:flag-food-security-concern` only
surfaces an observation for a human; it never decides anything, and it is
never a member of any rollout phase's auto-commit set. This actor does
not, and structurally cannot, determine who receives aid.

## Out of scope (structural, not a rollout milestone)

This actor is **operations coordination only**. It never performs or
authorizes:

- Finalizing a welfare/aid-eligibility determination.
- Directly executing field/production work on a household's behalf (this actor coordinates paperwork, not farm labor).
- Suspending or terminating a household's programme enrollment.

The governor's `scope-exclusion-violations` check re-scans every proposal
for this failure mode independently of the advisor's own framing, and
treats it as a HARD, permanent block regardless of confidence or how
clean everything else is. Every scope-excluded term is phrased as the
finalization/execution ACTION (e.g. "finalize the eligibility
determination"), never as a bare noun (e.g. "eligibility") — see
`hhproductionops.governor`'s ns docstring for why: a bare-noun exclusion
term can accidentally match inside the mock advisor's own legitimate
default proposal text and cause the actor to self-block on its own happy
path, a bug class independently discovered and fixed by multiple sibling
actors in this fleet. `default-mock-advisor-proposals-never-self-trip-
scope-exclusion` in `governor_test.clj` is a dedicated regression test
guarding against it.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/hhproductionops/governor_test.clj` — unit tests of governor hard checks and scope exclusion
- `test/hhproductionops/advisor_test.clj` — advisor proposal shape and consistency
- `test/hhproductionops/phase_test.clj` — rollout phase logic
- `test/hhproductionops/governor_contract_test.clj` — full graph integration, audit trail
- `test/hhproductionops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `hhproductionops.store` — SSoT (MemStore, String-keyed household directory, append-only ledger)
- `hhproductionops.advisor` — contained intelligence node (mock + real-LLM seam)
- `hhproductionops.governor` — independent compliance layer
- `hhproductionops.phase` — staged rollout (0→3)
- `hhproductionops.operation` — langgraph-clj StateGraph
- `hhproductionops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See
ADR-2607121000 (reverse-toposort rollout plan), ADR-2607152500 (Wave 4
person-facing-service safety guardrail), and
`cloud-itonami-isic-9810-undifferentiated-household-goods-coverage` (this
class's own ADR pair) for design decisions.
