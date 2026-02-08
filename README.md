# SmartMoney Backend API

RESTful API backend for SmartMoney, an AI-powered financial advisory platform that provides personalized financial guidance and budgeting recommendations.

## Live API

**Base URL:** `https://smartmoney-backend-production-0674.up.railway.app`

## Technology Stack

- **Framework:** Spring Boot 3.x
- **Language:** Java 21
- **Build Tool:** Maven
- **AI Integration:** Groq API (LLaMA model)
- **Hosting:** Railway

## API Endpoints

### Health Check
```
GET /api/health
```

Returns the current status and health information of the API.

**Response:**
```json
{
  "status": "OK",
  "message": "Financial Advisor API is running"
}
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
    "monthlyIncome": 5000,
    "monthlyExpenses": 3500,
    "currentSavings": 10000,
    "outstandingDebts": 5000,
    "financialGoals": "Save for a house"
  }
}
```

**Response:**
```json
{
  "response": "Based on your financial situation...",
  "success": true
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

3. Build the project:
```bash
mvn clean install
```

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
| `FRONTEND_URL` | Frontend application URL for CORS | Yes | `http://localhost:3000` |
| `PORT` | Server port | No | `8080` |

### Application Properties

Configuration is managed in `src/main/resources/application.properties`:
```properties
server.port=${PORT:8080}
spring.application.name=finance-advisor
groq.api.key=${GROQ_API_KEY}
cors.allowed.origins=${FRONTEND_URL:http://localhost:3000}
```

## Project Structure
```
src/main/java/com/nikhil/finance_advisor/
├── config/
│   └── CorsConfig.java                    # CORS configuration
├── controller/
│   └── FinancialAdvisorController.java    # REST API endpoints
├── model/
│   ├── ChatRequest.java                   # Request data transfer object
│   ├── ChatResponse.java                  # Response data transfer object
│   └── FinancialData.java                 # Financial data model
├── service/
│   └── GeminiService.java                 # AI service integration
└── FinanceAdvisorApplication.java         # Application entry point
```

## API Integration

The backend integrates with Groq's API to process natural language queries and generate contextual financial advice. User financial data is included in prompts to ensure personalized and relevant responses.

## Related Repositories

- [Frontend Application](https://github.com/nikhilthota2007/smartmoney-frontend)

## Author

Nikhil Thota
- GitHub: [@nikhilthota2007](https://github.com/nikhilthota2007)

## License

This project is licensed under the MIT License.
