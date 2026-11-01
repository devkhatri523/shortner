# Agentic URL Shortener — Java / Spring Boot / Spring AI

An interview-sized Maven multi-module demo with **actual OpenAI LLM calls**, a URL shortener, PostgreSQL, Redis, AES-256-GCM encrypted mappings, HMAC-SHA256 checksums, and a **code → test → repair → retest → human review** loop.

**All Java package names start with `com.url.shortner`** (spelling exactly as requested).

## Project layout

```text
agentic-url-shortener-llm-springboot/
├── pom.xml                        # Maven reactor, Spring Boot 3.5.8, Spring AI 1.1.2
├── docker-compose.yml             # PostgreSQL + Redis
├── .env.example                   # Example environment config; NO real secrets
├── url-shortener-service/
│   └── src/main/java/com/url/shortner/
│       ├── ShortenerApplication.java
│       ├── LinkController.java
│       ├── LinkService.java
│       ├── LinkInsertionService.java
│       ├── RedisMappingCache.java
│       ├── MappingCryptoService.java
│       └── ...
└── agent-orchestrator/
    └── src/main/java/com/url/shortner/orchestrator/
        ├── OrchestratorApplication.java
        ├── agent/
        │   ├── RequirementAgent.java  # REAL ChatClient call
        │   ├── PlanningAgent.java     # REAL ChatClient call
        │   ├── CodingAgent.java       # REAL ChatClient call: JSON Java files
        │   └── ReviewAgent.java       # REAL ChatClient call
        ├── workflow/
        │   ├── WorkflowEngine.java    # Governs retries, state, human approval
        │   └── WorkflowStore.java     # Atomic JSON checkpoints on disk
        ├── workspace/
        │   ├── WorkspaceManager.java  # Isolated copy of URL shortener module
        │   └── SourceFileTool.java    # Validates proposed changes before writing
        ├── test/
        │   ├── DockerMavenTestExecutor.java  # Execute Maven in offline sandbox
        │   └── SurefireReportReader.java     # Parse JUnit results
        └── controller/WorkflowController.java
```

## Prerequisites

- JDK **21**, Maven **3.9+**, Docker Desktop / Docker Compose, OpenSSL (or any secure random generator).
- A working **OpenAI API key** with API access and billing configured. ChatGPT subscription does not automatically grant API access.
- Network access when initially downloading Maven and Docker image dependencies, and for contacting the LLM API from the orchestrator.

## 1) Run PostgreSQL and Redis

From the project root:

```bash
cp .env.example .env
# Add two separate random secret keys (32 bytes each) for encrypted link mappings.
cat >> .env <<EOF_KEYS
APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
APP_HMAC_KEY=$(openssl rand -base64 32)
EOF_KEYS
# Edit .env with your own POSTGRES_PASSWORD and other settings as needed.
docker compose up -d --wait
```

The compose file only starts **PostgreSQL** and **Redis**. The Spring Boot modules run on your host via Maven.

## 2) Launch URL shortener service (port 8080)

In a shell in the project root:

```bash
set -a
source .env
set +a
mvn -pl url-shortener-service spring-boot:run
```

Try it:

```bash
curl -X POST http://localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/page"}'
# Take the code returned and:
curl -i http://localhost:8080/<code>
curl http://localhost:8080/api/links/<code>/stats
```

### URL service overview

- `ShortCodeGenerator` makes secure random Base62 codes.
- `LinkInsertionService` inserts in **independent transactions**, and Spring Retry handles **only code collisions**; retry count/backoff are env-configurable.
- `MappingCryptoService` encrypts URL mappings with AES-256-GCM and independently calculates an HMAC-SHA256 integrity checksum over the code plus ciphertext. Both Redis and Postgres store encrypted URLs.
- `RedisMappingCache` performs cache-aside reads and tolerates Redis outages.
- `LinkRepository.incrementClicks()` increments the database counter atomically for every redirect, including cache hits.
- `ShortenerProperties.baseUrl` derives the returned short link from `SHORTENER_BASE_URL` (no hardcoded localhost in Java).

## 3) Prepare Maven's offline cache for the agent test sandbox

The coding agent generates **untrusted Java code**. The testing agent therefore launches Maven in a locked-down Docker container with networking **disabled**. It can only resolve dependencies already present in the Maven cache.

First warm the cache in your host environment:

```bash
mvn -B -ntp -pl url-shortener-service test
mvn -B -ntp -pl url-shortener-service dependency:go-offline
# Pull the execution container while network is available:
docker pull maven:3.9.9-eclipse-temurin-21
```

Make sure your host user has permission to write to the isolated workspace from the Docker container. On Linux, set `AGENT_DOCKER_USER="$(id -u):$(id -g)"` in your environment if your uid/gid isn't `1000:1000`. On Windows, review bind-mount permissions.

**Important:** If offline Maven cannot resolve plugins/dependencies inside Docker, the workflow **safe-stops** and records a test infrastructure error; it never treats that as a passing test. You may need to warm the cache further for your OS/Maven setup.

## 4) Start the real LLM-backed agent orchestrator (port 8081)

In another terminal at the project root:

```bash
set -a
source .env
set +a
export OPENAI_API_KEY="YOUR_API_KEY_HERE"   # Do not commit or paste key in requests
export AGENT_SOURCE_ROOT="$(pwd)"             # Must point to this Maven reactor root
export AGENT_DOCKER_USER="$(id -u):$(id -g)" # Linux example
mvn -pl agent-orchestrator spring-boot:run
```

The model is configured using `spring.ai.openai.chat.options.model` and `OPENAI_MODEL` (default `gpt-4.1-mini`). The dependency is `org.springframework.ai:spring-ai-starter-model-openai`. **Without the API key, the LLM agent cannot run.** No fake or hardcoded model answers are used.

### API workflow walkthrough

**A. Submit a requirement.** This makes real LLM requests to RequirementAgent and PlanningAgent.

```bash
curl -s -X POST http://localhost:8081/api/workflows \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"Add URL expiration support. Expired links must return HTTP 410, with unit tests."}'
```

The response contains an `id`, `analysis`, `plan`, graph `tasks`, and `status: WAITING_PLAN_APPROVAL` (or `NEEDS_HUMAN` on an LLM failure). Save the returned `id` as `WORKFLOW_ID`.

**B. Human approves the plan.**

```bash
curl -X POST "http://localhost:8081/api/workflows/$WORKFLOW_ID/plan-decision" \
  -H 'Content-Type: application/json' -d '{"approve":true}'
```

**C. Execute asynchronously.**

```bash
curl -X POST "http://localhost:8081/api/workflows/$WORKFLOW_ID/execute"
```

This executes the following **real** pipeline:

1. Copy the URL shortener source into a separate directory (not the original repo).
2. CodingAgent calls the configured LLM and returns JSON describing **complete Java files** to write.
3. SourceFileTool enforces strict path allowlists and blocks modifications to existing tests, POMs, YAML, secrets and files outside the workspace.
4. DockerMavenTestExecutor runs Maven/JUnit offline with no network, caps/capability restrictions, and a timeout.
5. SurefireReportReader extracts test counts and failures from XML. **Maven is the authority for pass/fail, not the LLM.**
6. If tests fail, pass the source and actual error report back to CodingAgent for a fix. All retries are controlled by `AGENT_MAX_FIX_ATTEMPTS` (default 3; attempts are *repair attempts* following initial implementation).
7. On success, ReviewAgent makes a final real LLM review; the workflow stops at `WAITING_REVIEW` for a person to approve.
8. If retries are exhausted, tests cannot run, or an agent returns an unsafe patch, status becomes `NEEDS_HUMAN`. No automatic merge or deployment occurs.

**D. Poll status** (after execution starts):

```bash
curl -s "http://localhost:8081/api/workflows/$WORKFLOW_ID"
```

**E. Examine the proposed Java code.** The workflow response gives `workspace`, a real path under the isolated `AGENT_WORKSPACE_ROOT`. Open that workspace in IntelliJ or compare it with the original source. The original checkout has **not** been modified.

**F. Human final approval** (allowed only when tests pass):

```bash
curl -X POST "http://localhost:8081/api/workflows/$WORKFLOW_ID/review-decision" \
  -H 'Content-Type: application/json' -d '{"approve":true}'
```

`APPROVED` means **approved for manual merge**, not automatically merged or deployed.

## 5) Settings

| Environment | Description | Default |
|---|---|---|
| `OPENAI_API_KEY` | Required OpenAI API key | none |
| `OPENAI_MODEL` | Spring AI chat model | `gpt-4.1-mini` |
| `AGENT_SOURCE_ROOT` | Absolute reactor path for creating workspaces | `.` (set explicitly) |
| `AGENT_WORKSPACE_ROOT` | Where isolated workspaces and audit JSON live | JVM temp directory |
| `AGENT_MAX_FIX_ATTEMPTS` | Maximum code-fix cycles after initial test | `3` |
| `AGENT_TEST_TIMEOUT` | Sandbox test timeout | `180s` |
| `AGENT_TEST_IMAGE` | Maven Docker image for offline tests | `maven:3.9.9-eclipse-temurin-21` |
| `AGENT_DOCKER_USER` | uid:gid in Docker to write workspace | `1000:1000` |
| `AGENT_MAVEN_CACHE` | Host Maven dependency repository (read-only mount) | `~/.m2/repository` |
| `SHORTENER_BASE_URL` | Configurable public URL prefix | `http://localhost:8080` |
| `SHORTENER_CODE_MAX_ATTEMPTS` | Max short code insert attempts | `5` |
| `SHORTENER_CODE_RETRY_BACKOFF` | Collision retry delay | `100ms` |
| `SHORTENER_CACHE_TTL` | Redis cache expiration | `24h` |
| `APP_ENCRYPTION_KEY`, `APP_HMAC_KEY` | Independent Base64 32-byte secrets | required |

## 6) Tests

```bash
mvn test
```

- `url-shortener-service` tests: encryption/tampering, URL validation, retry exhaustion/recovery, Redis cache hit/corrupt cached mapping handling.
- `agent-orchestrator` tests: malicious-path prevention, existing test protection, Surefire parsing, mock LLM flow (tests fail → code fix → tests pass → review), maximum retry safe-stop.
- Real LLM execution tests require your API key. Docker/Redis/PostgreSQL tests require running the tools and services; no live API calls are made by unit tests.

## Scope limits and safety notes

This is a **working design and source prototype**, not a production-ready general-purpose agent platform:

- Explicit dependency graph and two human gates exist, but graph scheduling is currently **sequential**; no parallel agent execution or dynamic re-planning is implemented yet.
- Agent checkpoints are JSON files (durable locally), not multi-node transactional workflow persistence. Only single-process usage is supported.
- It does **not** merge/commit/deploy code or access live production services.
- Docker tests have no outbound network and limited CPU/RAM/capabilities, but Docker itself must be properly secured; do not expose the Docker daemon to untrusted users. Local test runners can execute arbitrary code embedded in agent output, so don't replace the sandbox with unsandboxed `mvn test`.
- SourceFileTool blocks edits outside approved Java source locations and overwriting existing tests. Prompt injection in repository files remains a potential risk, so human review is still required.
- No built-in API authentication, rate limiting, distributed locks, compliance audit store, or automatic rollback of merged code. Orchestrator binds to `127.0.0.1` by default.
- Review artifacts are the isolated workspace + audit JSON + JUnit reports; they are not automatically Git commits or PRs.
- URL shortener itself has no anti-phishing/malware screening or authenticated stats endpoint; do not expose it to the public without hardening.
- Rotating encryption/HMAC keys requires a key versioning and migration strategy; changing the keys without migration will make old URLs undecryptable.

**Note about versions:** Spring AI **1.1.2** targets Spring Boot **3.5.x**. This project aligns on Boot **3.5.8** and Java 21.
