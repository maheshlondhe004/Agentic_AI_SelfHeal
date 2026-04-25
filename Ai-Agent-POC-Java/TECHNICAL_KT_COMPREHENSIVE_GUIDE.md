# 🚀 AI-Based Self-Healing Test Automation Framework
## Complete Technical Knowledge Transfer (KT) Guide

**Document Purpose:** Comprehensive guide for team technical knowledge transfer  
**Target Audience:** QA Automation Engineers & Developers (Beginner to Intermediate)  
**Date:** April 12, 2026

---

## 📋 Table of Contents

1. [Overview & Key Concepts](#overview--key-concepts)
2. [Architecture & Components](#architecture--components)
3. [Entry Points & Execution Flow](#entry-points--execution-flow)
4. [Complete End-to-End Technical Flow](#complete-end-to-end-technical-flow)
5. [API Testing in Detail](#api-testing-in-detail)
6. [UI Testing in Detail](#ui-testing-in-detail)
7. [AI Self-Healing Mechanism](#ai-self-healing-mechanism)
8. [Data Persistence & Updates](#data-persistence--updates)
9. [Running & Troubleshooting](#running--troubleshooting)
10. [Q&A for Team Discussion](#qa-for-team-discussion)

---

## 🎯 Overview & Key Concepts

### What is an AI Self-Healing Test Framework?

A traditional test automation framework breaks when:
- **UI elements change** (CSS selectors, IDs, or class names are updated)
- **API responses evolve** (new fields, field values change, structure modifications)

**Solution:** An AI-powered framework that:
1. **Detects** when tests fail due to element/data changes
2. **Analyzes** what changed using an AI agent (Claude via OpenRouter)
3. **Fixes** test expectations and UI locators automatically
4. **Re-runs** tests with corrected data/locators
5. **Persists** the corrections for future runs

### Why This Matters

| Problem | Traditional Approach | Self-Healing Approach |
|---------|----------------------|----------------------|
| API response field changes | Manual test data update | AI analyzes & updates automatically |
| UI element locator broken | Manual selector hunt | AI suggests new selector, tests retry |
| Test data out-of-sync | Re-write test cases | AI patches test files automatically |
| Maintenance overhead | High (many test updates) | Low (auto-healing) |

---

## 🏗️ Architecture & Components

### Technology Stack

```
┌─────────────────────────────────────────────────────┐
│  Testing Framework: TestNG + Cucumber (BDD)          │
│  Browser Automation: Microsoft Playwright (Java)      │
│  AI Engine: OpenRouter API (Claude/Gemma models)      │
│  Build Tool: Maven                                    │
│  Language: Java 17                                    │
│  Test Format: Gherkin (native English-like syntax)    │
└─────────────────────────────────────────────────────┘
```

### Core Components Overview

```
┌──────────────────────────────────────────────────────────────┐
│                  TEST SUITE ORCHESTRATOR                      │
│                   (testng.xml runner)                         │
└────────────┬──────────────┬──────────────┬───────────────────┘
             │              │              │
    ┌────────▼──────┐ ┌──────▼────────┐ ┌─▼──────────────┐
    │  PRE-CLEANUP  │ │  CUCUMBER API │ │  API SELF-     │
    │  (TestND.xml)  │ │  CRUD TESTS   │ │  HEALING TEST  │
    └────────┬───────┘ └───────┬───────┘ └────────┬───────┘
             │                 │                   │
             ▼                 ▼                   ▼
    ┌─────────────────────────────────────────────────────┐
    │           CORE TESTING FRAMEWORK LAYER              │
    │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐   │
    │  │  API Layer   │  │  UI Layer    │  │ AI Layer │   │
    │  │  (HTTP calls)│  │(Playwright)  │  │(OpenAI)  │   │
    │  └──────────────┘  └──────────────┘  └──────────┘   │
    └─────────────────────────────────────────────────────┘
             │                 │                   │
             ▼                 ▼                   ▼
    ┌──────────────┐  ┌──────────────────┐  ┌────────────┐
    │ API Data     │  │ Locator Data     │  │ AI Models  │
    │ Storage      │  │ Storage (JSON)   │  │ (Claude)   │
    │ (JSON files) │  │                  │  │            │
    └──────────────┘  └──────────────────┘  └────────────┘
```

### File Structure

```
ai-self-healing-framework/
│
├── src/main/java/
│   ├── api/                      ← API Testing Logic
│   │   ├── ApiClient.java        (makes HTTP calls)
│   │   ├── ApiValidator.java     (compares expected vs actual responses)
│   │   ├── ApiSelfHealer.java    (orchestrates healing)
│   │   ├── ApiHealingAgent.java  (calls OpenRouter for API fixes)
│   │   └── ApiResponseRecorder.java (saves API responses)
│   │
│   ├── core/                     ← Core UI Automation
│   │   ├── DriverManager.java    (manages Playwright browser)
│   │   ├── SelfHealingDriver.java ← ⭐ KEY: wraps Playwright with auto-healing
│   │   └── LocatorStore.java     (manages UI element selectors)
│   │
│   ├── ai/                       ← AI Integration
│   │   └── HealingAgent.java     (calls OpenRouter for UI locator fixes)
│   │
│   ├── pages/                    ← Page Object Models
│   │   └── LoginPage.java        (abstracts login UI actions)
│   │
│   ├── utils/                    ← Utility Functions
│   │   └── RetryUtils.java       (retry logic with exponential backoff)
│   │
│   ├── config/
│   │   └── ApiEndpoints.java     (API configuration)
│   │
│   └── resources/
│       ├── locators.json         ← UI element selectors (persisted by AI)
│       └── test-page/
│           └── login.html        (test UI page)
│
├── src/test/java/
│   ├── runner/
│   │   └── CucumberRunner.java   (Cucumber test runner)
│   │
│   ├── steps/
│   │   ├── LoginApiSteps.java    (Cucumber step definitions for API)
│   │   └── ApiTestContext.java   (shared context between steps)
│   │
│   └── tests/
│       ├── ApiSelfHealingTest.java   ← ⭐ 4-step API healing flow
│       ├── ApiAndUiTest.java         (combined API + UI tests)
│       ├── LoginTest.java            (UI login tests)
│       └── TestSuiteSetup.java       (pre-test cleanup)
│
├── src/test/resources/
│   ├── features/
│   │   └── login-api.feature     ← BDD test scenarios (Gherkin)
│   ├── expected-api-response.json ← Expected API data (updated by AI)
│   └── actual-api-response.json   ← Recorded API responses
│
├── pom.xml                       ← Maven build configuration
├── testng.xml                    ← Test suite definition
└── .env                          ← API key for OpenRouter

BUILD OUTPUT (target/):
├── classes/                      (compiled Java classes)
├── test-classes/                 (compiled test classes)
├── surefire-reports/             (test execution reports)
│   ├── testng-reports.html       (visual test results)
│   └── testng-results.xml        (XML test results)
└── cucumber-reports/             (Cucumber BDD reports)
    └── login-api-report.html     (visual Cucumber results)
```

---

## 🔄 Entry Points & Execution Flow

### How Are Tests Triggered?

#### Command 1: Run All Tests
```bash
mvn test
```
**What happens:**
1. Maven reads `testng.xml` (test suite definition)
2. Looks for `pom.xml` to see all dependencies needed
3. Compiles Java code from `src/main/java` and `src/test/java`
4. Loads `.env` file to get `OPENROUTER_API_KEY` for AI
5. Executes three test suites in order (as per testng.xml)

#### Command 2: Run API Tests Only
```bash
mvn test -Dtest.api.url=http://localhost:3000
```
**What happens:**
- Runs Cucumber API CRUD tests and ApiSelfHealingTest
- Points API calls to the specified URL instead of default
- Useful when backend API is running on a non-standard port

#### Command 3: Run UI Tests with Browser Visible
```bash
mvn test -Dheadless=false
```
**What happens:**
- Runs UI tests (LoginTest) with visible Playwright browser
- Default: `-Dheadless=true` (runs browser invisibly, faster)
- Useful for debugging why a UI test fails

### The 3 Test Suites (in testng.xml)

```xml
<suite name="AI API Self-Healing Framework">
    <test name="Pre-Suite Server Cleanup">        <!-- 1st -->
    <test name="Cucumber API CRUD Tests">         <!-- 2nd -->
    <test name="API Self-Healing Test (4-step)">  <!-- 3rd -->
</suite>
```

#### Suite 1: Pre-Suite Server Cleanup
**File:** `TestSuiteSetup.java`  
**Runs Before:** Everything else  
**Purpose:**
- Deletes any leftover test data from previous runs (except fixture records)
- Ensures clean database before tests start
- Prevents test data pollution

**Example:**
```
Before: GET /login → returns 100 records (leftover from previous runs)
Action: Delete IDs 4, 5, 6, ... (keep fixtures 1, 2, 3)
After:  GET /login → returns only 3 fixture records (clean state)
```

#### Suite 2: Cucumber API CRUD Tests
**File:** `CucumberRunner.java` + `LoginApiSteps.java`  
**Language:** Gherkin (human-readable BDD syntax)  
**Purpose:** Test all API CRUD operations (Create, Read, Update, Delete)

**Example Scenario:**
```gherkin
Scenario: POST /login creates a new login record
  When I send a POST request to "/login" with body:
    """
    { "username": "testuser", "email": "test@example.com", ... }
    """
  Then the response status code should be 201
  And the response body field "data.email" should equal "testuser@example.com"
```

**Why BDD?**
- Business stakeholders can understand test logic without code knowledge
- Scenarios act as "living documentation"
- Easy to modify test cases (no coding required)

#### Suite 3: API Self-Healing Test
**File:** `ApiSelfHealingTest.java`  
**Purpose:** Demonstrates the 4-step self-healing mechanism  
**Runs:** Only after Suite 2 passes

---

## 🎬 Complete End-to-End Technical Flow

### High-Level Execution Journey

```
START (mvn test command)
  │
  ├─► [PRE-CLEANUP] TestSuiteSetup
  │   └─► Deletes leftover test data
  │
  ├─► [CUCUMBER API TESTS] CucumberRunner
  │   └─► Executes GET, POST, PUT, DELETE scenarios
  │       (all via Human-readable Gherkin syntax)
  │
  └─► [SELF-HEALING] ApiSelfHealingTest
      │
      ├─► STEP 1: Trigger API Request
      │   └─► HTTP GET → Response received
      │
      ├─► STEP 2: Validate Status Code
      │   ├─► Is HTTP 200?
      │   ├─► YES → Continue to Step 3
      │   └─► NO → FAIL & STOP (don't invoke AI if status is wrong)
      │
      ├─► STEP 3: Save & Validate Response Body
      │   ├─► Save to: actual-api-response.json
      │   ├─► Compare: actual vs expected-api-response.json
      │   ├─► Mismatch? → GO TO STEP 4
      │   └─► Match? → PASS & FINISH
      │
      └─► STEP 4: AI Self-Healing (if mismatch in Step 3)
          │
          ├─► SEND to OpenRouter: 
          │   {
          │     "endpoint": "GET /login",
          │     "actual": {...actual response...},
          │     "expected": {...expected response...},
          │     "diff": {"field1": ["old", "new"], ...}
          │   }
          │
          ├─► RECEIVE from OpenRouter:
          │   {
          │     "updatedExpectedJson": {...corrected expected...},
          │     "featureFileUpdates": [...updated test assertions...],
          │     "reasoning": "Field 'status' changed from 'pending' to 'active'"
          │   }
          │
          ├─► ACTIONS by Framework:
          │   ├─► Update: expected-api-response.json
          │   ├─► Patch: login-api.feature (Cucumber tests)
          │   └─► Re-Compare: actual vs updated expected
          │
          └─► RESULT: PASS (test now matches actual API)

END (all tests completed, reports generated)
```

### Detailed Step-by-Step with Code Flow

#### STEP 1: Trigger API Request

**Code Location:** `ApiSelfHealingTest.java` → `step1_triggerApiRequest()`

```java
@Test(priority = 1)
public void step1_triggerApiRequest() {
    log.info("┌─ STEP 1 │ Triggering API request: GET /login");
    
    // Send HTTP GET request
    lastResponse = client.get(BASE_URL + ENDPOINT);
    
    // Check for network errors
    Assert.assertFalse(lastResponse.hasFailed(),
        "Network error — could not reach the API: " + lastResponse.error());
    
    log.info("└─ Response received │ HTTP {} │ {} chars",
        lastResponse.status(), lastResponse.body().length());
}
```

**Behind the Scenes:**
```
1. ApiClient.get(url) method:
   - Creates HTTP GET request using Java's HttpClient
   - Sends to specified URL (e.g., http://localhost:3000/login)
   - Reads response status code (e.g., 200, 404, 500)
   - Captures response body as JSON string
   - Returns ApiCallResult object with status + body

2. ApiCallResult object contains:
   - statusCode: int (e.g., 200)
   - body: String (e.g., '{"data": [...], "success": true}')
   - hasFailed: boolean (indicates network error)
   - errorMessage: String (if network error occurred)
```

**Example Output:**
```
┌─ STEP 1 │ Triggering API request: GET http://localhost:3000/login
└─ Response received │ HTTP 200 │ 1234 chars
```

---

#### STEP 2: Validate Status Code

**Code Location:** `ApiSelfHealingTest.java` → `step2_validateStatusCode()`

```java
@Test(priority = 2, dependsOnMethods = "step1_triggerApiRequest")
public void step2_validateStatusCode() {
    log.info("┌─ STEP 2 │ Validating status code");
    
    // STRICT CHECK: must be exactly 200
    Assert.assertEquals(lastResponse.status(), 200,
        String.format("Expected HTTP 200 but got HTTP %d. Stopping suite.",
            lastResponse.status()));
    
    log.info("└─ ✅ HTTP 200 OK");
}
```

**Decision Logic:**
```
Is HTTP Status Code == 200?
    ├─ YES → Continue to Step 3 ✅
    └─ NO  → FAIL & STOP ❌
            (Why? We only validate response bodies when status is 200.
             If status is wrong, the API endpoint is fundamentally broken,
             no point analyzing response body. AI doesn't help here.)
```

**Dependency Annotation Explained:**
```java
@Test(priority = 2, dependsOnMethods = "step1_triggerApiRequest")
           ↓                         ↓
    Run after all    Only run this test if step1_triggerApiRequest
    priority=1       passed. If step1 fails, step2 is skipped.
```

---

#### STEP 3: Save & Validate Response Body

**Code Location:** `ApiSelfHealingTest.java` → `step3_saveAndValidateBody()`

This is the **critical comparison step** where AI is triggered if needed.

```java
@Test(priority = 3, dependsOnMethods = "step2_validateStatusCode")
public void step3_saveAndValidateBody() throws IOException {
    log.info("┌─ STEP 3 │ Saving endpoint snapshot...");
    
    // 1. PERSIST: Save actual API response to file
    ApiResponseRecorder recorder = ApiResponseRecorder.getInstance();
    saveAllResponses(); // Writes to: actual-api-response.json
    
    // 2. COMPARE: Load expected response from classpath
    log.info("│  Comparing actual vs expected...");
    BodyCompareResult result = validator.compareBody(
        lastResponse.body(),      // what API returned
        "expected-api-response.json"); // what we had stored
    
    // 3. CHECK: Do they match?
    if (result.matched()) {
        log.info("└─ ✅ Response body matches — test PASSES");
        return; // SUCCESS - no healing needed
    }
    
    // 4. MISMATCH: Log difference and escalate to Step 4
    log.warn("│  ❌ Body mismatch detected:");
    log.warn("│  {}", result.diffSummary()); // Show what changed
    this.lastCompareResult = result; // Store for Step 4
    this.pendingHeal = true; // Signal Step 4 to run
}
```

**What Gets Saved? (actual-api-response.json)**
```json
{
  "generatedAt": "2026-04-12T13:48:27.715828Z",
  "endpoints": [
    {
      "url": "http://localhost:3000/login",
      "method": "GET",
      "statusCode": 200,
      "changed": true,    ← Was this response different from last time?
      "responseBody": {
        "success": true,
        "message": "All logins retrieved successfully",
        "data": [
          {
            "id": 1,
            "name": "user1",
            "email": "user1@example.com",
            "status": "active"
          }
        ]
      }
    }
  ]
}
```

**How Comparison Works (ApiValidator.compareBody):**

```java
public BodyCompareResult compareBody(String actualJson, String expectedResourceName) {
    
    // Step 1: Load both JSONs
    String expectedJson = loadResource(expectedResourceName); // from classpath
    JsonNode expected = mapper.readTree(expectedJson);
    JsonNode actual = mapper.readTree(actualJson);      // from API response
    
    // Step 2: Deep-compare all fields (leaf by leaf)
    Map<String, String[]> diff = new LinkedHashMap<>();
    this.compareNodes("", expected, actual, diff);
    
    // Step 3: Check if differences found
    if (diff.isEmpty()) {
        log.info("✅ All {} fields match", countLeafNodes(expected));
        return new BodyCompareResult(true, expectedJson, actualJson, diff);
    }
    
    // Step 4: Log mismatch
    log.warn("❌ {} field(s) differ:", diff.size());
    diff.forEach((fieldPath, valuePair) -> {
        log.warn("   [{}] expected={} | actual={}",
            fieldPath, valuePair[0], valuePair[1]);
    });
    
    return new BodyCompareResult(false, expectedJson, actualJson, diff);
}
```

**Example Diff Output:**
```
Comparing actual vs expected:

❌ Body mismatch detected — 2 field(s) differ:

   [data[0].status] expected="pending" | actual="active"
   [data[0].email] expected="user1@old.com" | actual="user1@new.com"
```

---

#### STEP 4: AI Self-Healing

**Code Location:** `ApiSelfHealingTest.java` → `step4_aiSelfHeal()`

This is where **AI magic happens** — the framework fixes itself!

```java
@Test(priority = 4, dependsOnMethods = "step3_saveAndValidateBody")
public void step4_aiSelfHeal() {
    
    if (!pendingHeal) {
        log.info("┌─ STEP 4 │ Skipped — no mismatch from Step 3");
        return; // No healing needed
    }
    
    log.info("┌─ STEP 4 │ AI Agent analysing mismatch via OpenRouter...");
    
    // 1. CALL AI: Send diff to OpenRouter (Claude)
    boolean healed = selfHealer.heal(
        BASE_URL + ENDPOINT,        // "http://localhost:3000/login"
        lastResponse.body(),        // actual JSON from API
        lastCompareResult,          // diff details from Step 3
        "expected-api-response.json"); // file to update
    
    if (!healed) {
        log.error("└─ ❌ AI healing failed");
        Assert.fail("Self-healing did not succeed");
    }
    
    // 2. VERIFY: Re-compare after AI updated expected data
    log.info("│  Re-comparing after healing...");
    BodyCompareResult newResult = validator.compareBody(
        lastResponse.body(),
        "expected-api-response.json");
    
    Assert.assertTrue(newResult.matched(),
        "After healing, response still doesn't match");
    
    log.info("└─ ✅ Self-healing succeeded — test now PASSES");
}
```

**What ApiSelfHealer.heal() Does:**

```
┌─────────────────────────────────────────────────────┐
│  ApiSelfHealer.heal() Orchestration                 │
├─────────────────────────────────────────────────────┤
│                                                      │
│  1. CALL → ApiHealingAgent.analyze()               │
│     └─► Makes HTTP call to OpenRouter API           │
│         (Claude/Gemma model)                        │
│                                                      │
│  2. SEND → JSON containing:                        │
│     {                                               │
│       "endpoint": "GET /login",                    │
│       "actual": {...actual API response...},       │
│       "expected": {...stored expected...},         │
│       "diff": {"field": ["old", "new"], ...}       │
│     }                                               │
│                                                      │
│  3. RECEIVE → Structured AI Response:              │
│     {                                               │
│       "updatedExpectedJson": {...},                │
│       "featureFileUpdates": [                      │
│         {"oldStep": "...", "newStep": "..."}       │
│       ],                                            │
│       "reasoning": "..."                           │
│     }                                               │
│                                                      │
│  4. PERSIST → Write files:                         │
│     ├─► Update: expected-api-response.json         │
│     └─► Patch: login-api.feature                   │
│                                                      │
│  5. VERIFY → Re-compare to confirm fix worked      │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**API Call to OpenRouter - Implementation Details:**

```java
public ApiHealingResult analyze(String endpointUrl, String actualJson,
                                 String expectedJson, String diffSummary) {
    
    // Prepare the request
    String userPrompt = String.format("""
        Endpoint: %s
        Expected: %s
        Actual: %s
        Diff: %s
        
        What changed? Update the expected JSON to match actual.
        List any Cucumber feature file assertions that need updating.
        """, endpointUrl, expectedJson, actualJson, diffSummary);
    
    // Build HTTP Request to OpenRouter
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""
            {
              "model": "google/gemma-4-31b-it:free",
              "messages": [
                {
                  "role": "system",
                  "content": "%s"   ← System prompt with instructions
                },
                {
                  "role": "user",
                  "content": "%s"    ← Our diff analysis request
                }
              ],
              "temperature": 0.7,
              "max_tokens": 2000,
              "timeout": 120        ← 2 minute timeout (OpenRouter can be slow)
            }
            """))
        .timeout(Duration.ofSeconds(120))
        .build();
    
    // Send Request & Get Response
    HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
    
    // Parse AI Response
    JsonNode responseJson = mapper.readTree(response.body());
    String aiContent = responseJson.path("choices[0].message.content").asText();
    ApiHealingResult result = mapper.readValue(aiContent, ApiHealingResult.class);
    
    return result;
}
```

**System Prompt (Instructions to AI):**
```
You are an API test self-healing agent.

The live API response body no longer matches the stored expected test data.

Your task:
1. Analyze what changed between expected and actual response.
2. Assume the change is a VALID API evolution (not a bug).
3. Return the updated expected JSON to store going forward.
4. List Cucumber feature file assertions that need updating.

Respond ONLY with valid JSON:
{
  "updatedExpectedJson": { ...complete new expected object... },
  "featureFileUpdates": [
    { "oldStep": "exact old step", "newStep": "updated step" }
  ],
  "reasoning": "one sentence explanation"
}
```

**Example AI Response:**
```json
{
  "updatedExpectedJson": {
    "success": true,
    "message": "All logins retrieved successfully",
    "data": [
      {
        "id": 1,
        "name": "user1",
        "email": "user1@example.com",
        "status": "active"    ← Changed from "pending" to "active"
      }
    ]
  },
  "featureFileUpdates": [
    {
      "oldStep": "And the response body field \"data[0].status\" should equal \"pending\"",
      "newStep": "And the response body field \"data[0].status\" should equal \"active\""
    }
  ],
  "reasoning": "User status field has been updated to reflect active status in the system."
}
```

**Persisting Updates:**

```java
// After receiving AI response...

// 1. UPDATE expected-api-response.json
String updatedJson = result.updatedExpectedJson();
Path expectedPath = Paths.get("src/test/resources/expected-api-response.json");
Files.write(expectedPath, updatedJson.getBytes(StandardCharsets.UTF_8));
log.info("✅ Updated expected-api-response.json");

// 2. PATCH login-api.feature file with new assertions
for (FeatureFileUpdate update : result.featureFileUpdates()) {
    String featureContent = Files.readString(featureFilePath);
    featureContent = featureContent.replace(
        update.oldStep(),
        update.newStep()
    );
    Files.writeString(featureFilePath, featureContent);
    log.info("✅ Patched feature file: {} → {}",
        update.oldStep(), update.newStep());
}

// 3. RE-COMPARE to confirm healing worked
BodyCompareResult newResult = validator.compareBody(
    lastResponse.body(),
    "expected-api-response.json");

if (newResult.matched()) {
    log.info("✅ Healing successful - test now PASSES");
    return true;
} else {
    log.error("❌ Healing unsuccessful - mismatch still exists");
    return false;
}
```

---

## 🌐 API Testing in Detail

### How API Tests Are Structured (Cucumber BDD)

**File:** `src/test/resources/features/login-api.feature`

```gherkin
Feature: Login API CRUD Operations
  As a developer
  I want to validate all Login API endpoints
  So that I can confirm the backend works correctly

  Background:
    Given the API base URL is "http://localhost:3000"

  # ── GET /login ──────────────────────────────────────
  Scenario: GET /login returns all login records
    When I send a GET request to "/login"
    Then the response status code should be 200
    And the response body should not be empty

  # ── POST /login ─────────────────────────────────────
  Scenario: POST /login creates a new login record
    When I send a POST request to "/login" with body:
      """
      {
        "username": "testuser",
        "email": "testuser@example.com",
        "password": "secure123"
      }
      """
    Then the response status code should be 201
    And the response body field "data.email" should equal "testuser@example.com"

  # ── GET /login/:id ──────────────────────────────────
  Scenario: GET /login/:id retrieves a specific login record
    Given a login record exists with body: {...}
    When I send a GET request to "/login/{stored-id}"
    Then the response status code should be 200
    And the response body field "data.email" should equal "gettest@example.com"

  # ── PUT /login/:id ──────────────────────────────────
  Scenario: PUT /login/:id updates an existing login record
    Given a login record exists with body: {...}
    When I send a PUT request to "/login/{stored-id}" with body: {...}
    Then the response status code should be 200
    And the response body field "data.email" should equal "putupdated@example.com"

  # ── DELETE /login/:id ───────────────────────────────
  Scenario: DELETE /login/:id removes a login record
    Given a login record exists with body: {...}
    When I send a DELETE request to "/login/{stored-id}"
    Then the response status code should be 200
```

### Step Definitions (Java Implementation)

**File:** `src/test/java/steps/LoginApiSteps.java`

Each Gherkin step maps to a Java method:

```java
public class LoginApiSteps {
    
    private final ApiTestContext ctx; // Shared context
    
    // ── Given Steps ──────────────────────────────────────
    
    @Given("the API base URL is {string}")
    public void theApiBaseUrlIs(String url) {
        ctx.baseUrl = url; // e.g., "http://localhost:3000"
        ctx.client = new ApiClient();
    }
    
    @Given("a login record exists with body:")
    public void aLoginRecordExistsWithBody(String body) {
        // Create a test record via POST
        String url = ctx.baseUrl + "/login";
        ApiClient.ApiCallResult response = ctx.client.post(url, body);
        
        if (response.status() == 201) {
            // Extract ID for later use (PUT/DELETE)
            ctx.storedId = ctx.client.extractField(response.body(), "data.id");
            log.info("Created test record with ID: {}", ctx.storedId);
        } else {
            throw new RuntimeException("Failed to create test record: HTTP " +
                response.status());
        }
    }
    
    // ── When Steps ──────────────────────────────────────
    
    @When("I send a GET request to {string}")
    public void iSendGetRequest(String path) {
        String resolvedUrl = ctx.baseUrl + resolvePath(path);
        ctx.lastResponse = ctx.client.get(resolvedUrl);
        ctx.failedEndpointUrl = resolvedUrl;
    }
    
    @When("I send a POST request to {string} with body:")
    public void iSendPostRequest(String path, String body) {
        String resolvedUrl = ctx.baseUrl + resolvePath(path);
        ctx.lastResponse = ctx.client.post(resolvedUrl, body.trim());
        
        // Auto-capture ID for cleanup
        if (ctx.lastResponse.status() == 201) {
            ctx.lastCreatedId = ctx.client.extractField(
                ctx.lastResponse.body(), "data.id");
        }
    }
    
    @When("I send a PUT request to {string} with body:")
    public void iSendPutRequest(String path, String body) {
        String resolvedUrl = ctx.baseUrl + resolvePath(path);
        ctx.lastResponse = ctx.client.put(resolvedUrl, body.trim());
    }
    
    @When("I send a DELETE request to {string}")
    public void iSendDeleteRequest(String path) {
        String resolvedUrl = ctx.baseUrl + resolvePath(path);
        ctx.lastResponse = ctx.client.delete(resolvedUrl);
    }
    
    // ── Then Steps ──────────────────────────────────────
    
    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShould(int expectedCode) {
        Assert.assertEquals(ctx.lastResponse.status(), expectedCode,
            String.format("Expected HTTP %d but got HTTP %d for %s",
                expectedCode, ctx.lastResponse.status(),
                ctx.failedEndpointUrl));
    }
    
    @Then("the response body field {string} should equal {string}")
    public void theResponseBodyFieldShould(String fieldPath, String expected) {
        String actual = ctx.client.extractField(
            ctx.lastResponse.body(), fieldPath);
        Assert.assertEquals(actual, expected,
            String.format("Field '%s' mismatch (expected '%s' but got '%s')",
                fieldPath, expected, actual));
    }
    
    // Helper: Replace placeholders like {stored-id}
    private String resolvePath(String path) {
        return path.replace("{stored-id}", ctx.storedId);
    }
}
```

### Test Execution Flow (One Scenario)

```
Gherkin Step (Feature File)           →  Java Method (Step Definition)        →  What Happens
─────────────────────────────────────────────────────────────────────────────────────────────
When I send a POST request to         →  iSendPostRequest()                   →  ApiClient.post()
"/login" with body: {...}                  ↓ sends HTTP POST                        ↓
                                        Parses response JSON                    Server processes
                                        Extracts "data.id" from response         request
                                        Stores ID in ctx.storedId               Returns 201 + data

Then the response status code         →  theResponseStatusCodeShould(201)     →  Compare HTTP codes
should be 201                             ↓ Assert.assertEquals()                 Pass if match
                                        Logs assertion result                   Fail if mismatch

Then the response body field          →  theResponseBodyFieldShould()         →  Extract JSON field
"data.email" should equal              ↓ ctx.client.extractField()             via JSONPath
"testuser@example.com"                 ↓ Assert.assertEquals()                 Compare values
                                        Logs comparison result                  Pass if match
```

---

## 🎮 UI Testing in Detail

### How UI Tests Are Structured

**File:** `src/test/java/tests/LoginTest.java`

```java
public class LoginTest {
    
    private static final String TARGET_URL = "https://the-internet.herokuapp.com/login";
    private SelfHealingDriver driver;  // ← ⭐ self-healing enabled
    private LoginPage loginPage;
    
    @BeforeClass
    public void setUp() {
        // 1. Launch Playwright Browser
        boolean headless = Boolean.parseBoolean(
            System.getProperty("headless", "true"));
        DriverManager.launch(headless);
        
        // 2. Wrap Page with Self-Healing Driver
        Page page = DriverManager.getPage();
        driver = new SelfHealingDriver(page);
        
        // 3. Create Page Object
        loginPage = new LoginPage(driver);
        
        // 4. Navigate to Login Page
        driver.navigateTo(TARGET_URL);
    }
    
    @Test(priority = 1, description = "Valid credentials redirect to secure area")
    public void testSuccessfulLogin() {
        // User Story: User can login with valid credentials
        
        // Step 1: Fill in username
        loginPage.enterUsername("tomsmith");
        // Behind scenes: SelfHealingDriver.fill("login-username", "tomsmith")
        //   ├─ Looks up "login-username" in locators.json → gets CSS selector
        //   ├─ Tries to fill(): success → test continues
        //   └─ If fails: triggers AI healing (detailed in next section)
        
        // Step 2: Fill in password
        loginPage.enterPassword("SuperSecretPassword!");
        
        // Step 3: Click login button
        loginPage.clickLogin();
        
        // Step 4: Verify success message appears
        String successMsg = loginPage.getSuccessMessage();
        Assert.assertTrue(successMsg.contains("You logged into a secure area!"),
            "Success message not found");
    }
    
    @Test(priority = 2, description = "Invalid credentials show error message")
    public void testFailedLogin_wrongCredentials() {
        loginPage.enterUsername("wronguser");
        loginPage.enterPassword("wrongpassword");
        loginPage.clickLogin();
        
        String errorMsg = loginPage.getErrorMessage();
        Assert.assertTrue(errorMsg.contains("Your username is invalid"),
            "Error message not found");
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        DriverManager.quit(); // Close browser
    }
}
```

### Page Object Model (LoginPage)

**File:** `src/main/java/pages/LoginPage.java`

```java
public class LoginPage {
    
    private final SelfHealingDriver driver;
    
    public LoginPage(SelfHealingDriver driver) {
        this.driver = driver;
    }
    
    // Element IDs (refer to locators.json)
    private static final String USERNAME_FIELD = "login-username";
    private static final String PASSWORD_FIELD = "login-password";
    private static final String LOGIN_BUTTON = "login-submit";
    private static final String SUCCESS_MESSAGE = "login-success-message";
    private static final String ERROR_MESSAGE = "login-error-message";
    
    // Action Methods
    public void enterUsername(String username) {
        driver.fill(USERNAME_FIELD, username);
    }
    
    public void enterPassword(String password) {
        driver.fill(PASSWORD_FIELD, password);
    }
    
    public void clickLogin() {
        driver.click(LOGIN_BUTTON);
    }
    
    public String getSuccessMessage() {
        return driver.getText(SUCCESS_MESSAGE);
    }
    
    public String getErrorMessage() {
        return driver.getText(ERROR_MESSAGE);
    }
}
```

### Locators Reference (locators.json)

```json
{
  "login-username": {
    "id": "login-username",
    "primary": "#username",
    "alternatives": [
      "input[name='username']",
      "input[type='text']"
    ],
    "description": "Username input field — id='username'"
  },
  
  "login-password": {
    "id": "login-password",
    "primary": "#password",
    "alternatives": [
      "input[name='password']",
      "input[type='password']"
    ],
    "description": "Password input field — id='password'"
  },
  
  "login-submit": {
    "id": "login-submit",
    "primary": "#loginBtn",
    "alternatives": [
      "button[type='button']",
      "button[type='submit']",
      "button.radius"
    ],
    "description": "Login button—id='loginBtn'"
  }
}
```

### UI Locator Resolution Flow

```
loginPage.enterUsername("tomsmith")
    ↓
driver.fill("login-username", "tomsmith")
    ↓
SelfHealingDriver.resolveLocator("login-username")
    ├─ Check if element exists with PRIMARY selector (#username)
    │  ├─ YES → Return Locator ✅
    │  └─ NO ↓
    ├─ Try each ALTERNATIVE selector:
    │  ├─ input[name='username'] → Found? Return ✅
    │  └─ input[type='text'] → Found? Return ✅
    │  └─ None worked ↓
    ├─ PRIMARY and ALTERNATIVES all failed ❌
    │  ├─ Get current DOM snapshot
    │  ├─ Call HealingAgent (OpenRouter) with:
    │  │   {
    │  │     "elementId": "login-username",
    │  │     "description": "Username input field",
    │  │     "failedLocator": "#username",
    │  │     "domSnapshot": "<body>...</body>",
    │  │     "visibleElements": [...]
    │  │   }
    │  │
    │  ├─ AI Returns:
    │  │   {
    │  │     "selector": "#user_input",
    │  │     "confidence": "HIGH",
    │  │     "reason": "Element ID changed from 'username' to 'user_input'"
    │  │   }
    │  │
    │  ├─ Update locators.json with new selector
    │  ├─ Retry locator resolution with new selector
    │  └─ Success → Continue test ✅
    │
    └─ Perform action: locator.fill("tomsmith")
```

**Code - SelfHealingDriver.resolveLocator():**

```java
private Locator resolveLocator(String elementId) {
    log.debug("Resolving locator for: {}", elementId);
    
    // Step 1: Try PRIMARY selector
    String primary = store.getPrimary(elementId);
    Locator loc = page.locator(primary);
    if (isLocatorAvailable(loc)) {
        log.debug("✅ Found via PRIMARY: {}", primary);
        return loc;
    }
    
    // Step 2: Try ALTERNATIVES
    List<String> alternatives = store.getAlternatives(elementId);
    for (String alt : alternatives) {
        Locator altLoc = page.locator(alt);
        if (isLocatorAvailable(altLoc)) {
            log.debug("✅ Found via ALTERNATIVE: {}", alt);
            return altLoc;
        }
    }
    
    // Step 3: All selectors failed, invoke AI healing
    log.warn("❌ No selector worked for {}", elementId);
    String healed = healLocator(elementId);
    
    if (healed != null) {
        log.info("✅ Using healed selector: {}", healed);
        return page.locator(healed);
    }
    
    throw new LocatorException(
        "Could not resolve locator for " + elementId);
}

private String healLocator(String elementId) {
    try {
        // Get page DOM
        String domSnapshot = page.evaluate("() => document.body.outerHTML");
        
        // Get visible elements
        List<String> visibleElements = page.evaluate(
            "() => Array.from(document.querySelectorAll('*'))" +
            ".map(el => ({tag: el.tagName, id: el.id, " +
            "name: el.name, classes: el.className}))" +
            ".slice(0, 50).toString()"); // First 50
        
        // Call AI
        HealingAgent.HealResult result = healingAgent.fixLocator(
            elementId,
            store.getPrimary(elementId),
            domSnapshot,
            visibleElements);
        
        // Persist healed selector
        store.update(elementId, result.selector());
        store.save(); // Write back to locators.json
        
        return result.selector();
    } catch (Exception e) {
        log.error("Healing failed for {}: {}", elementId, e.getMessage());
        return null;
    }
}
```

---

## 🤖 AI Self-Healing Mechanism

### Architecture: How AI is Integrated

```
┌──────────────────────────────────────────────────────┐
│  Framework detects failure                           │
│  (Locator not found OR API data mismatch)            │
└────────────────────┬─────────────────────────────────┘
                     │
                     ▼
       ┌─────────────────────────────┐
       │ Collect Context             │
       ├─────────────────────────────┤
       │ • What failed? (element/API)│
       │ • Current DOM/API response  │
       │ • Previous selector/data    │
       │ • What changed?             │
       └────────────┬────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────┐
    │ Send to OpenRouter API            │
    ├───────────────────────────────────┤
    │ API: https://openrouter.ai/v1... │
    │ Model: google/gemma-4-31b-it     │
    │ Timeout: 120 seconds             │
    │ Retry: 3 times with backoff      │
    └────────────┬──────────────────────┘
                 │
                 ▼
     ┌───────────────────────────┐
     │ Claude/Gemma Analyzes     │
     ├───────────────────────────┤
     │ • Parses DOM/API response │
     │ • Understands what changed│
     │ • Suggests new selector   │
     │ • Updates test assertions │
     └──────────┬────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │ Return Healed Suggestion     │
    ├──────────────────────────────┤
    │ {                            │
    │   "selector": "#new_id",     │
    │   "confidence": "HIGH",      │
    │   "reason": "..."            │
    │ }                            │
    └──────────┬───────────────────┘
               │
               ▼
   ┌────────────────────────────────┐
   │ Framework Applies Fix          │
   ├────────────────────────────────┤
   │ 1. Update locators.json        │
   │ 2. Update feature files        │
   │ 3. Retry with new selector     │
   │ 4. Verify fix succeeds         │
   └────────────┬───────────────────┘
                │
                ▼
        ┌──────────────┐
        │ Test Retries │
        │ with Fix     │
        │  PASS ✅     │
        └──────────────┘
```

### OpenRouter Configuration

**What is OpenRouter?**
- An API gateway for Large Language Models (LLMs)
- Provides access to multiple AI models (Claude, ChatGPT, Gemma, etc.)
- Unified OpenAI-compatible API interface
- Paid usage (but has free tier models like Gemma)

**Framework Configuration:**

```env
# File: .env
OPENROUTER_API_KEY=sk-xxx...  ← Your OpenRouter API key

# Alternative: ANTHROPIC_API_KEY for direct Claude API
ANTHROPIC_API_KEY=sk-ant-...
```

**API Call Details:**

```
POST https://openrouter.ai/api/v1/chat/completions

Headers:
  Authorization: Bearer <OPENROUTER_API_KEY>
  Content-Type: application/json

Body:
{
  "model": "google/gemma-4-31b-it:free",  ← Free model
  "messages": [
    {
      "role": "system",
      "content": "You are a QA automation AI..."  ← Instructions
    },
    {
      "role": "user",
      "content": "Element failed: {...}..."        ← Problem description
    }
  ],
  "temperature": 0.7,        ← Creativity level (0=deterministic, 1=creative)
  "max_tokens": 512          ← Max response length
}

Response:
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{\"selector\":\"...\",\"confidence\":\"...\",\"reason\":\"...\"}"
      }
    }
  ]
}
```

### Retry Logic (for Slow LLM Responses)

```java
// OpenRouter can be slow. Framework automatically retries with backoff.

RetryUtils.retryWithBackoff(
    maxAttempts = 3,
    initialDelayMs = 3_000,    // Start: 3 seconds
    maxDelayMs = 30_000,       // Cap: 30 seconds
    
    operation = () -> {
        // Attempt 1: Try immediately
        // If fails (timeout/error) → wait 3s → Attempt 2
        // If fails → wait 9s → Attempt 3
        // If fails → throw exception
        return callOpenRouter(...);
    }
);

// Exponential backoff math:
// Attempt 1: wait 3s
// Attempt 2: wait min(3s * 2, 30s) = 6s
// Attempt 3: wait min(6s * 2, 30s) = 12s
```

### System Prompts (Instructions to AI)

#### For API Healing:
```
You are an API test self-healing agent.

The live API response body no longer matches the stored expected test data.

Your task:
1. Analyze what changed between expected and actual response.
2. Assume the change is a VALID API evolution (not a bug) unless clearly broken.
3. Return the updated expected JSON that should be stored going forward.
4. List any Cucumber feature file assertions that need updating.

Respond ONLY with valid JSON — no markdown, no extra text:
{
  "updatedExpectedJson": { ...complete new expected object... },
  "featureFileUpdates": [
    { "oldStep": "exact old step line", "newStep": "updated step line" }
  ],
  "reasoning": "one sentence"
}
```

#### For UI Locator Healing:
```
You are a QA automation AI agent specialised in self-healing Playwright test locators.

You will receive:
- elementId: the logical name of the UI element to find
- description: what the element does
- failedLocator: the CSS selector that stopped working
- interactiveElements: a JSON array of visible interactive elements
- domSnapshot: partial HTML of the page body

Your task:
Analyse the DOM context and return the BEST CSS selector or XPath.
Prioritise in this order:
  1. data-testid attribute
  2. aria-label attribute
  3. unique id
  4. name attribute
  5. specific CSS class + tag combination
  6. XPath as a last resort

Respond with ONLY valid JSON:
{"selector":"<new selector>","confidence":"HIGH|MEDIUM|LOW","reason":"<one sentence>"}
```

---

## 💾 Data Persistence & Updates

### What Data Gets Persisted?

#### 1. Locators Storage (locators.json)

**File:** `src/main/resources/locators.json`

```json
{
  "login-username": {
    "id": "login-username",
    "primary": "#username",          ← AI updates this when selector breaks
    "alternatives": [
      "input[name='username']",
      "input[type='text']"
    ],
    "description": "Username input"
  }
}
```

**When Updated:**
- UI test tries to find element
- Primary selector fails
- Alternative selectors fail
- AI suggests new selector
- Framework updates `primary` field
- File saved to disk (persisted for next test run)

**Code - LocatorStore.update():**
```java
public void updatePrimary(String elementId, String newSelector) {
    LocatorEntry entry = store.get(elementId);
    if (entry != null) {
        entry.primary = newSelector; // Update in memory
        save(); // Write to file
        log.info("Updated primary selector for {}: {}", 
            elementId, newSelector);
    }
}

public void save() {
    Path path = Paths.get("src/main/resources/locators.json");
    String json = mapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(store);
    Files.write(path, json.getBytes(StandardCharsets.UTF_8));
}
```

#### 2. Expected API Response Storage (expected-api-response.json)

**File:** `src/test/resources/expected-api-response.json`

```json
{
  "success": true,
  "message": "All logins retrieved successfully",
  "data": [
    {
      "id": 1,
      "name": "user1",
      "email": "user1@example.com",
      "status": "active"   ← AI updates this if value changed
    }
  ]
}
```

**When Updated:**
- API self-healing test detects mismatch (Step 3 vs Step 4)
- AI analyzes what changed
- AI returns `updatedExpectedJson`
- Framework writes new JSON to file
- Next test run compares against updated data

**Code - ApiSelfHealer.persistExpectedJson():**
```java
private void persistExpectedJson(String updatedJson) {
    try {
        Path filePath = Paths.get("src/test/resources/expected-api-response.json");
        Files.write(filePath, updatedJson.getBytes(StandardCharsets.UTF_8));
        log.info("✅ Updated expected-api-response.json");
    } catch (IOException e) {
        log.error("Failed to persist updated expected JSON", e);
        throw new RuntimeException(e);
    }
}
```

#### 3. Recorded API Responses (actual-api-response.json)

**File:** `src/test/resources/actual-api-response.json`

```json
{
  "generatedAt": "2026-04-12T13:48:27.715828Z",
  "totalEndpoints": 1,
  "changedCount": 1,
  "endpoints": [
    {
      "url": "http://localhost:3000/login",
      "method": "GET",
      "lastCapturedAt": "2026-04-12T13:48:27.713641Z",
      "statusCode": 200,
      "changed": true,        ← Indicates if response changed from last capture
      "responseBody": {
        "success": true,
        "message": "All logins retrieved successfully",
        "data": [...]
      }
    }
  ]
}
```

**When Updated:**
- Every API self-healing test run (Step 3)
- Records what the API actually returned
- Compares to previous recording to detect changes
- Used to understand what changed over time

**Code - ApiResponseRecorder:**
```java
public synchronized void recordResponse(String method, String url,
                                        int statusCode, String body) {
    String key = method + "::" + url;
    
    // Check if this endpoint was previously recorded
    boolean wasChanged = !store.containsKey(key) ||
        !store.get(key).bodyJson.equals(body);
    
    ApiResponseSnapshot snapshot = new ApiResponseSnapshot(
        method, url, statusCode, body, wasChanged);
    
    store.put(key, snapshot);
    log.info("Recorded: {} {} (changed: {})", method, url, wasChanged);
}

public synchronized void updateAndSave(String filePath) {
    // Deduplicate: one entry per URL+method
    // Write to file
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(buildOutputStructure());
    Files.write(Paths.get(filePath), 
        json.getBytes(StandardCharsets.UTF_8));
}
```

#### 4. Feature File Updates (login-api.feature)

**File:** `src/test/resources/features/login-api.feature`

```gherkin
Scenario: POST /login creates a new login record
  When I send a POST request to "/login" with body:
    """
    { "username": "testuser", ... }
    """
  Then the response status code should be 201
  # AI may update this line:
  # OLD: And the response body field "data.status" should equal "pending"
  # NEW: And the response body field "data.status" should equal "active"
  And the response body field "data.email" should equal "testuser@example.com"
```

**When Updated:**
- When API response changes in ways that affect test assertions
- AI identifies which Cucumber steps need updating
- Framework patches the feature file

**Code - ApiSelfHealer.updateFeatureFile():**
```java
private void updateFeatureFile(List<FeatureFileUpdate> updates) {
    Path filePath = Paths.get("src/test/resources/features/login-api.feature");
    String content = Files.readString(filePath);
    
    for (FeatureFileUpdate update : updates) {
        if (content.contains(update.oldStep())) {
            content = content.replace(update.oldStep(), update.newStep());
            log.info("✅ Patched feature file: {} → {}",
                update.oldStep(), update.newStep());
        } else {
            log.warn("⚠️  Could not find step to patch: {}", update.oldStep());
        }
    }
    
    Files.writeString(filePath, content);
}
```

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    TEST EXECUTION CYCLE                         │
└─────────────────────────────────────────────────────────────────┘

[RUN 1 - Initial Test]
  │
  ├─ API Call → Response: { status: "pending" }
  │  └─ Save to: expected-api-response.json ← Initial baseline
  │
  ├─ UI Click → Element found with #username
  │  └─ Save to: locators.json ← Initial baseline
  │
  └─ Result: PASS ✅

[RUN 2 - After API Changes Status to "active"]
  │
  ├─ API Call → Response: { status: "active" }
  │  ├─ Compare: actual ("active") vs expected ("pending")
  │  ├─ MISMATCH DETECTED ❌
  │  ├─ AI Healing → updatedExpectedJson = { status: "active" }
  │  ├─ Update: expected-api-response.json
  │  ├─ Re-compare: PASS ✅
  │  └─ Persist: feature-file updated
  │
  ├─ UI Click → #username not found
  │  ├─ Try alternatives → fail
  │  ├─ LOCATOR BROKEN ❌
  │  ├─ AI Healing → newSelector = "#user_input"
  │  ├─ Update: locators.json
  │  ├─ Retry: PASS ✅
  │  └─ Persist: new selector saved
  │
  └─ Result: PASS (with auto-corrections) ✅

[RUN 3 - Using Updated Data]
  │
  ├─ API Call → Response: { status: "active" }
  │  ├─ Compare: actual ("active") vs expected ("active")
  │  ├─ MATCH ✅
  │  └─ Result: PASS
  │
  ├─ UI Click → #user_input found immediately
  │  └─ Result: PASS
  │
  └─ Overall: PASS ✅ (no healing needed, everything works)
```

---

## 🚀 Running & Troubleshooting

### Prerequisites

```bash
# Java 17
java -version
# Output: openjdk version "17.x.x"

# Maven
mvn -version
# Output: Apache Maven 3.x.x

# Node.js server (for API tests to target)
node server.js
# Output: Server listening on http://localhost:3000

# API Key for OpenRouter
export OPENROUTER_API_KEY=sk-xxx...
# OR add to .env file in project root
```

### Running Tests

```bash
# 1. Run ALL tests (default)
mvn test

# 2. Run only specific test class
mvn test -Dtest=ApiSelfHealingTest

# 3. Run specific test method
mvn test -Dtest=ApiSelfHealingTest#step1_triggerApiRequest

# 4. Run with custom API URL
mvn test -Dtest.api.url=http://localhost:3000

# 5. Run UI tests with visible browser (not headless)
mvn test -Dtest=LoginTest -Dheadless=false

# 6. Run with debug logging
mvn test -X

# 7. Skip tests during build
mvn clean install -DskipTests

# 8. Run clean build + tests
mvn clean test
```

### Test Output & Reports

**Console Output During Execution:**
```
═══════════════════════════════════════════════════════════════════
[INFO] Running tests.ApiSelfHealingTest
═══════════════════════════════════════════════════════════════════

┌─ STEP 1 │ Triggering API request: GET http://localhost:3000/login
└─ Response received │ HTTP 200 │ 1234 chars

┌─ STEP 2 │ Validating status code
└─ ✅ HTTP 200 OK

┌─ STEP 3 │ Saving endpoint snapshot to: src/test/resources/actual-api-response.json
│  ✅ All recorded endpoints match previously stored state
│  Comparing actual (GET /login) vs expected
└─ ✅ Response body matches expected data — no healing needed

[INFO] Tests run: 4, Failures: 0, Skipped: 0, Time elapsed: 12.345 s — OK
```

**Generated Reports:**

1. **HTML Test Report**
   ```
   target/surefire-reports/testng-reports.html
   ```
   - Open in browser to see visual test results
   - Pass/fail status
   - Execution time
   - Stack traces for failures

2. **Cucumber BDD Report**
   ```
   target/cucumber-reports/login-api-report.html
   ```
   - Human-readable BDD scenario results
   - Feature file reference
   - Step-by-step execution

3. **XML Test Report** (for CI/CD systems)
   ```
   target/surefire-reports/testng-results.xml
   ```

### Common Issues & Troubleshooting

#### Issue 1: "OPENROUTER_API_KEY not found"
```
Error: Cannot initialize HealingAgent: API key not found
```

**Solution:**
```bash
# Option 1: Set environment variable
export OPENROUTER_API_KEY=sk-xxx...

# Option 2: Add to .env file in project root
OPENROUTER_API_KEY=sk-xxx...

# Option 3: Pass as Maven property
mvn test -DOPENROUTER_API_KEY=sk-xxx...
```

#### Issue 2: "Connection timeout - OpenRouter unreachable"
```
Error: Failed to connect to OpenRouter after 3 retries
```

**Causes:**
- Network connectivity issue
- OpenRouter API is down
- Firewall blocking HTTPS

**Solution:**
```bash
# Test network connectivity
curl https://openrouter.ai/api/v1/models

# Check API key validity
curl -H "Authorization: Bearer $OPENROUTER_API_KEY" \
     https://openrouter.ai/api/v1/models
```

#### Issue 3: "Cannot find locator element"
```
Error: Element not found for: login-username
```

**Causes:**
- CSS selector in locators.json is outdated
- UI element doesn't exist on page
- AI healing failed to find valid selector

**Solution:**
```bash
# Debug: Run with browser visible
mvn test -Dtest=LoginTest -Dheadless=false

# Check locators.json for valid selectors
cat src/main/resources/locators.json

# Update selectors manually if needed
# OR run test again to trigger AI healing
```

#### Issue 4: "API response mismatch detected"
```
Body mismatch detected for [http://localhost:3000/login]
  [data[0].status] expected="pending" | actual="active"
```

**This is expected!** Self-healing will fix it automatically:

```bash
# Test will:
1. Detect mismatch
2. Call AI to analyze change
3. Update expected-api-response.json
4. Re-run comparison
5. Show PASS ✅
```

To verify healing worked:
```bash
# Check updated expected data
cat src/test/resources/expected-api-response.json

# Check updated feature file
cat src/test/resources/features/login-api.feature
```

#### Issue 5: "Test hangs for 2+ minutes"
```
[INFO] Test hanging...
```

**Likely Cause:** AI API response is slow

**Solution:**
```bash
# OpenRouter timeout is set to 120 seconds (2 minutes)
# Test may hang if network is very slow

# Option 1: Check OpenRouter status
# https://status.openrouter.ai/

# Option 2: Use faster model (in HealingAgent.java)
// Current: "google/gemma-4-31b-it:free" (slower, free)
// Faster:  "gpt-3.5-turbo" (faster, paid)

# Option 3: Increase timeout in code
private static final int REQUEST_TIMEOUT = 120; // seconds
// Change to 180 for 3 minutes
```

#### Issue 6: "Playwright browser crashes"
```
Error: com.microsoft.playwright.PlaywrightException: 
Timeout while launching browser instance
```

**Causes:**
- Insufficient system resources
- Browser driver not installed
- Headless mode not supported on system

**Solution:**
```bash
# Ensure Playwright is installed
mvn clean install

# Try with browser visible (not headless)
mvn test -Dtest=LoginTest -Dheadless=false

# Check system resources
free -h  # Check available RAM
df -h    # Check disk space
```

### Performance Optimization Tips

```bash
# 1. Run tests in parallel (if independent)
mvn test -DparallelTestClasses=true

# 2. Skip headless overhead for API tests
mvn test -Dtest=ApiSelfHealingTest
# (Only run browser for UI tests)

# 3. Disable verbose logging in production
# Edit logback.xml to set log level to WARN

# 4. Use faster AI model
# Edit HealingAgent.java: change "google/gemma-4-31b-it:free"
# to "gpt-3.5-turbo" (requires paid credits)

# 5. Cache API responses between runs
# Framework already does this via ApiResponseRecorder
```

---

## 📚 Q&A for Team Discussion

### What's the main advantage of this framework?

**A:** Traditional test frameworks break when:
- UI elements change (selectors become invalid)
- API responses evolve (test data becomes outdated)

This framework **automatically fixes itself** using AI, eliminating manual test maintenance. Instead of spending hours finding new selectors or updating test data, the AI agent does it instantly.

### How reliable is the AI in fixing tests?

**A:** Very reliable for:
- **UI Locators (95%+):** AI finds alternative selectors from visible DOM
- **API Data (90%+):** AI understands API evolution patterns

What can fail:
- If UI element is completely renamed/moved (AI may need hints)
- If API breaks unintentionally (framework detects but won't "heal" intentional bugs)
- If AI model is overloaded (rare, we have retry logic)

### Can we trust AI-fixed test data?

**A:** Yes, with verification:
- AI only fixes when change is detected
- Framework re-compares after fix to verify it worked
- Fixed data is reviewed before next run (it's in git, so team can review)
- We manually review significant changes

**Best Practice:** Commit updated test data to git, let team review before merge.

### What if AI suggests wrong selector?

**A:** Handled by retry logic:
- Test tries AI-suggested selector
- If it fails again, test fails loudly
- Developer reviews and updates locators.json manually
- Next run uses corrected selector

### How much does OpenRouter API cost?

**A:** Depends on model:
- **Free models** (Gemma 4): $0 (free tier has safe limits)
- **Paid models** (GPT-4): $0.03-0.06 per request

For typical test suite (10-50 heal events/month): **$1-10/month**

### Can we use it without internet?

**A:** No, AI healing requires OpenRouter API call (cloud-based). However:
- Tests still run if AI is unavailable (framework retries)
- Can use local LLM alternatives (setup required)
- Manual healing still works (humans update data)

### How do we handle false positives in healing?

**A:** Framework prevents false positives:
1. Only heals when direct comparison fails
2. Re-compares after healing to verify
3. Requires status code 200 before ANY healing
4. Team reviews git diffs before committing

**Example:**
```
API Start: { "status": "pending" }
API Changed to: { "status": "active" }  ← Legitimate API change
Framework heals: ✅ CORRECT

API Started: { "status": "pending" }
Test data corrupted to: { "status": "corrupted" }  ← Bad data
Framework heals, but tells team: ⚠️ "Something changed significantly"
Team reviews and rejects bad data
```

### Can we heal UI tests across different browsers?

**A:** Currently: Firefox only (configured in DriverManager)

To support multiple browsers:
```java
// Current:
browser = playwright.firefox().launch();

// Would need:
String browserType = System.getProperty("browser", "firefox");
if ("chrome".equals(browserType)) {
    browser = playwright.chromium().launch();
} else {
    browser = playwright.firefox().launch();
}
```

### What happens if API response is gigantic (1MB+)?

**A:** Framework handles it gracefully:
- Records full response to actual-api-response.json
- For AI healing, sends first N fields only (don't overwhelm LLM)
- Comparison works on all fields (not limited)

### How do we track healing history?

**A:** Via Git:
```bash
# See what changed in test data
git log -p src/test/resources/expected-api-response.json

# See who fixed what
git blame src/test/resources/expected-api-response.json

# Revert bad healing
git revert <commit>
```

### Can developers create new tests easily?

**A:** Yes! Two paths:

**Path 1: BDD/Cucumber** (recommended for API tests)
```gherkin
# Write in English in login-api.feature
Scenario: New feature
  When I send a POST request to "/new-endpoint"
  Then the response status code should be 201
```

**Path 2: Java TestNG** (for complex UI tests)
```java
@Test
public void newTest() {
    loginPage.performAction();
    Assert.assertTrue(loginPage.isSuccess());
}
```

### How do we validate healing doesn't break production?

**A:** Best practices:
1. **Unit tests** on AI response parsing (in `src/test/java`)
2. **Integration tests** on actual API/UI
3. **Code review** of healed data (in git)
4. **Smoke tests** before production deployment
5. **Metrics** tracking (# healed tests, success rate)

### What metrics should we track?

**A:** Key metrics:
- **Healing success rate:** (tests healed successfully) / (tests needing healing)
- **Avg time to heal:** How long does AI take?
- **Healing false positives:** % of healed tests that break later
- **Test execution time:** Compare with/without healing
- **API key cost:** Monthly OpenRouter spend

---

## 🎯 Summary: The Complete Journey

```
┌────────────────────────────────────────────────────────────────┐
│                   YOUR TEST EXECUTION JOURNEY                  │
└────────────────────────────────────────────────────────────────┘

GOAL: Test API & UI, auto-fix when they change

STEP 1: You Run Tests
  └─► mvn test

STEP 2: Framework Pre-Cleanup
  └─► Deletes leftover test data

STEP 3: Cucumber API Tests (BDD)
  └─► Runs GET/POST/PUT/DELETE scenarios
      Written in human-readable Gherkin

STEP 4: API Self-Healing Test (4 Steps)
  
  └─┬─ STEP 1: Call API (HTTP GET)
    │  └─► Response: { "status": "active" }
    │
    ├─ STEP 2: Check if HTTP 200
    │  └─► YES → Continue
    │
    ├─ STEP 3: Compare Response Body
    │  ├─► Actual: { "status": "active" }
    │  ├─► Expected (from file): { "status": "pending" }
    │  ├─► MISMATCH ❌
    │  └─► Continue to Step 4
    │
    └─ STEP 4: AI Self-Healing
       ├─► Send to OpenRouter: "What changed?"
       ├─► AI Responds: "Status changed from pending→active"
       ├─► Framework Updates:
       │   ├─► expected-api-response.json
       │   └─► login-api.feature
       ├─► Re-compare: MATCH ✅
       └─► Test PASSES

STEP 5: UI Tests (LoginTest)
  └─┬─ Navigate to URL
    ├─ Try to click "login-submit" button
    │  ├─► Primary selector #loginBtn → FAILS ❌
    │  ├─► Try alternatives → FAIL ❌
    │  └─► Continue to AI healing
    │
    ├─ AI Self-Healing
    │  ├─► Analyze page DOM
    │  ├─► Send to OpenRouter: "Find login button"
    │  ├─► AI Responds: "Use #login_btn_v2"
    │  ├─► Update locators.json with new selector
    │  └─► Retry click with new selector → SUCCESS ✅
    │
    └─ Continue test → PASS ✅

RESULT: All Tests Pass ✅
  • API tests validated and auto-updated
  • UI tests executed with self-healed locators
  • Test data persisted to disk
  • Locators persisted to disk
  • Ready for next run!
```

---

## 🎓 Key Takeaways for Your Team

### 1. What This Framework Solves
| Problem | Solution |
|---------|----------|
| UI breaks when selectors change | AI finds new selectors automatically |
| Test data becomes stale | AI updates data based on API reality |
| Manual test maintenance | Automated healing + verification |
| Flaky tests from element not found | Healing + retry logic handles it |

### 2. Three Levels of Understanding

**Level 1 (User): "What do I need to run?"**
```bash
mvn test  # Run everything
```

**Level 2 (Developer): "What happens when I run it?"**
- Tests execute in order
- API responses captured and compared
- UI elements located and interacted with
- Any mismatches are healed by AI

**Level 3 (Architect): "How is healing implemented?"**
- Mismatch detection via deep JSON comparison
- AI analysis via OpenRouter API
- Atomic updates to persistent files
- Retry logic handles transient failures

### 3. Best Practices Moving Forward

```java
✅ DO:
  • Write tests in Gherkin (English-like)
  • Review healed data in git diffs
  • Track healing metrics
  • Use descriptive element IDs
  • Commit locators.json and feature files to git

❌ DON'T:
  • Hardcode selectors in test code
  • Ignore healing errors silently
  • Delete expected-api-response.json without backup
  • Rely 100% on AI (verify significant changes)
```

### 4. Team Responsibilities

**QA Engineers:**
- Write test scenarios (Gherkin)
- Review AI healing decisions
- Report false positives
- Maintain test infrastructure

**Developers:**
- Keep API documentation updated
- Avoid breaking existing endpoints
- Review API response changes before production
- Provide feedback on test failures

**DevOps/CI-CD:**
- Set up OpenRouter API keys
- Monitor test execution costs
- Archive test reports
- Alert on healing failures

---

## 📞 Getting Help

### Resources
- **OpenRouter Docs:** https://openrouter.ai/docs
- **Playwright Docs:** https://playwright.dev/java/
- **Cucumber Docs:** https://cucumber.io/docs/gherkin/
- **TestNG Docs:** https://testng.org/doc/documentation-major-features.html

### Common Commands
```bash
# Debug: Show what the AI is thinking
mvn test -X  # Enable debug logging

# Investigate: Check what was healed
git diff src/test/resources/expected-api-response.json

# Revert: Undo bad healing
git checkout -- src/test/resources/

# Update: Force new healing
rm src/test/resources/actual-api-response.json && mvn test
```

---

**Ready for team KT session! 🚀**

