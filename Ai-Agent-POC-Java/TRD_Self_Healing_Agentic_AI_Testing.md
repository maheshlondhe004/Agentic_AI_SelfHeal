# Technical Requirements Document (TRD)
## Self-Healing Agentic AI for API and UI Testing — POC

**Version:** 1.0  
**Status:** Draft  
**Date:** April 10, 2026  
**Author:** Platform / QA Engineering

---

## 1. System Architecture Overview

The system is composed of three primary modules orchestrated by a central **Test Runner**:

```
┌─────────────────────────────────────────────────────────────┐
│                        TEST RUNNER                          │
│              (Java — Orchestration Layer)                   │
└────────────┬──────────────────────┬────────────────────────┘
             │                      │
     ┌───────▼──────┐      ┌────────▼────────┐
     │  API Module  │      │   UI Module     │
     │ (HttpClient/ │      │  (Playwright    │
     │ RestAssured) │      │    Java)        │
     └───────┬──────┘      └────────┬────────┘
             │                      │
     ┌───────▼──────────────────────▼────────┐
     │            AI AGENT LAYER             │
     │    (LLM API — Claude / GPT-4)         │
     └───────────────────┬───────────────────┘
                         │
             ┌───────────▼───────────┐
             │   PERSISTENCE LAYER   │
             │  (JSON / YAML files)  │
             └───────────────────────┘
```

---

## 2. Technology Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 17+ |
| UI Automation | Playwright for Java | 1.43+ |
| API Testing | Apache HttpClient / REST-assured | 5.x / 5.x |
| AI Agent | Anthropic Claude API (or OpenAI GPT-4) | claude-sonnet-4 |
| JSON Processing | Jackson / Gson | 2.x |
| YAML Processing | SnakeYAML | 2.x |
| Build Tool | Maven or Gradle | 3.9+ / 8.x |
| Test Framework | TestNG or JUnit 5 | 7.x / 5.x |
| Logging | SLF4J + Logback | 2.x |

---

## 3. Module Specifications

### 3.1 API Testing Module

#### 3.1.1 Request Execution

```java
// ApiTestExecutor.java
public class ApiTestExecutor {

    private final String endpoint;
    private final String method;
    private final Map<String, String> headers;
    private final String requestBody;

    public ApiResponse execute() {
        // Use Apache HttpClient or REST-assured
        // Return status code + response body string
    }
}
```

**Configuration** — stored in `api-config.json`:
```json
{
  "endpoint": "https://api.example.com/v1/resource",
  "method": "GET",
  "headers": {
    "Accept": "application/json"
  },
  "expectedResponseFile": "expected/api-response.json"
}
```

#### 3.1.2 Status Code Validation

- Strict check: `statusCode == 200`
- On failure: log error with actual status, halt execution, do NOT invoke AI Agent
- On success: proceed to response body validation

#### 3.1.3 Response Body Validation

- Load expected JSON from `expected/api-response.json`
- Deep-compare actual vs expected using Jackson `ObjectMapper`
- Generate a structured diff (keys added / removed / changed)
- If diff is empty → pass, proceed to UI
- If diff is non-empty → invoke AI Agent (Section 4.1)

---

### 3.2 UI Testing Module (Playwright Java)

#### 3.2.1 Browser Initialization

```java
// PlaywrightDriver.java
public class PlaywrightDriver {
    private Playwright playwright;
    private Browser browser;
    private Page page;

    public void init() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(false)
        );
        page = browser.newPage();
    }

    public Page getPage() { return page; }

    public void teardown() {
        browser.close();
        playwright.close();
    }
}
```

#### 3.2.2 Locator Registry

Locators are stored externally in `locators/locator-registry.yaml`:

```yaml
loginPage:
  usernameField: "#username"
  passwordField: "#password"
  submitButton: "button[type='submit']"
homePage:
  welcomeBanner: ".welcome-message"
  profileLink: "a[data-testid='profile']"
```

Loaded at startup by `LocatorRegistry.java` and injected into test steps.

#### 3.2.3 Action Execution with Heal Guard

Every UI action is wrapped in a `HealGuard` utility:

```java
// HealGuard.java
public class HealGuard {

    private final AiAgentClient aiAgent;
    private final LocatorRegistry registry;
    private final Page page;

    public void click(String locatorKey) {
        String locator = registry.get(locatorKey);
        try {
            page.locator(locator).click();
        } catch (PlaywrightException e) {
            // Locator failed — invoke AI Agent
            String healedLocator = aiAgent.healLocator(
                locatorKey, locator, page.content()
            );
            page.locator(healedLocator).click();
            registry.update(locatorKey, healedLocator); // persist
        }
    }

    // fill(), getText(), assertVisible() follow the same pattern
}
```

---

### 3.3 AI Agent Layer

#### 3.3.1 API Healing Agent

**Trigger:** Response body diff is detected.

**Input Payload to LLM:**
```json
{
  "task": "api_healing",
  "description": "API response body has changed. Determine if the change is valid and return updated expected JSON.",
  "expected": { /* previous expected body */ },
  "actual": { /* current actual body */ },
  "diff": { /* structured diff */ }
}
```

**System Prompt:**
```
You are a QA test automation AI agent.
You are given an expected API response and an actual API response with a diff.
Your task is to decide if the change represents a valid evolution of the API (e.g., new optional fields, changed default values) or a genuine regression.
If valid: return the updated expected JSON only. No explanation.
If regression: return {"status": "regression", "reason": "<explanation>"}.
```

**Response Handling:**
- Parse JSON from LLM response
- If `status == "regression"` → halt and log
- Otherwise → write returned JSON to `expected/api-response.json`

#### 3.3.2 Locator Healing Agent

**Trigger:** `PlaywrightException` on locator resolve.

**Input Payload to LLM:**
```json
{
  "task": "locator_healing",
  "description": "A Playwright locator failed. Find the correct locator from the DOM.",
  "failedLocator": "#old-username",
  "locatorKey": "loginPage.usernameField",
  "domSnapshot": "<html>...</html>"
}
```

**System Prompt:**
```
You are a QA automation AI agent specializing in Playwright locators.
You are given a DOM snapshot and a locator that failed to find an element.
Your task is to identify the best alternative locator for the element described by the locator key.
Prefer: data-testid > aria-label > id > CSS class > XPath.
Return ONLY the locator string. No explanation, no code block.
```

**Response Handling:**
- Trim and sanitize the returned string
- Retry action with new locator
- On success: persist to `locators/locator-registry.yaml`
- On second failure: log error, mark step as failed, continue remaining steps

#### 3.3.3 AI Agent Client

```java
// AiAgentClient.java
public class AiAgentClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-sonnet-4-20250514";
    private final String apiKey;

    public String healLocator(String key, String failedLocator, String domSnapshot) {
        String userMessage = buildLocatorHealingPrompt(key, failedLocator, domSnapshot);
        return callLlm(LOCATOR_SYSTEM_PROMPT, userMessage);
    }

    public String healApiResponse(String expected, String actual, String diff) {
        String userMessage = buildApiHealingPrompt(expected, actual, diff);
        return callLlm(API_SYSTEM_PROMPT, userMessage);
    }

    private String callLlm(String systemPrompt, String userMessage) {
        // Build request body (JSON)
        // POST to API_URL with Authorization: x-api-key header
        // Parse response content[0].text
        // Return raw string
    }
}
```

---

### 3.4 Persistence Layer

#### 3.4.1 Expected API Response

- File: `expected/api-response.json`
- Written by `ApiHealingService.java` after AI confirmation
- Versioned with a timestamp suffix in `expected/history/` for rollback

#### 3.4.2 Locator Registry

- File: `locators/locator-registry.yaml`
- Updated in-memory during test run by `LocatorRegistry.java`
- Flushed to disk after all test steps complete (Section 3.2.3)
- Original backed up to `locators/history/` before each write

#### 3.4.3 Execution Log

- File: `logs/execution-<timestamp>.json`
- Structured log entries:

```json
[
  {
    "timestamp": "2026-04-10T10:00:00Z",
    "step": "API_STATUS_CHECK",
    "status": "PASS",
    "detail": "Status 200 received"
  },
  {
    "timestamp": "2026-04-10T10:00:01Z",
    "step": "API_BODY_VALIDATION",
    "status": "HEALED",
    "detail": "AI updated expected: field 'version' changed 1.0 → 1.1"
  },
  {
    "timestamp": "2026-04-10T10:00:15Z",
    "step": "UI_LOCATOR_HEAL",
    "status": "HEALED",
    "locatorKey": "loginPage.usernameField",
    "oldLocator": "#username",
    "newLocator": "[data-testid='username-input']"
  }
]
```

---

## 4. Execution Flow — Technical Detail

```
TestRunner.run()
    │
    ├─ ApiTestExecutor.execute()
    │       └─ HTTP GET → ApiResponse(statusCode, body)
    │
    ├─ if statusCode != 200 → throw ApiHealthException → STOP
    │
    ├─ ResponseValidator.validate(expected, actual)
    │       └─ if diff != empty
    │               └─ AiAgentClient.healApiResponse(expected, actual, diff)
    │                       └─ write healed JSON to expected/api-response.json
    │
    ├─ PlaywrightDriver.init()
    │
    ├─ for each TestStep in testPlan:
    │       └─ HealGuard.execute(step)
    │               ├─ try: page.locator(locator).<action>()
    │               └─ catch PlaywrightException:
    │                       └─ AiAgentClient.healLocator(key, locator, dom)
    │                               └─ retry with healedLocator
    │                               └─ LocatorRegistry.update(key, healedLocator)
    │
    ├─ LocatorRegistry.flush()  → locators/locator-registry.yaml
    ├─ Logger.flush()           → logs/execution-<ts>.json
    └─ PlaywrightDriver.teardown()
```

---

## 5. Project Structure

```
self-healing-poc/
├── src/
│   └── main/java/com/poc/selfhealing/
│       ├── api/
│       │   ├── ApiTestExecutor.java
│       │   ├── ResponseValidator.java
│       │   └── ApiHealingService.java
│       ├── ui/
│       │   ├── PlaywrightDriver.java
│       │   ├── HealGuard.java
│       │   └── LocatorRegistry.java
│       ├── agent/
│       │   └── AiAgentClient.java
│       ├── persistence/
│       │   ├── ExpectedResponseStore.java
│       │   └── ExecutionLogger.java
│       └── runner/
│           └── TestRunner.java
├── config/
│   └── api-config.json
├── expected/
│   ├── api-response.json
│   └── history/
├── locators/
│   ├── locator-registry.yaml
│   └── history/
├── logs/
├── pom.xml
└── README.md
```

---

## 6. Configuration

All runtime configuration in `config/application.properties`:

```properties
# API
api.endpoint=https://api.example.com/v1/resource
api.method=GET
api.expected.file=expected/api-response.json

# UI
ui.base.url=https://app.example.com
ui.headless=false
ui.timeout.ms=10000

# AI Agent
ai.provider=anthropic
ai.model=claude-sonnet-4-20250514
ai.api.key=${ANTHROPIC_API_KEY}
ai.max.tokens=1000
ai.timeout.seconds=15

# Locators
locator.registry.file=locators/locator-registry.yaml

# Logging
log.output.dir=logs/
```

---

## 7. Error Handling Strategy

| Error Condition | Action |
|---|---|
| API status != 200 | Halt execution; log; do not invoke AI |
| AI Agent timeout on API heal | Log warning; use existing expected data; continue |
| AI returns regression signal | Halt execution; log regression details |
| Locator heal fails twice | Mark step FAILED; log; continue to next step |
| AI Agent timeout on locator heal | Mark step FAILED; log; continue |
| File write failure (persistence) | Log error; do not crash; healing still applied in-memory |

---

## 8. Security Considerations

- AI API key loaded from environment variable `ANTHROPIC_API_KEY` — never hardcoded
- DOM snapshot sent to LLM must be sanitized: strip any PII or session tokens before sending
- Expected response files must not contain production credentials

---

## 9. Testing the POC Itself

| Test Type | Approach |
|---|---|
| API heal unit test | Mock LLM response; assert expected JSON is updated |
| Locator heal unit test | Mock PlaywrightException + LLM response; assert retry succeeds |
| Integration test | Use a local mock server (WireMock) for API; use a local HTML page for UI |
| End-to-end test | Run against a real staging environment |

---

## 10. Constraints and Limitations (POC)

- DOM snapshot size: if page DOM exceeds ~100KB, truncate to visible viewport area before sending to LLM to stay within token limits
- LLM non-determinism: healed locators/expected data should be reviewed post-run by a QA engineer
- No concurrent test execution — sequential only in POC phase
- Healing history is file-based — no database in this phase
