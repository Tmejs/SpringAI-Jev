# Issue triage API design

Date: 2026-10-10
Status: Approved in conversation; implementation has not started.

## Purpose and success criteria

Build a small, explainable portfolio API with Java 25, Maven, Spring Boot, Spring AI, Jev, JWT authentication, PostgreSQL, and Docker Compose. Users submit software issues through this API. Jev makes narrow, typed triage decisions. Java rules turn those decisions into a review status. A maintainer can ask Spring AI and a local Ollama model for an editable reply draft. The first version does not import issues or post replies to GitHub.

Success means a reviewer can run the API locally, log in as a user or maintainer, submit an issue, inspect a stored Jev triage result and its uncertainty, retry unavailable AI operations, and generate and edit a reply draft. Tests must exercise the workflow without live AI services. A live Jev demonstration requires TypeSafe early access and an API key.

## Components and boundaries

- A Spring MVC REST API validates requests and enforces JWT authentication and role or ownership checks.
- An issue service owns issue lifecycle and persistence. It calls a triage service and a draft service through separate interfaces.
- A Jev HTTP adapter sends the issue title and description as state to TypeSafe's `POST /v1/systemone` endpoint. It maps typed answers, model identifier, and probabilities into application records. The API key stays server side.
- A draft service uses Spring AI `ChatClient` with a locally running Ollama chat model. It receives the saved issue and triage result and returns text for a maintainer to review.
- PostgreSQL stores users, issues, triage attempts/results, and reply drafts. Flyway owns schema changes. Docker Compose starts PostgreSQL; Ollama runs locally and is configured through Spring AI properties.

Jev is a structured decision service, not a chat model. It is integrated through its HTTP API. Spring AI is used for text generation through `ChatClient`; it does not make triage decisions.

## Authentication and authorization

The API has two seeded demonstration roles. `USER` can create and read their own issues. `MAINTAINER` can read the full queue, run or retry triage, create and edit drafts, and inspect all results. Login accepts a seeded account's credentials and returns a short-lived signed JWT. Passwords are stored as hashes; the JWT signing secret and seeded credentials are supplied through local configuration or environment variables and are not committed. The first version has no public registration or refresh-token flow.

All issue endpoints require a valid JWT. A missing or invalid token returns 401; insufficient role returns 403. Reading an issue owned by another user returns 404 to avoid revealing its existence.

## Issue and AI workflow

1. `USER` submits a title and description. The API validates length and nonblank content, saves the issue with status `UNTRIAGED`, and returns its identifier.
2. `MAINTAINER` invokes triage. One Jev call asks three independent questions against the issue state:
   - `Choice`: bug report, feature request, or question.
   - `Score`: urgency using ordered levels `routine`, `time-sensitive`, and `critical`, with descriptions in the request rubric.
   - `Noul`: whether the issue contains enough information for a maintainer to act.
3. The API stores the model identifier, returned answers and distributions, and the rule outcome. Initial configurable rules flag `NEEDS_REVIEW` when category confidence is below 0.70 or the probability of sufficient information is below 0.60; otherwise they set `TRIAGED`. Urgency is stored for display and does not automatically trigger an external action. These thresholds are provisional and must be described as such in the README until tested against a labeled issue set.
4. `MAINTAINER` requests a reply draft for a saved triage result. Spring AI passes the issue and triage summary to Ollama with instructions to avoid asserting that a bug is fixed or a feature is promised. The draft is stored and may be edited by a maintainer. Generating a draft never changes the triage result and never publishes a reply.

An AI outage leaves the issue accessible. A failed triage request does not replace an earlier successful result; the maintainer can retry. A failed Ollama request does not create a draft. Provider errors are returned as a stable API error with a retryable indication, without leaking credentials or raw provider internals. Each successful triage attempt is retained so a later retry does not erase earlier evidence.

## Proposed HTTP surface

All paths below are prefixed with `/api/v1`.

| Method and path | Access | Purpose |
| --- | --- | --- |
| `POST /auth/login` | Public | Exchange seeded credentials for a JWT |
| `POST /issues` | USER or MAINTAINER | Submit an issue |
| `GET /issues` | USER or MAINTAINER | List own issues, or all issues for a maintainer |
| `GET /issues/{id}` | Owner or MAINTAINER | Read an issue and its latest triage status |
| `POST /issues/{id}/triage` | MAINTAINER | Run or retry Jev triage |
| `GET /issues/{id}/triage` | Owner or MAINTAINER | Inspect successful triage attempts and uncertainty |
| `POST /issues/{id}/drafts` | MAINTAINER | Generate a reply draft using Spring AI and Ollama |
| `GET /issues/{id}/drafts` | MAINTAINER | List saved drafts |
| `PATCH /issues/{id}/drafts/{draftId}` | MAINTAINER | Edit a draft |

The maintainer's review queue is `GET /issues?status=NEEDS_REVIEW` or `?status=UNTRIAGED`. Responses include stable IDs, timestamps, and typed fields. The API uses standard 400, 401, 403, 404, and 502-style responses as appropriate, with a consistent error body.

## Data and security details

Store an issue's owner, title, description, status, and timestamps. Store each successful triage attempt with its model, three answers, probability distributions, rule outcome, and timestamp. Store each draft with its issue, text, creator, and timestamps. Never store a Jev API key, Ollama credential, JWT signing secret, or plaintext password in the database. Limit issue text size before sending it to a model and do not log issue descriptions or prompt bodies by default.

JWT authorization is enforced at the API boundary and rechecked for issue ownership in the service layer. Model output is treated as untrusted input: validate expected answer keys, types, ranges, and enum values before persisting or applying rules. Generated drafts are displayed as suggestions, not authoritative status updates.

## Verification and portfolio demonstration

- Unit tests cover rule boundaries, unexpected Jev responses, and draft prompt construction.
- API and persistence tests cover login, 401/403/404 behavior, owner isolation, issue creation, triage history, and draft editing.
- Adapter tests use a local HTTP stub for TypeSafe; draft tests use a fake chat model or service. The complete local gate must run without live AI keys or Ollama.
- Manual demonstration instructions cover PostgreSQL startup, Ollama setup, environment variables, sample credentials, API requests, and optional live Jev execution.
- The README reports only features that have actually been implemented and verified. Dependency versions and a specific Ollama model will be pinned and verified in the implementation plan.

## Sources

- TypeSafe introduction: https://docs.typesafe.ai/introduction
- TypeSafe API reference: https://docs.typesafe.ai/api
- Spring AI ChatClient: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Spring AI Ollama chat: https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
