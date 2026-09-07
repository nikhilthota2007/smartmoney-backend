# SmartMoney Backend API

RESTful API backend for SmartMoney, an AI-powered financial advisory platform that provides personalized financial guidance and budgeting recommendations.

## Live API

**Base URL:** `https://smartmoney-backend-production-0674.up.railway.app`

## Technology Stack

- **Framework:** Spring Boot 4.0.1
- **Language:** Java 21 (compiled at source level 17)
- **Build Tool:** Maven
- **AI Integration:** Groq API (LLaMA model)
- **Hosting:** Railway

## API Endpoints

### Health Check
```
GET /api/health
```

Returns a plain-text liveness string (not JSON):

```
Financial Advisor API is running!
```

### Chat Endpoint
```
POST /api/chat
Content-Type: application/json
```

Generates AI-powered financial advice based on user's financial profile.

**Request Body:**
```json
{
  "message": "Should I save or pay off debt first?",
  "financialData": {
    "monthlyIncome": "5000",
    "monthlyExpenses": "3500",
    "savings": "10000",
    "debts": "5000",
    "goals": "Save for a house"
  },
  "history": [
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." }
  ]
}
```

All `financialData` fields are strings and all are optional — anything omitted is
sent to the model as "Not provided". `history` is the conversation so far, oldest
first, and may be omitted or empty.

**Response:**
```json
{
  "response": "Based on your financial situation...",
  "success": true
}
```

On failure the endpoint still returns HTTP 200 with a generic message; the cause
is written to the server log rather than returned to the client:
```json
{
  "response": null,
  "success": false,
  "error": "The advisor is temporarily unavailable. Please try again in a moment."
}
```

## Local Development

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- Groq API key

### Setup

1. Clone the repository:
```bash
git clone https://github.com/nikhilthota2007/smartmoney-backend.git
cd smartmoney-backend
```

2. Configure environment variables:
```bash
export GROQ_API_KEY=your_groq_api_key_here
export FRONTEND_URL=http://localhost:3000
```

3. Build and test the project:
```bash
mvn clean verify
```
The test suite does not need a real `GROQ_API_KEY`.

4. Run the application:
```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

## Configuration

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `GROQ_API_KEY` | API key for Groq AI service | Yes | - |
| `FRONTEND_URL` | Frontend application URL for CORS | No | `http://localhost:3000` |
| `GROQ_MODEL` | Groq model id | No | `llama-3.3-70b-versatile` |
| `PORT` | Server port | No | `8080` |

`GROQ_API_KEY` has no default on purpose: the application fails to start without
it, so a misconfigured deploy is caught immediately rather than serving errors.

### Application Properties

Configuration is managed in `src/main/resources/application.properties`:
```properties
server.port=${PORT:8080}
spring.application.name=finance-advisor
groq.api.key=${GROQ_API_KEY}
cors.allowed.origins=${FRONTEND_URL:http://localhost:3000}

groq.api.model=${GROQ_MODEL:llama-3.3-70b-versatile}
groq.api.temperature=0.7
groq.api.max-tokens=1000
groq.api.connect-timeout-seconds=10
groq.api.read-timeout-seconds=60
```

## Project Structure
```
src/main/
├── java/com/nikhil/finance_advisor/
│   ├── config/
│   │   └── RestClientConfig.java          # Shared RestTemplate with timeouts
│   ├── controller/
│   │   └── FinancialAdvisorController.java # REST API endpoints
│   ├── model/
│   │   ├── ChatMessage.java               # One conversation turn
│   │   ├── ChatRequest.java               # Request data transfer object
│   │   ├── ChatResponse.java              # Response data transfer object
│   │   └── FinancialData.java             # Financial data model
│   ├── prompt/
│   │   └── AdvisorPrompt.java             # Loads and fills the system prompt
│   ├── service/
│   │   └── AdvisorService.java            # Groq API integration
│   └── FinanceAdvisorApplication.java     # Application entry point
└── resources/
    ├── application.properties
    └── prompts/
        └── advisor-system-prompt.v2.md    # The system prompt of record
```

## The system prompt

The advisor's behaviour is defined in
[`src/main/resources/prompts/advisor-system-prompt.v3.md`](src/main/resources/prompts/advisor-system-prompt.v3.md),
not in Java source, so it can be reviewed as a diff and rolled back on its own.
`AdvisorPrompt` loads it at startup and `FinancialPictureRenderer` substitutes the
computed financial picture into it.

Its `SAFETY AND SCOPE` section carries the guardrails: educational framing rather
than licensed advice, escalation to a CFP/CPA/attorney for tax, estate, insurance
and legal questions, a prohibition on recommending specific securities or
predicting returns, and a rule against stating figures that cannot be derived from
what the user supplied. `AdvisorPromptTest` asserts each of these is present, so
removing one fails the build.

To change the prompt materially, add a new versioned file, bump
`AdvisorPrompt.PROMPT_VERSION`, and update the tests.

## The financial picture

`financialContext` on the chat request carries figures the frontend has already
computed — derived metrics, the health score, per-debt terms, payoff timelines,
coverage gaps, and a list of what the user has not entered yet. The prompt tells
the model these are authoritative: quote them, never recompute them, and say so
plainly when a question needs a calculation that is not present.

Nothing here is recalculated server-side. Duplicating money maths in a second
language would give two answers that could disagree; the frontend's version is
covered by characterization tests pinning its output.

`financialContext` is optional. An older client that sends only `financialData`
still works, and the prompt then tells the model that only the headline figures
are available.

`src/test/resources/contract/advisor-context.json` is the exact payload the
frontend emits, checked in on both sides.
[`AdvisorContextContractTest`](src/test/java/com/nikhil/finance_advisor/prompt/AdvisorContextContractTest.java)
asserts it binds to our records with nothing dropped — a renamed field would
otherwise deserialize to null and the model would quietly lose that figure. The
frontend has the matching test.

## API Integration

The backend integrates with Groq's API to process natural language queries and generate contextual financial advice. User financial data is included in prompts to ensure personalized and relevant responses.

The model is grounded in the precomputed figures described above rather than
doing its own arithmetic. Parameterized what-ifs ("what if I pay $500 extra?")
still need real tool calls, which is the remaining Phase 2 work in the
[frontend repository's plan](https://github.com/nikhilthota2007/smartmoney-frontend/blob/main/docs/PLAN.md).

## Related Repositories

- [Frontend Application](https://github.com/nikhilthota2007/smartmoney-frontend)

## Author

Nikhil Thota
- GitHub: [@nikhilthota2007](https://github.com/nikhilthota2007)

## License

This project is licensed under the MIT License.
