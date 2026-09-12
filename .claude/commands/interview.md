---
description: Document a Java/Spring Boot interview topic and apply it to a practice project
argument-hint: [subject-area] <interview topic or question>
---

Use the `spring-boot-interview` agent to handle this topic: **$ARGUMENTS**

Produce both artifacts the agent is defined to produce — the study document under
`back-end/spring-boot/practice/docs/interview/<topic>/` and the applied code in the routed
practice project, with the back-link comments and the index row. Report the compile result.

If the input names a subject area (`spring-core`, `database`, `aws`, …), file the document in
that folder. Otherwise infer the folder, reuse an existing one where it fits, and say which you
picked.

Two requirements override everything else:

1. **Plain, short writing everywhere** — in the markdown and in the code comments alike. One or
   two sentences per point, everyday words, one fact per sentence. Keep the real technical names
   (`@Transactional`, `LAZY`) but say the rest the way you'd say it to a teammate. No padding.
2. **Production-grade examples only** — real domain objects from the repo, the failure path
   handled, no `foo`/`bar` toys, no unbounded queries. Review your own code for correctness and
   cost before reporting, the way a senior engineer reviews a PR.

If `$ARGUMENTS` is empty, ask which topic to cover before doing anything else.
