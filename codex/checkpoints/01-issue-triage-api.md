# Checkpoint 01 — issue triage API

Date: 2026-10-13. Branch: `codex/checkpoint-1-issue-triage-api` from synchronized `main` (`85f8d18`).

## Delivered

- Java 25, Maven, Spring Boot 4.1.1, Spring AI 2.0.1, PostgreSQL 17, Flyway, and Compose foundation.
- JWT login and role restrictions; owner-scoped issue and triage reads.
- Validated Jev adapter, persistent triage history, review thresholds, and retry-safe failures.
- Spring AI `ChatClient` with configurable local Ollama for editable, persisted draft replies.

## Verification

Environment: Temurin 25.0.4, Maven 3.9.6, Docker 28.3.3, PostgreSQL container 17.11.

| Check | Result |
| --- | --- |
| Focused feature tests | Passed for schema, authentication, issues, Jev adapter, triage, and drafts during implementation. |
| `mvn -ntp -Dmaven.repo.local=.superpowers/sdd/2026-10-10-issue-triage-api/m2 clean verify` | Passed after review fix; 31 tests, 0 failures, packaged JAR. |
| `git diff --check` | Passed before review. |
| Local Compose PostgreSQL | Healthy; Flyway applied migration V1 when packaged API started. |
| Packaged API smoke | Login 200; issue create 201; owner issue read 200 with `UNTRIAGED`. |

Integration tests used Testcontainers PostgreSQL and local Jev/Ollama doubles. No live TypeSafe AI key was supplied, so the live Jev request is unverified. The `ollama` command is unavailable on this machine, so a live draft request is unverified. The adapter and generator behavior are covered by focused tests. The smoke test created demo accounts and one issue in the local Compose database.

## Independent review

An independent reviewer found one Important issue: the Jev adapter accepted category and urgency probability maps whose values did not sum to one. Commit `cc24fb3` adds a tolerance-based check and two malformed-response tests. Both tests failed before the fix and passed afterward. The reviewer rechecked the fix and found no remaining Critical or Important findings. A Minor documentation finding was resolved by labeling the confidence thresholds provisional in the README. The final clean gate passed 31 tests, and `git diff --check` passed before push.
