# Quick Start Guide — Refactored API Testing Workflow

## TL;DR

The API testing workflow has been refactored into a clean 6-step orchestrated process. You now just call:

```java
orchestrator.testEndpoint("http://localhost:3000/login");
```

Instead of managing 4 separate test methods with complex state.

---

## Directory Structure After Refactoring

```
✅ NEW STRUCTURE:
src/test/resources/
├── baselines/                          ← Per-endpoint baseline responses
│   ├── GET_localhost_3000_login.json
│   └── ... (auto-generated on first run)
└── features/
    └── login-api.feature

❌ REMOVED:
└── expected-api-response.json          (Was confusing, now per-endpoint)
└── actual-api-response.json            (Was auto-generated, no longer needed)
```

---

## New Classes

### 1. `ApiTestOrchestrator` - Main Workflow Engine
```java
ApiTestOrchestrator orchestrator = new ApiTestOrchestrator();

// Test single endpoint
EndpointTestResult result = orchestrator.testEndpoint(url);

// Test multiple endpoints
List<EndpointTestResult> results = orchestrator.testEndpoints(Arrays.asList(
    "http://localhost:3000/login",
    "http://localhost:3000/users"
));

// Print summary
orchestrator.printSummary();
```

**Handles:**
- ✓ API calls
- ✓ Status validation
- ✓ Baseline storage/loading
- ✓ Response comparison
- ✓ AI self-healing on mismatch
- ✓ Re-validation after healing
- ✓ Result reporting

### 2. `BaselineManager` - Baseline File Management
```java
BaselineManager baselines = new BaselineManager();

// Check if baseline exists
boolean exists = baselines.baselineExists("GET::http://localhost:3000/login");

// Load baseline
String baseline = baselines.loadBaseline(key);

// Save new baseline
baselines.saveBaseline(key, responseBody);

// List all baselines
List<String> allKeys = baselines.listBaselines();
```

**Storage Location:** `src/test/resources/baselines/`

---

## The 6-Step Workflow

```
1. CALL API          → Make HTTP request
   ↓ (fail) → FAILED with network error
   
2. CHECK STATUS      → Is HTTP 200?
   ↓ (not 200) → FAILED with status code
   
3. CHECK BASELINE    → Does baseline file exist?
   ↓ (no) → SAVE baseline → PASSED ✓
   
4. COMPARE           → Does response match baseline?
   ↓ (yes) → PASSED ✓
   
5. AI HEAL           → Call LLM to fix mismatch
   ↓ (fail) → FAILED with reason
   
6. RE-VALIDATE       → Does new response match?
   ↓ (yes) → PASSED ✓
   ↓ (no) → FAILED
```

---

## Old Test Code vs New

### Before (4 methods, 75+ lines, complex state)
```java
@Test(priority = 1)
public void step1_triggerApiRequest() { ... }

@Test(priority = 2, dependsOnMethods = "step1_...")
public void step2_validateStatusCode() { ... }

@Test(priority = 3, dependsOnMethods = "step2_...")
public void step3_saveAndValidateBody() throws IOException { ... }

@Test(priority = 4, dependsOnMethods = "step3_...")
public void step4_aiSelfHeal() { ... }

// Plus shared variables: lastResponse, lastCompareResult, pendingHeal
```

### After (1 method, 10 lines, clean)
```java
@Test
public void testCompleteWorkflow() {
    EndpointTestResult result = orchestrator.testEndpoint(url);
    
    Assert.assertEquals(result.status, TestStatus.PASSED,
            "Test failed: " + result.reason);
}
```

---

## Running Tests

### Standard Run (Auto-learns baselines on first run)
```bash
mvn test -Dtest=ApiSelfHealingTest -Dtest.api.url=http://localhost:3000
```

**Output:**
```
═════════════════════════════════════════════════════════
Testing Endpoint: http://localhost:3000/login
═════════════════════════════════════════════════════════
STEP 1: Initiating API call...
✅ HTTP 200 OK

STEP 3: Checking stored baseline...
ℹ️  First-time execution — saving response as baseline
✅ Baseline saved

╔════════════════════════════════════════════════════════════╗
║                     TEST SUMMARY                           ║
╚════════════════════════════════════════════════════════════╝
Total Endpoints:  1
✅ Passed:         1 (all)
```

### Subsequent Runs (Validates against baseline)
```bash
mvn test -Dtest=ApiSelfHealingTest -Dtest.api.url=http://localhost:3000
```

**Output:**
```
STEP 4: Comparing response with baseline...
✅ Response matches baseline — no healing needed

Result: ✅ PASSED
```

### When Healing Triggers (Mismatch detected)
```
STEP 4: Comparing response with baseline...
⚠️  Response mismatch detected

STEP 5: Triggering AI self-healing...
Sending request to OpenAI (model=gpt-4o-mini-2024-07-18)...
✅ Self-healing applied

STEP 6: Re-validating after healing...
✅ Re-validation passed after healing

Result: ✅ PASSED (🔧 healed)
```

---

## Result Types

```java
EndpointTestResult result = orchestrator.testEndpoint(url);

// Check status
if (result.status == TestStatus.PASSED) {
    System.out.println("✅ " + result.reason);
} else {
    System.out.println("❌ " + result.reason);
}

// Check if healed
if (result.isHealed()) {
    System.out.println("🔧 Healed via AI");
}

// View details
System.out.println("HTTP Status: " + result.responseStatus);
System.out.println("Response: " + result.responseBody);
System.out.println("Diff: " + result.diff);
```

**Possible Statuses:**
- `PASSED` - Response valid/matched/healed
- `FAILED` - Network error/wrong status/mismatch (even after healing)

**Possible Reasons:**
- "Baseline created (first-time execution)"
- "Response matches baseline"
- "Healing successful — response now matches baseline"
- "HTTP 404 (expected 200)"
- "Network error: ..."
- "Self-healing failed"

---

## Key Points

### Baseline Files
- Location: `src/test/resources/baselines/`
- Created: First test run automatically
- Format: One JSON file per endpoint
- Naming: `GET_localhost_3000_login.json`
- Managed by: `BaselineManager`

### Multiple Endpoints
```java
List<EndpointTestResult> results = orchestrator.testEndpoints(
    Arrays.asList(
        "http://localhost:3000/login",
        "http://localhost:3000/profile"
    )
);

// All tests run, results collected
// Failed endpoints don't stop execution
results.forEach(r -> System.out.println(r.url + " → " + r.status));
```

### AI Self-Healing
- Model: `gpt-4o-mini-2024-07-18` (via OpenAI)
- API Key: `OPENAI_API_KEY` environment variable
- Handles: JSON updates + Cucumber feature file patches
- Retry: 3 attempts with exponential backoff

### Error Handling
- Network errors → FAILED
- Non-200 status → FAILED (continues to next endpoint)
- Mismatch on first run → Saves as baseline (PASSED)
- Mismatch later → Triggers healing
- Failed healing → FAILED

---

## Files Changed

### ✅ Created (New)
```
src/main/java/api/ApiTestOrchestrator.java      (240 lines)
src/main/java/api/BaselineManager.java          (150 lines)
src/test/resources/baselines/                   (directory)
API_TESTING_WORKFLOW.md                         (design doc)
REFACTORING_SUMMARY.md                          (this detail)
QUICK_START_GUIDE.md                            (quick ref)
```

### ✏️ Modified
```
src/test/java/tests/ApiSelfHealingTest.java     (refactored to use orchestrator)
```

### ❌ Deleted (Old)
```
src/test/resources/expected-api-response.json   (moved to baselines/)
src/test/resources/actual-api-response.json     (no longer needed)
```

---

## Workflow Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                   API Test Orchestrator                          │
│                                                                  │
│  testEndpoint("http://localhost:3000/login")                   │
│         ↓                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 1: Call API                                        │    │
│  │ → client.get(url)                                       │    │
│  │ → If error: return FAILED                              │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 2: Validate Status                                 │    │
│  │ → Is HTTP 200?                                          │    │
│  │ → If not: return FAILED                                │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 3: Check Baseline                                  │    │
│  │ → baselineManager.baselineExists(key)?                 │    │
│  │ → If no: save & return PASSED                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 4: Compare                                         │    │
│  │ → Load baseline                                         │    │
│  │ → Compare with actual                                  │    │
│  │ → If match: return PASSED                              │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓ (if mismatch)                                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 5: AI Self-Heal                                    │    │
│  │ → selfHealer.heal(url, actual, diff)                   │    │
│  │ → If fail: return FAILED                               │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓ (if healed)                                           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ STEP 6: Re-validate                                     │    │
│  │ → Load updated baseline                                 │    │
│  │ → Compare again                                         │    │
│  │ → If match: return PASSED (healed)                      │    │
│  │ → Else: return FAILED                                   │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                        │
│  Return: EndpointTestResult {                                  │
│    url, status, reason, responseStatus,                        │
│    responseBody, diff, healed, ...                             │
│  }                                                              │
└──────────────────────────────────────────────────────────────────┘
```

---

## Next Steps

1. **Run your first test**
   ```bash
   mvn test -Dtest=ApiSelfHealingTest -Dtest.api.url=http://localhost:3000
   ```

2. **Verify baseline created**
   ```bash
   ls src/test/resources/baselines/
   cat src/test/resources/baselines/GET_localhost_3000_login.json | jq .
   ```

3. **Commit baselines**
   ```bash
   git add src/test/resources/baselines/
   git commit -m "Add API baseline responses"
   ```

4. **Run again** (should pass without healing)
   ```bash
   mvn test -Dtest=ApiSelfHealingTest -Dtest.api.url=http://localhost:3000
   ```

---

## Need Help?

- **Detailed Design:** See `API_TESTING_WORKFLOW.md`
- **What Changed:** See `REFACTORING_SUMMARY.md`
- **Classes:** `ApiTestOrchestrator`, `BaselineManager`
- **Tests:** `src/test/java/tests/ApiSelfHealingTest.java`

Happy testing! 🚀
