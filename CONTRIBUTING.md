# Contributing to cloud-itonami-isic-9810

Contributions should preserve the actor's scope: own-account-household-
production tracking-programme back-office coordination only, with
CRITICAL exclusions of welfare/aid-eligibility-determination finalization,
direct field/production-work execution, and programme-enrollment
suspension/termination (see README.md).

- All code must be `.cljc` (portable Clojure, no JVM-only constructs).
- Tests must pass: `clojure -M:test`
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize a welfare/aid-eligibility determination or otherwise stand in
  for a welfare/aid authority.
- Directly execute or perform field/production work (planting, harvest,
  construction, etc.) on a household's behalf.
- Suspend or terminate a household's programme enrollment.

Contributions that cross these boundaries will be rejected.
