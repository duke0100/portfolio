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

Keep the writing plain and short — everyday words, real technical names explained on first use,
no padding.

If `$ARGUMENTS` is empty, ask which topic to cover before doing anything else.
