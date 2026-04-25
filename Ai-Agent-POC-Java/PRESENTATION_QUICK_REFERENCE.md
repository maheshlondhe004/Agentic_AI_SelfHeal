# AI Self-Healing Framework - Quick Reference & Presentation Slides

**For Quick Lookups & Presentation Slides Outline**

---

## 📊 Slide 1: Problem & Solution

### Problem (Before This Framework)
```
❌ UI Selector Breaks
   Developer: "Why did my test fail?"
   Debug: "Element #username no longer exists in HTML"
   Action: Manually hunt for new selector in browser
   Time: 30 minutes

❌ API Response Changes
   Test: "Expected { status: 'pending' }, got { status: 'active' }"
   Action: Manually update test data files
   Time: 45 minutes

❌ Total Maintenance Time: Multiple hours per month
```

### Solution (With This Framework)
```
✅ Smart Detection
   Framework: "UI selector failed"
   AI: "Analyzing page DOM..."
   AI: "Found new selector: #new_username"
   Framework: "Updating locators.json and retrying"
   Result: Test passes automatically

✅ Automatic Healing
   Framework: "API data mismatch detected"
   AI: "Analyzing diff..."
   AI: "Status field evolved: 'pending' → 'active' (legitimate change)"
   Framework: "Updating expected data and feature file"
   Result: Test passes automatically

✅ Total Maintenance Time: Near zero (AI handles it)
```

---

## 🎬 Slide 2: How the Framework Works (30-Second Version)

```
┌─────────────────────────────────────────┐
│  YOU WRITE TEST CASES (BDD - English)    │
│  "Given API is at localhost:3000"        │
│  "When I send GET /login"                │
│  "Then response status is 200"           │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│  FRAMEWORK RUNS TESTS                   │
│  • Sends HTTP requests                  │
│  • Extracts UI elements                 │
│  • Compares responses                   │
└────────────┬────────────────────────────┘
             │
             ▼
        DOES TEST PASS?
        ├─ YES → ✅ Great! Done.
        └─ NO ↓
             │
             ▼
┌─────────────────────────────────────────┐
│  AI ANALYZES FAILURE                    │
│  • Calls OpenRouter (Claude AI)         │
│  • Analyzes what changed                │
│  • Suggests fix                         │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│  FRAMEWORK APPLIES FIX                  │
│  • Updates test data files              │
│  • Updates test assertions              │
│  • Retries test                         │
└────────────┬────────────────────────────┘
             │
             ▼
        ✅ TEST PASSES
        (Framework fixed it automatically)
```

---

## 🔄 Slide 3: The 4-Step API Test Flow

```
STEP 1: TRIGGER API REQUEST
┌──────────────────────────────────────────┐
│ GET http://localhost:3000/login          │
│ → Response: HTTP 200, Body: {...}        │
└──────────────────────────────────────────┘

STEP 2: VALIDATE STATUS CODE
┌──────────────────────────────────────────┐
│ Is HTTP 200?                             │
│ ✅ YES → Continue                        │
│ ❌ NO → STOP (Don't heal invalid status) │
└──────────────────────────────────────────┘

STEP 3: SAVE & VALIDATE RESPONSE BODY
┌──────────────────────────────────────────┐
│ Save: actual-api-response.json           │
│ Compare: actual vs expected              │
│ ✅ Match → Test PASSES                   │
│ ❌ Mismatch → Continue to Step 4         │
└──────────────────────────────────────────┘

STEP 4: AI SELF-HEALING (if needed)
┌──────────────────────────────────────────┐
│ 1. Call OpenRouter API (Claude)          │
│ 2. AI analyzes what changed              │
│ 3. Framework updates expected data       │
│ 4. Re-compare: Now PASSES ✅             │
│ 5. Persist changes to disk               │
└──────────────────────────────────────────┘
```

---

## 🌐 Slide 4: API Testing (BDD Style)

### What is BDD?
```
BDD = Behavior Driven Development
     = Write tests in English (not Java code)
     = Business analysts can understand tests
     = Non-technical stakeholders understand

Example (Gherkin - English):
┌──────────────────────────────────────────┐
│ Scenario: Create a new login record      │
│   When I send a POST request to "/login" │
│   Then the response status should be 201 │
│   And the email field should be saved    │
└──────────────────────────────────────────┘

Behind the Scenes:
├─ Feature file (login-api.feature)
├─ Step definitions (LoginApiSteps.java)
└─ Execution engine (CucumberRunner.java)
```

### CRUD Operations Tested
```
C (Create)  → POST /login          → HTTP 201
R (Read)    → GET /login/:id       → HTTP 200
U (Update)  → PUT /login/:id       → HTTP 200
D (Delete)  → DELETE /login/:id    → HTTP 200
```

---

## 🎮 Slide 5: UI Testing (Self-Healing)

### Normal UI Test
```
1. Find element by CSS selector #username
2. Type "testuser" into it
3. Find button #loginBtn
4. Click it
5. Assert: "Welcome" message appears
```

### Self-Healing UI Test (How We Do It)
```
1. Find element by ID "login-username"
   ├─ Try primary selector: #username
   │  ├─ Found? → Use it ✅
   │  └─ Not found? Try alternatives
   │     ├─ input[name='username'] → Found? ✅
   │     └─ input[type='text'] → Found? ✅
   ├─ Nothing worked? → Call AI
   │  ├─ AI: "Analyze page DOM"
   │  ├─ AI: "Found new selector: #user_input"
   │  ├─ Framework updates locators.json
   │  └─ Retry with new selector → ✅
   │
2. Type "testuser" (same as before)
3. Find element by ID "login-submit"
   ├─ Try primary selector: #loginBtn
   │  ├─ Found? → Click ✅
   │  └─ Not found? (same healing as above)
   │
4. Type verification (same as before)
```

---

## 📁 Slide 6: Key Files Reference

```
locators.json
├─ What: Database of UI element selectors
├─ When Updated: When AI finds new selector
├─ Who Updates: Framework automatically
└─ Content:
   {
     "login-username": {
       "primary": "#username",
       "alternatives": ["input[name='username']", ...],
       "description": "Username input field"
     }
   }

expected-api-response.json
├─ What: Expected API response data (baseline)
├─ When Updated: When API changes (AI heals)
├─ Who Updates: Framework automatically
└─ Content:
   {
     "success": true,
     "data": { "status": "active", ... }
   }

actual-api-response.json
├─ What: Recorded API responses from latest run
├─ When Updated: Every test run (Step 3)
├─ Who Updates: Framework automatically
└─ Content:
   {
     "endpoints": [{
       "url": "/login",
       "method": "GET",
       "statusCode": 200,
       "changed": false,
       "responseBody": { ... }
     }]
   }

login-api.feature
├─ What: BDD test scenarios (in Gherkin/English)
├─ When Updated: When assertions need to change (AI heals)
├─ Who Updates: Framework automatically
└─ Content:
   Scenario: POST /login creates a record
     When I send a POST request to "/login"
     Then response status should be 201
```

---

## 🤖 Slide 7: AI Integration (OpenRouter)

### What is OpenRouter?
```
OpenRouter = Gateway to multiple AI models
           = Unified API (like OpenAI but multi-provider)
           = Access to: Claude, ChatGPT, Gemma, etc.
           = Charges per API call

┌─────────────┐
│  Your Code  │
└──────┬──────┘
       │
       ▼
┌──────────────────┐      ┌─────────────────────┐
│  OpenRouter API │ ───▶ │  AI Model Selected  │
│  openrouter.ai │      │  (Gemma 4 / Claude) │
└──────────────────┘      └─────────────────────┘
```

### API Call Flow
```
Step 1: Build Request
{
  "model": "google/gemma-4-31b-it:free",
  "messages": [
    { "role": "system", "content": "You are a test healer..." },
    { "role": "user", "content": "Element failed: #username not found..." }
  ]
}

Step 2: Send to OpenRouter (120 second timeout)

Step 3: Receive Response
{
  "selector": "#new_username",
  "confidence": "HIGH",
  "reason": "Element ID changed from 'username' to 'new_username'"
}

Step 4: Framework applies fix
├─ Update locators.json with new selector
├─ Retry test with new selector
└─ Verify it works
```

### Cost Estimate
```
Free Models (Gemma):   $0/month (free tier)
Typical Usage:         10-50 heal events per month
Estimated Cost:        $0-10/month for paid models
```

---

## 🚀 Slide 8: Running Tests (Commands)

### Quick Commands Reference

```bash
# Run everything
$ mvn test

# Run just API tests
$ mvn test -Dtest=ApiSelfHealingTest

# Run just UI tests (with browser visible)
$ mvn test -Dtest=LoginTest -Dheadless=false

# Run specific method
$ mvn test -Dtest=ApiSelfHealingTest#step1_triggerApiRequest

# Run Cucumber tests only
$ mvn test -Dtest=CucumberRunner

# With debug logging
$ mvn test -X

# Skip tests (just build)
$ mvn clean install -DskipTests
```

### Environment Setup
```bash
# Set API Key (choose one method)

# Method 1: Environment variable
export OPENROUTER_API_KEY=sk-xxx...

# Method 2: .env file in project root
# OPENROUTER_API_KEY=sk-xxx...

# Method 3: Maven property
mvn test -DOPENROUTER_API_KEY=sk-xxx...

# Verify setup
echo $OPENROUTER_API_KEY
```

---

## 📊 Slide 9: Test Execution Sequence

```
┌─────────────────────────────────────────┐
│ mvn test (You run this)                 │
└────────────────────┬────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
   ┌────────┐   ┌─────────┐   ┌─────────────┐
   │CLEANUP │   │CUCUMBER │   │  API SELF-  │
   │(Pre)   │   │ API     │   │  HEALING    │
   │        │   │ CRUD    │   │  (4-step)   │
   └────────┘   └─────────┘   └─────────────┘
        │            │            │
        └────────────┼────────────┘
                     │
                     ▼
        ┌────────────────────────┐
        │  Test Results Report   │
        ├────────────────────────┤
        │ PASS: 23               │
        │ FAIL: 0                │
        │ SKIPPED: 0             │
        │ Time: 45 seconds       │
        └────────────────────────┘
```

---

## 📈 Slide 10: Metrics to Track

### Key Performance Indicators (KPIs)

```
1. HEALING SUCCESS RATE
   ┌─────────────────────────────┐
   │ Tests Healed Successfully   │ = 95%+ is good
   │ ÷ Tests Needing Healing     │
   └─────────────────────────────┘

2. FALSE POSITIVE RATE
   ┌─────────────────────────────┐
   │ Healed Tests That Break     │ = <5% is good
   │ ÷ Total Healed Tests        │
   └─────────────────────────────┘

3. TEST EXECUTION TIME
   Before AI Healing: 2+ hours (including manual fixes)
   After AI Healing: 45 minutes (full auto-remediation)

4. COST PER TEST
   OpenRouter Usage: $5-15/month for typical suite
   Manual Maintenance Saved: 10+ hours/month

5. DEVELOPER PRODUCTIVITY
   Before: Spend 1 hour fixing broken tests
   After: Framework fixes, developer reviews (5 min)
   Time Saved: 55 minutes per incident
```

---

## ⚡ Slide 11: Troubleshooting Quick Guide

```
PROBLEM                    SYMPTOM                   SOLUTION
────────────────────────────────────────────────────────────────
No API Key                 "API key not found"       Add to .env file

Network Error              "Connection timeout"      Check internet
                                                     Check OpenRouter status

Slow Healing               "Test hangs 2+ min"       Normal (AI can be slow)
                                                     Increase timeout if needed

Element Not Found          "Cannot locate element"   Check with browser visible
                                                     (-Dheadless=false)

Healing Failed             "Mismatch still exists"   Review AI response
                                                     Update manually

Test Data Corrupt          "Unexpected values"       Review git diff
                                                     Revert bad changes
```

---

## 🎯 Slide 12: Key Takeaways

### What We Solved
```
❌ Problem #1: "My tests break whenever UI changes"
✅ Solution: AI finds new selectors automatically

❌ Problem #2: "Test data becomes stale over time"
✅ Solution: AI updates data based on API reality

❌ Problem #3: "Test maintenance is a huge burden"
✅ Solution: Automated healing reduces manual work by 90%

❌ Problem #4: "We can't keep up with API evolution"
✅ Solution: Framework adapts as API changes
```

### How It Works in 3 Steps
```
1️⃣  TEST RUNS
    • Sends API request
    • Records response
    • Finds UI elements

2️⃣  DETECTS MISMATCH
    • Compares actual vs expected
    • Finds broken selectors
    • Identifies what changed

3️⃣  CALLS AI TO HEAL
    • AI analyzes changes
    • Suggests fixes
    • Framework applies fixes
    • Tests pass automatically ✅
```

### Best Practices
```
✅ Write tests in BDD (Gherkin)
✅ Review healed data in git diffs
✅ Track healing metrics
✅ Use descriptive element IDs
✅ Commit test files to git
✅ Monitor AI costs monthly

❌ Don't hardcode selectors
❌ Don't ignore healing errors
❌ Don't delete expected data without backup
```

---

## 📚 Slide 13: Resources & Next Steps

### Documentation Files
```
📄 TECHNICAL_KT_COMPREHENSIVE_GUIDE.md
   (This document - complete explanation)

📄 PRD_Self_Healing_Agentic_AI_Testing.md
   (Product Requirements - what we're building)

📄 TRD_Self_Healing_Agentic_AI_Testing.md
   (Technical Requirements - how we build it)
```

### External Resources
```
🌐 OpenRouter Docs
   https://openrouter.ai/docs

🌐 Playwright Documentation
   https://playwright.dev/java/

🌐 Cucumber/Gherkin Guide
   https://cucumber.io/docs/gherkin/

🌐 TestNG Documentation
   https://testng.org/doc/
```

### Next Steps
```
1. Clone repository and setup environment
2. Read TECHNICAL_KT_COMPREHENSIVE_GUIDE.md
3. Run: mvn clean test
4. Review generated test reports
5. Try modifying a test scenario
6. Observe AI healing in action
7. Ask questions & get feedback
```

---

## 💡 Slide 14: Common Questions Answered

### Q: Will this work for all my existing tests?
```
A: Partially. Best for:
   ✅ API CRUD tests (100% compatible)
   ✅ UI login/navigation tests (95% compatible)
   ❌ Performance tests (not applicable)
   ❌ Security scanning tests (not applicable)
```

### Q: How much does it cost?
```
A: Depends on your choice of AI:
   • Free Models (Gemma): $0
   • Paid Models (Claude): $0.01-0.10 per request
   
   Typical calculation:
   • 50 tests per suite
   • 5% fail and need healing
   • 2.5 requests per month
   • Cost: $0-5/month
```

### Q: Can I use this without internet?
```
A: No, AI healing requires OpenRouter cloud API.
   
   But:
   • Tests still run offline (no healing)
   • Can use local LLM (setup required)
   • Manual healing still works
```

### Q: What if AI suggests wrong fix?
```
A: Framework catches it:
   1. AI suggests fix
   2. Framework applies fix
   3. Framework re-tests with fix
   4. If still fails → Test fails clearly
   5. Developer reviews and updates manually
```

### Q: How do I know what was healed?
```
A: Check git:
   $ git log -p src/test/resources/
   
   See all changes, who made them, when.
```

---

## 🎓 Slide 15: Training & Onboarding Path

### For QA Testers
```
Week 1: Basics
├─ Run the test suite (mvn test)
├─ Read test reports
└─ Understand BDD Gherkin syntax

Week 2: Writing Tests
├─ Write first Gherkin scenario
├─ Map steps to Java implementations
└─ Debug failing tests

Week 3: Self-Healing
├─ Observe AI healing in action
├─ Review healed data
├─ Validate healing correctness
└─ Make manual corrections if needed
```

### For Developers
```
Week 1: Architecture
├─ Understand project structure
├─ Read TECHNICAL_KT_COMPREHENSIVE_GUIDE.md
└─ Review main classes

Week 2: Integration
├─ Add new API endpoints
├─ Update test feature files
├─ Ensure backward compatibility
└─ Run test suite

Week 3: Maintenance
├─ Monitor healing metrics
├─ Review AI responses
├─ Update framework as needed
└─ Optimize performance
```

---

## 🎬 Presentation Flow (90 Minutes)

```
SEGMENT 1: PROBLEM & SOLUTION (10 min)
├─ Slide 1: Problem & Solution
├─ Slide 2: 30-Second Overview
└─ Discussion: "What problems do you face with tests?"

SEGMENT 2: ARCHITECTURE DEEP DIVE (25 min)
├─ Slide 3: 4-Step API Test Flow
├─ Slide 4: API Testing (BDD)
├─ Slide 5: UI Testing (Self-Healing)
├─ Slide 6: Key Files Reference
└─ Demo: Show running tests

SEGMENT 3: AI & AUTOMATION (20 min)
├─ Slide 7: AI Integration
├─ Slide 8: Running Tests
├─ Slide 9: Execution Sequence
└─ Demo: Trigger healing, show AI response

SEGMENT 4: OPERATIONS & SUPPORT (20 min)
├─ Slide 10: Metrics to Track
├─ Slide 11: Troubleshooting
├─ Slide 12: Key Takeaways
└─ Q&A: Team questions

SEGMENT 5: NEXT STEPS (15 min)
├─ Slide 13: Resources
├─ Slide 14: FAQ
├─ Slide 15: Training Path
└─ Hands-on: Everyone runs tests locally

TOTAL: 90 minutes (flexible based on questions)
```

---

## 🔮 Advanced Topics (Optional Deep Dive)

### For Architects
```
Custom AI Models
├─ Switch from Gemma to Claude
├─ Fine-tune prompts for your domain
└─ Cost tradeoffs

Self-Healing Strategy
├─ When to heal vs. when to fail
├─ Confidence thresholds
└─ Manual approval workflows

Integration with CI/CD
├─ Jenkins/GitHub Actions setup
├─ Automated test runs
├─ Slack notifications
└─ Report aggregation

Local LLM Setup
├─ Using Ollama locally
├─ Private AI without cloud costs
└─ Offline operation
```

### For Performance Optimization
```
Parallel Test Execution
├─ Run API & UI tests simultaneously
├─ Reduce total execution time
└─ Manage resource contention

Caching Strategies
├─ Cache AI responses
├─ Reuse selectors across runs
└─ Reduce OpenRouter calls

Selective Healing
├─ Only heal critical paths
├─ Manual fix low-priority tests
└─ Optimize cost vs. coverage
```

---

## 📋 Presentation Checklist

Before presenting to your team:

```
☐ Install Java 17 & Maven
☐ Clone the repository
☐ Setup .env with OPENROUTER_API_KEY
☐ Run: mvn clean test (to verify everything works)
☐ Print out key files (locators.json, feature files)
☐ Have browser open to openrouter.ai docs
☐ Have command line ready to demo
☐ Prepare for 2-3 demo failures (realistic!)
☐ Have slides + this document ready
☐ Setup recording (if recording session)
☐ Send pre-reading to team (TECHNICAL_KT_COMPREHENSIVE_GUIDE.md)
```

---

## 🎯 Post-Presentation Actions

### For Attendees
```
Week 1:
├─ Read TECHNICAL_KT_COMPREHENSIVE_GUIDE.md
├─ Run: mvn clean test locally
└─ Ask questions in team chat

Week 2:
├─ Write first test scenario
├─ Observe healing happen
└─ Review healed data

Week 3:
├─ Lead debugging of first failure
├─ Propose improvement to framework
└─ Suggest new tests
```

### For Team Leads
```
Month 1:
├─ Integrate framework into CI/CD
├─ Setup cost tracking
└─ Create team guidelines

Month 2:
├─ Migrate existing tests (prioritized)
├─ Monitor healing effectiveness
└─ Collect team feedback

Month 3:
├─ Optimize framework performance
├─ Scale to more test suites
└─ Share learnings with other teams
```

---

**Ready to present! Good luck with your KT session! 🚀**

