# Issue Triage API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a JWT-protected issue API that stores software issues, triages them through Jev, and creates editable reply drafts through Spring AI and local Ollama.

**Architecture:** Spring MVC controllers delegate to issue, triage, and draft services. Jev and Ollama sit behind independent ports; PostgreSQL stores durable state through Spring Data JPA and Flyway. Spring Security validates short-lived JWTs and services enforce ownership.

**Tech Stack:** Java 25, Maven 3.9+, Spring Boot 4.1.1, Spring AI 2.0.1, Spring Security, PostgreSQL 17, Flyway, Docker Compose, Ollama `gemma3:4b`, JUnit 5, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-10-issue-triage-api-design.md`

## Global Constraints

- Java 25, Maven, Spring Boot, Spring AI, Jev, JWT authentication, PostgreSQL, and Docker Compose are required.
- Jev is called through its HTTP API for typed decisions; Spring AI `ChatClient` calls Ollama for text drafts.
- The first version accepts issues through this API and does not import or publish GitHub issues.
- `USER` reads and creates own issues. `MAINTAINER` sees the queue, invokes AI operations, and edits drafts.
- All issue data persists before AI calls; AI failures leave existing data intact and actions retryable.
- Secrets and plaintext passwords are never committed or logged.
- Begin implementation only after the plan is approved. First establish the approved bootstrap commit on remote `main`, fetch, confirm local and remote `main` match, then create `codex/checkpoint-1-issue-triage-api` from synchronized `main`. Do not implement on `main`.
- Before push: run the full applicable local gate, `git diff --check`, and independent review. Fix Critical or Important findings and re-review. Push the checkpoint, confirm three branch SHAs match, merge with `--no-ff`, verify and push `main`, confirm all `main` SHAs match. Do not force-push or delete the checkpoint branch.

## Review Focus

1. A valid token for another owner must yield 404 for issue reads and triage history (Task 3 and Task 5 tests).
2. A stale or malformed JWT must yield 401, and `USER` must get 403 on maintainer routes (Task 2 tests).
3. Jev may return missing keys, unknown choice values, or out-of-range numbers; reject such responses without changing stored triage (Task 4 and Task 5 tests).
4. A provider timeout after a previous successful triage must preserve that result and allow another retry (Task 5 test).
5. Ollama may return blank text, and a draft ID may belong to another issue; reject blank drafts and cross-issue edits (Task 6 tests).

## File map

Use base package `com.tmejs.springaijev`. Keep files grouped by feature:

- `pom.xml`, `compose.yaml`, `.gitignore`, `src/main/resources/application.yml`, `src/main/java/com/tmejs/springaijev/IssueTriageApplication.java`: build and runtime wiring.
- `src/main/resources/db/migration/V1__initial_schema.sql`: users, issues, triage results, drafts and constraints.
- `src/test/resources/application-test.yml`: test-only dummy secrets and AI settings; Testcontainers supplies the PostgreSQL connection.
- `.../auth/`: login, demo-user seeding, token issuing, security configuration.
- `.../issue/`: issue entity/repository/service/controller and request/response records.
- `.../triage/`: typed Jev result, strict validation, HTTP adapter, rules, persistence, controller.
- `.../draft/`: Spring AI adapter, draft persistence, controller and editing.
- `.../common/ApiErrorHandler.java`: consistent errors and safe provider failure mapping.
- Matching `src/test/java/com/tmejs/springaijev/...` tests own each feature; `src/test/resources/application-test.yml` holds test-only settings.
- `README.md` and `codex/checkpoints/01-issue-triage-api.md`: verified setup and checkpoint evidence.

All Java paths abbreviated with `.../` above expand from `src/main/java/com/tmejs/springaijev`; test paths expand from `src/test/java/com/tmejs/springaijev`.

---

### Task 1: Bootable application and durable schema

**Files:** Create `pom.xml`, `compose.yaml`, `.gitignore`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__initial_schema.sql`, `src/main/java/com/tmejs/springaijev/IssueTriageApplication.java`, `src/main/java/com/tmejs/springaijev/issue/Issue.java`, `src/main/java/com/tmejs/springaijev/issue/IssueStatus.java`, `src/main/java/com/tmejs/springaijev/issue/IssueRepository.java`, `src/main/java/com/tmejs/springaijev/auth/AppUser.java`, `src/main/java/com/tmejs/springaijev/auth/AppUserRole.java`, `src/main/java/com/tmejs/springaijev/auth/AppUserRepository.java`; test `src/test/java/com/tmejs/springaijev/SchemaIntegrationTest.java`, `src/test/resources/application-test.yml`.

**Interfaces:** Produce `IssueRepository extends JpaRepository<Issue, UUID>` and `AppUserRepository extends JpaRepository<AppUser, UUID>` with `findByUsername(String)`; `Issue` contains owner, title, description, `UNTRIAGED|TRIAGED|NEEDS_REVIEW`, created/updated timestamps.

- [ ] **Step 1: Write a failing schema test.** With a Testcontainers PostgreSQL container and `@SpringBootTest`, save a user and owned issue, reload them, and assert the owner and `UNTRIAGED` status persisted:

  ```java
  var issue = issues.save(new Issue(user, "Login fails", "The login button returns 500"));
  assertEquals(IssueStatus.UNTRIAGED, issues.findById(issue.getId()).orElseThrow().getStatus());
  ```

- [ ] **Step 2: Run `mvn -Dtest=SchemaIntegrationTest test`; expect compilation or context failure before implementation.**
- [ ] **Step 3: Add Spring Boot parent `4.1.1`, Java release `25`, Spring AI BOM `2.0.1`, and Boot starters `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-flyway`, `spring-ai-starter-model-ollama`, plus PostgreSQL runtime, `flyway-database-postgresql`, `spring-boot-starter-test`, and Testcontainers PostgreSQL. Configure `ddl-auto: validate`, environment-backed PostgreSQL URL/user/password, and a PostgreSQL 17 Compose service with health check. Make Ollama model `gemma3:4b` configurable. The migration creates FK-linked `app_users`, `issues`, `triage_results`, and `reply_drafts`; issues default to `UNTRIAGED`, and no credential is inserted by SQL. `application-test.yml` supplies dummy JWT/Jev secrets; `@ActiveProfiles("test")` and `@DynamicPropertySource` point tests to Testcontainers PostgreSQL.**
- [ ] **Step 4: Run `docker compose up -d postgres` and `mvn -Dtest=SchemaIntegrationTest test`; expect PASS.**
- [ ] **Step 5: Commit only this working foundation: `git commit -m "feat: bootstrap API and PostgreSQL schema"`.**

### Task 2: JWT login and role enforcement

**Files:** Create `.../auth/AuthController.java`, `.../auth/AuthService.java`, `.../auth/JwtService.java`, `.../auth/SecurityConfig.java`, `.../auth/DemoUserInitializer.java`, `.../auth/LoginRequest.java`, `.../auth/LoginResponse.java`; test `src/test/java/com/tmejs/springaijev/auth/AuthApiTest.java`; modify `application.yml`.

**Interfaces:** `AuthService.login(String username, String password): LoginResponse`; JWT subject is persisted user UUID, claim `role` is `USER` or `MAINTAINER`, issuer is `springai-jev`, expiry is 30 minutes. `SecurityConfig` maps that role claim to `ROLE_USER` or `ROLE_MAINTAINER`. `DemoUserInitializer` seeds accounts only when `DEMO_USER_PASSWORD` and `DEMO_MAINTAINER_PASSWORD` are supplied; it uses `PasswordEncoder` and never logs them.

- [ ] **Step 1: Write MockMvc tests for correct login, wrong password, missing token, expired token, and `USER` access to a maintainer-only test controller declared inside the test. Assert 200 with `accessToken`, then 401/401/401/403 respectively.**
- [ ] **Step 2: Run `mvn -Dtest=AuthApiTest test`; expect failed assertions or missing endpoints.**
- [ ] **Step 3: Implement a stateless `SecurityFilterChain`, `BCryptPasswordEncoder`, authentication against hashed database passwords, and `NimbusJwtEncoder`/`NimbusJwtDecoder` using an environment-provided HS256 secret of at least 32 bytes. Validate issuer and timestamps on decode. Permit only `POST /api/v1/auth/login` anonymously; require JWT elsewhere. A token is minted with:**

  ```java
  JwtClaimsSet claims = JwtClaimsSet.builder()
      .issuer("springai-jev").subject(user.getId().toString())
      .issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(30)))
      .claim("role", user.getRole().name()).build();
  ```

- [ ] **Step 4: Run `mvn -Dtest=AuthApiTest test`; expect PASS, including expired-token and role checks.**
- [ ] **Step 5: Commit: `git commit -m "feat: add JWT login and roles"`.**

### Task 3: Issue submission, listing, and ownership

**Files:** Create `.../issue/IssueController.java`, `.../issue/IssueService.java`, `.../issue/CreateIssueRequest.java`, `.../issue/IssueResponse.java`, `.../common/ApiErrorHandler.java`; test `src/test/java/com/tmejs/springaijev/issue/IssueApiTest.java`.

**Interfaces:** `IssueService.create(UUID actorId, CreateIssueRequest): IssueResponse`, `get(UUID actorId, boolean maintainer, UUID issueId): IssueResponse`, `list(UUID actorId, boolean maintainer, IssueStatus filter): List<IssueResponse>`. `CreateIssueRequest` has nonblank title, max 200 characters, nonblank description, max 10,000 characters. `GET /api/v1/issues?status=...` filters after ownership restriction.

- [ ] **Step 1: Write authenticated MockMvc tests: create returns 201 and `UNTRIAGED`; owner reads it; another `USER` gets 404; maintainer reads it; user list excludes another owner's issue; invalid or oversized input gets 400.**
- [ ] **Step 2: Run `mvn -Dtest=IssueApiTest test`; expect missing endpoint failures.**
- [ ] **Step 3: Implement controller and service. Extract actor UUID and role from validated JWT, use repository queries scoped by owner for users, and return 404 for a foreign issue. Add one common JSON error shape `{code,message,retryable}` without stack traces.**

  ```java
  Issue issue = maintainer
      ? issues.findById(issueId).orElseThrow(IssueNotFoundException::new)
      : issues.findByIdAndOwnerId(issueId, actorId).orElseThrow(IssueNotFoundException::new);
  ```

- [ ] **Step 4: Run `mvn -Dtest=IssueApiTest test`; expect PASS.**
- [ ] **Step 5: Commit: `git commit -m "feat: add owned issue API"`.**

### Task 4: Jev adapter and typed response validation

**Files:** Create `.../triage/JevClient.java`, `.../triage/TypeSafeJevClient.java`, `.../triage/JevAssessment.java`, `.../triage/JevProtocolException.java`, `.../triage/JevUnavailableException.java`; test `src/test/java/com/tmejs/springaijev/triage/TypeSafeJevClientTest.java`; modify `application.yml`.

**Interfaces:** `JevClient.assess(String title, String description): JevAssessment`. `JevAssessment` contains model, category choice and distribution/confidence, urgency score and distribution/confidence, sufficient-information probability. Category enum values are `BUG`, `FEATURE`, `QUESTION`. One HTTP call sends `state`, model `jev-latest`, and three typed questions.

- [ ] **Step 1: Write local HTTP stub tests asserting the Bearer API key, exact question IDs `category`, `urgency`, `sufficient_information`, and mapping of a valid response. Add malformed cases: absent answer, unknown category, NaN/out-of-range probability, and 5xx/timeout. Assert protocol errors or unavailable errors as appropriate.**
- [ ] **Step 2: Run `mvn -Dtest=TypeSafeJevClientTest test`; expect missing adapter failures.**
- [ ] **Step 3: Implement with Spring `RestClient` and environment-backed `TYPESAFE_API_KEY`, base URL, and bounded connect/read timeouts. Ask `Choice` for bug/feature/question, `Score` for routine/time-sensitive/critical, and `Noul` for sufficient information. Validate expected types and required keys; probability/confidence values must be finite and in `[0,1]`, urgency score in `[0,2]`, and category must be known before constructing `JevAssessment`. Map HTTP 429/5xx/timeouts to retryable unavailable errors; map invalid JSON to protocol error. Never log key or issue body.**

  ```java
  public interface JevClient {
      JevAssessment assess(String title, String description);
  }
  ```

- [ ] **Step 4: Run `mvn -Dtest=TypeSafeJevClientTest test`; expect PASS without a live Jev key.**
- [ ] **Step 5: Commit: `git commit -m "feat: add validated Jev client"`.**

### Task 5: Triage rules, history, and endpoints

**Files:** Create `.../triage/TriageRules.java`, `.../triage/TriageResult.java`, `.../triage/TriageRepository.java`, `.../triage/TriageService.java`, `.../triage/TriageController.java`, `.../triage/TriageResponse.java`; test `src/test/java/com/tmejs/springaijev/triage/TriageRulesTest.java` and `TriageApiTest.java`; modify `ApiErrorHandler.java`, `application.yml`.

**Interfaces:** `TriageRules.status(JevAssessment): IssueStatus`; `TriageService.run(UUID issueId): TriageResponse`; `history(UUID issueId): List<TriageResponse>`. Config defaults: category confidence `0.70`, sufficient-information probability `0.60`. Store each successful result with model, all three answers/distributions, status and time. JSONB columns hold probability maps using Hibernate's JSON mapping. `TriageResult.summary(): String` provides the category and urgency summary for draft generation. Do not replace prior results.

- [ ] **Step 1: Write rule boundary tests for `0.69`/`0.70` category confidence and `0.59`/`0.60` sufficient information. Write API tests for maintainer success, user 403 on run, owner versus foreign-user history, malformed Jev response, and timeout after an earlier successful result.**
- [ ] **Step 2: Run `mvn -Dtest=TriageRulesTest,TriageApiTest test`; expect failures.**
- [ ] **Step 3: Implement the rule as:**

  ```java
  return assessment.categoryConfidence() < categoryConfidenceThreshold
      || assessment.sufficientInformationProbability() < sufficientInformationThreshold
      ? IssueStatus.NEEDS_REVIEW : IssueStatus.TRIAGED;
  ```

  Call Jev outside a database transaction; after a valid answer, persist a new result and update issue status in one transaction. A failed call leaves the issue and last result untouched. `GET /issues/{id}/triage` checks ownership and returns result history. Map provider failures to a stable 502 error with `retryable=true`; malformed provider output is also 502 but `retryable=false`.
- [ ] **Step 4: Run `mvn -Dtest=TriageRulesTest,TriageApiTest test`; expect PASS.**
- [ ] **Step 5: Commit: `git commit -m "feat: persist Jev triage and rules"`.**

### Task 6: Spring AI draft generation and editing

**Files:** Create `.../draft/ReplyGenerator.java`, `.../draft/OllamaReplyGenerator.java`, `.../draft/ReplyDraft.java`, `.../draft/ReplyDraftRepository.java`, `.../draft/DraftService.java`, `.../draft/DraftController.java`, `.../draft/DraftResponse.java`, `.../draft/EditDraftRequest.java`; test `src/test/java/com/tmejs/springaijev/draft/OllamaReplyGeneratorTest.java` and `DraftApiTest.java`; modify `ApiErrorHandler.java`, `application.yml`.

**Interfaces:** `ReplyGenerator.generate(Issue issue, TriageResult latest): String`; `DraftService.generate(UUID issueId, UUID maintainerId): DraftResponse`; `edit(UUID issueId, UUID draftId, String text): DraftResponse`. Configure Spring AI Ollama model `gemma3:4b` and `http://localhost:11434`. Draft creation requires an existing successful triage result.

- [ ] **Step 1: Write a generator test with a fake `ChatModel` verifying the prompt contains issue title/description, category and urgency, and instructions to avoid unsupported claims. Write API tests for successful stored draft, listing that draft, blank model output, unavailable Ollama, user 403, edit persistence, and a draft ID from a different issue returning 404.**
- [ ] **Step 2: Run `mvn -Dtest=OllamaReplyGeneratorTest,DraftApiTest test`; expect failures.**
- [ ] **Step 3: Build `ChatClient` from the configured Spring AI builder. Send a system instruction that this is an editable maintainer draft, never claim a fix or promise delivery, and ask for a concise response. Send only the saved issue and latest triage summary in the user message. Reject null/blank generated text; save each successful draft with creator and timestamps. Scope edit lookup by both issue ID and draft ID. Map Ollama outage to retryable 502 without saving a draft.**

  ```java
  String text = chatClient.prompt()
      .system("Draft a concise maintainer reply. Do not claim a fix or promise a feature.")
      .user(issue.getTitle() + "\n" + issue.getDescription() + "\n" + latest.summary())
      .call().content();
  ```

- [ ] **Step 4: Run `mvn -Dtest=OllamaReplyGeneratorTest,DraftApiTest test`; expect PASS without a running Ollama process.**
- [ ] **Step 5: Commit: `git commit -m "feat: add Spring AI reply drafts"`.**

### Task 7: Runnable demonstration, full gate, and checkpoint

**Files:** Modify `README.md`; create `.env.example` and `codex/checkpoints/01-issue-triage-api.md`; modify tests only for concrete failures found by the complete gate.

**Interfaces:** README documents Java 25, Maven, PostgreSQL Compose startup, `ollama pull gemma3:4b`, environment variables, demo-user credentials supplied locally, `mvn spring-boot:run`, and `curl` requests for login, issue submission, triage, history, draft, edit. It distinguishes live Jev access from stubbed tests.

- [ ] **Step 1: Write the README walkthrough and `.env.example` with variable names only, including `JWT_SECRET`, database settings, `TYPESAFE_API_KEY`, `DEMO_USER_PASSWORD`, and `DEMO_MAINTAINER_PASSWORD`. Use a valid sample issue and exact endpoint payloads.**
- [ ] **Step 2: Run `mvn clean verify` with Docker available; expect all unit, API, adapter, and PostgreSQL tests to pass without live Jev/Ollama. Run `git diff --check`. If a required check is unavailable, record its reason; fix actual failures and rerun the affected check and full gate.**
- [ ] **Step 3: Run a local smoke demonstration against PostgreSQL and Ollama when available. If Jev access is available, use one live request; otherwise label live Jev unverified. Record commands, results, versions, and remaining limits in `codex/checkpoints/01-issue-triage-api.md`.**
- [ ] **Step 4: Request independent review of the checkpoint diff. Resolve all Critical or Important findings, rerun focused tests and the full gate, and record review/fix evidence in the checkpoint file. Commit final documentation and verification evidence separately: `git commit -m "docs: verify issue triage checkpoint"`.**
- [ ] **Step 5: Fetch and inspect the remote again. If `main` remains synchronized, push `codex/checkpoint-1-issue-triage-api`, compare local/tracking/remote SHA, merge it into `main` with `git merge --no-ff`, rerun the applicable gate on merged `main`, push `main`, and compare all three `main` SHAs. Preserve the checkpoint branch.**

## Source references checked for this plan

- Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x: https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI Ollama starter and model configuration: https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- Jev endpoint and typed questions: https://docs.typesafe.ai/api
- Spring Security custom JWT resource server support: https://docs.spring.io/spring-security/reference/servlet/oauth2/
- Ollama `gemma3:4b` model: https://ollama.com/library/gemma3
