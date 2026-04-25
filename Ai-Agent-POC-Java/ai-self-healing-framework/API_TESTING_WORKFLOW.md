# API Testing Workflow — Refactored Design

## Overview

The API testing workflow has been refactored to follow a clear, structured 6-step process for comprehensive API validation with AI-powered self-healing capabilities.

## Workflow Steps

### 1. **Initiate API Testing**
- Start execution for all configured API endpoints
- Call the `ApiTestOrchestrator.testEndpoint(url)` method
- Handles network-level error catching

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 1

---

### 2. **Validate Response Status**
- Check if HTTP response status is 200
- **If NOT 200:**
  - Mark test as **FAILED** for that endpoint
  - Record status code and reason
  - **Continue** with remaining endpoints (don't stop suite)
- **If 200:**
  - Proceed to next step

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 2

**Status Codes Handled:**
- `200` → Continue to body validation
- `4xx` → Mark FAILED, continue
- `5xx` → Mark FAILED, continue
- Network error → Mark FAILED, continue

---

### 3. **Check Stored Baseline Data**
- Query `BaselineManager` to check if baseline exists for endpoint
- **If NO baseline exists (first-time execution):**
  - Save current response as baseline
  - Mark test as **PASSED**
  - Return "Baseline created (first-time execution)"
  - No further validation needed
- **If baseline exists:**
  - Proceed to comparison step

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 3

**Baseline Storage:**
- Location: `src/test/resources/baselines/`
- File naming: `GET_localhost_3000_login.json`
- Format: Raw JSON response body

---

### 4. **Compare with Existing Baseline**
- Load stored baseline from `BaselineManager`
- Perform deep JSON comparison using `ApiValidator.compareBody()`
- **If responses match:**
  - Mark test as **PASSED**
  - Skip self-healing
  - Return "Response matches baseline"
- **If responses differ:**
  - Collect difference map
  - Proceed to self-healing step

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 4

**Comparison Method:**
- Deep field-level comparison (only fields in baseline)
- Extra fields in actual response are allowed (non-breaking additions)
- Returns map of mismatched fields

---

### 5. **Trigger Self-Healing on Mismatch**
- Invoke `ApiSelfHealer.heal()` with:
  - Endpoint URL
  - Actual response body
  - Difference summary
- **Self-Healer LLM Agent (GPT-4o-mini via OpenAI):**
  - Analyzes the difference
  - Generates updated baseline JSON
  - Generates Cucumber feature file patches
  - Updates files on disk
- **If healing succeeds:**
  - Mark healing as performed
  - Proceed to re-validation
- **If healing fails:**
  - Mark test as **FAILED**
  - Return reason for failure

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 5

**Self-Healer Features:**
- Uses `ApiHealingAgent` (calls OpenAI GPT-4o-mini)
- Updates `src/test/resources/baselines/` files
- Patches `src/test/resources/features/login-api.feature`
- 3-attempt retry with exponential backoff
- 120-second timeout for LLM response

---

### 6. **Re-run Updated Tests**
- After successful healing, re-load the updated baseline
- Perform comparison with actual response again
- **If re-comparison passes:**
  - Mark test as **PASSED**
  - Return "Healing successful — response now matches baseline"
- **If still mismatches:**
  - Mark test as **FAILED**
  - Return "Post-healing validation failed"

**Location:** `ApiTestOrchestrator.testEndpoint()` - STEP 6

---

## Architecture

### New Classes

#### `BaselineManager`
**Responsibility:** Manage baseline JSON files on disk

**Methods:**
- `baselineExists(key)` → Check if baseline stored
- `loadBaseline(key)` → Retrieve stored baseline
- `saveBaseline(key, body)` → Persist baseline
- `deleteAllBaselines()` → Cleanup (testing only)
- `listBaselines()` → List all stored baselines

**Storage:**
- Directory: `src/test/resources/baselines/`
- File format: `{METHOD}_{URL_SAFE}.json`

#### `ApiTestOrchestrator`
**Responsibility:** Orchestrate the 6-step workflow

**Methods:**
- `testEndpoint(url)` → Test single endpoint
- `testEndpoints(urls)` → Test multiple endpoints
- `printSummary()` → Report results

**Result Types:**
- `EndpointTestResult` → Per-endpoint results
- `TestStatus.PASSED` / `TestStatus.FAILED`

### Modified Classes

#### `ApiSelfHealingTest`
**Changes:**
- Now uses `ApiTestOrchestrator` instead of manual 4-step flow
- Single unified test method that calls orchestrator
- Optional multi-endpoint test

**Before:** 4 separate test methods (`@Test` with priority 1-4)
**After:** 2 unified test methods using orchestrator

---

## JSON Files Management

### Old Approach (Before Refactoring)
```
src/test/resources/
├── expected-api-response.json   (Developer-provided single baseline)
├── actual-api-response.json     (Auto-generated per test run)
└── baselines/                   (NOT USED)
```

**Issues:**
- Single `expected-api-response.json` for all endpoints
- Multiple test runs overwrite each other
- No versioning or history
- Confusing: `actual-` vs `expected-` purpose unclear

### New Approach (After Refactoring)
```
src/test/resources/
├── baselines/                      (Managed by BaselineManager)
│   ├── GET_localhost_3000_login.json
│   ├── GET_localhost_3000_users.json
│   └── POST_localhost_3000_login.json
└── features/
    └── login-api.feature           (Updated by self-healer)
```

**Benefits:**
- Per-endpoint baseline isolation
- Clear naming convention
- No conflicts between tests
- Automatic file organization
- Supports multiple API endpoints

### Migration Guide

**To delete old files:**
```bash
rm src/test/resources/expected-api-response.json
rm src/test/resources/actual-api-response.json
```

**First run will:**
- Create `src/test/resources/baselines/` directory
- Auto-generate baseline files for each endpoint
- Print summary of created baselines

---

## Test Execution Flow

### Single Endpoint Example
```
ApiTestOrchestrator.testEndpoint("http://localhost:3000/login")
    ↓
[STEP 1] Call GET /login
    ↓ (if network error) → FAILED
    ↓ (if success)
[STEP 2] Check HTTP 200
    ↓ (if not 200) → FAILED
    ↓ (if 200)
[STEP 3] Check baseline exists?
    ↓ (if NO) → Save baseline → PASSED
    ↓ (if YES)
[STEP 4] Compare with baseline
    ↓ (if match) → PASSED
    ↓ (if diff)
[STEP 5] AI Self-Heal
    ↓ (if fail) → FAILED
    ↓ (if success)
[STEP 6] Re-validate
    ↓ (if match) → PASSED
    ↓ (if still diff) → FAILED
```

### Multi-Endpoint Example
```
ApiTestOrchestrator.testEndpoints([
    "http://localhost:3000/login",
    "http://localhost:3000/users"
])
    ↓
For each URL: Execute flow above
    ↓
Print summary: √ 2 passed, ✗ 0 failed, 🔧 0 healed
```

---

## Configuration

### API Test Orchestrator Setup
```java
// In test class
private ApiTestOrchestrator orchestrator;

@BeforeClass
public void setUp() {
    orchestrator = new ApiTestOrchestrator();
    // Internally initializes:
    // - ApiClient
    // - ApiValidator
    // - ApiSelfHealer
    // - BaselineManager
    // - ApiResponseRecorder
}
```

### Running Tests
```bash
# Single endpoint API test
mvn test -Dtest.api.url=http://localhost:3000

# API + UI combined
mvn test -Dtest.api.url=http://localhost:3000 -Dheadless=true

# Clean baselines and re-learn
rm -rf src/test/resources/baselines/
mvn test -Dtest.api.url=http://localhost:3000
```

---

## Logging and Diagnostics

### Log Output Example
```
═════════════════════════════════════════════════════════
Testing Endpoint: http://localhost:3000/login
═════════════════════════════════════════════════════════
STEP 1: Initiating API call...
→ GET http://localhost:3000/login
← HTTP 200 (455 chars)
✅ HTTP 200 OK

STEP 2: Validating response status...
✅ HTTP 200 OK

STEP 3: Checking stored baseline...
✅ Baseline exists, proceeding with comparison

STEP 4: Comparing response with baseline...
✅ Response matches baseline — no healing needed

╔════════════════════════════════════════════════════════════╗
║                     TEST SUMMARY                           ║
╚════════════════════════════════════════════════════════════╝
Total Endpoints:  1
✅ Passed:         1 (all)
❌ Failed:         0
🔧 Healed:         0 (via AI)

✅ http://localhost:3000/login → Response matches baseline
```

---

## Benefits of Refactored Workflow

1. **Clear Step Separation**
   - Each step has well-defined responsibility
   - Easy to understand flow
   - Simple debugging

2. **Graceful Degradation**
   - Non-200 responses don't stop entire suite
   - Results for all endpoints are collected

3. **Automatic Baseline Learning**
   - First run automatically creates baselines
   - No manual baseline file creation needed
   - Subsequent runs validate against learned baseline

4. **AI-powered Self-Healing**
   - Mismatches trigger intelligent fixes
   - Both JSON baseline and Cucumber feature files updated
   - Re-validation ensures fix works

5. **Multi-Endpoint Support**
   - Easy to test multiple endpoints
   - Per-endpoint baseline isolation
   - Comprehensive summary report

6. **Better Organization**
   - Baselines in dedicated `src/test/resources/baselines/` directory
   - Clear file naming convention
   - No confusion between "expected" and "actual"

---

## Troubleshooting

### Issue: "Baseline directory not found"
**Solution:**
```bash
mkdir -p src/test/resources/baselines/
```

### Issue: "OpenAI API key missing"
**Solution:**
```bash
# Update .env
OPENAI_API_KEY=sk-your-key-here
```

### Issue: "Test still fails after self-healing"
**Check:**
1. LLM response logged: `log.info("OpenAI response body: {}")`
2. Feature file update: `src/test/resources/features/login-api.feature`
3. Baseline file update: `src/test/resources/baselines/`

---

## Next Steps

1. **Integration with CI/CD**
   - Add to GitHub Actions / Jenkins
   - Upload baseline files to version control
   - Compare baselines across builds

2. **Advanced Comparisons**
   - Time-series baseline comparisons
   - Tolerance thresholds for numeric fields
   - Pattern-based matching

3. **Reporting Dashboard**
   - HTML test report generation
   - Visualization of baseline changes
   - Trend analysis over time

---
