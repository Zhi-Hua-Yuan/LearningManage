# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LearningManage** is a Spring Boot 3.3.6 backend application for learning management, built with MyBatis Plus and MySQL. The project follows a modular architecture with clear separation of concerns.

## Technology Stack

- **Java**: JDK 17
- **Framework**: Spring Boot 3.3.6
- **ORM**: MyBatis Plus 3.5.7
- **Database**: MySQL 8.x
- **API Documentation**: Knife4j OpenAPI 3
- **Build Tool**: Maven 3.9+
- **AI Integration**: Alibaba Cloud Qwen model

## Project Structure

```
src/main/java/com/spt/learningmanage/
├── LearningManageApplication.java      # Main application entry point
├── common/                             # Common utilities and response classes
├── config/                             # Configuration classes
├── constant/                           # Constants and enums
├── controller/                         # REST controllers
├── exception/                          # Exception handling
├── interceptor/                        # Spring interceptors
├── mapper/                             # MyBatis Plus mappers
├── model/                              # DTOs and VO objects
├── service/                            # Business logic services
└── utils/                              # Utility classes
```

## Development Workflow

The project follows a standardized development process with a clear 5-step workflow:

1. **Requirement Definition**: Define module requirements with clear acceptance criteria
2. **TODO Breakdown**: Break down requirements into actionable tasks with priorities
3. **Implementation**: Execute tasks in order with proper validation
4. **Review**: Code and design review focusing on quality and standards compliance
5. **Handover**: Complete documentation and handover

### Module Execution Order
1. Project module
2. Milestone and Task modules
3. WeeklyReview and Dashboard data interfaces
4. AI integration (placeholder first, then real integration)

## Common Development Tasks

### Build and Run

```bash
# Run with default profile (dev)
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Windows
.\mvnw.cmd spring-boot:run
```

### API Endpoints

- **Base URL**: `http://localhost:8123/api`
- **Health Check**: `GET /api/health`
- **API Docs**: Available via Knife4j at `/api/doc.html`

### Database

SQL initialization scripts are located in `sql/`:
- `init_user.sql`
- `init_project.sql`
- `init_milestone.sql`
- `init_task.sql`
- `init_weekly_review.sql`

## Response Format

All API responses follow a standard format:

```json
{
  "code": 0,           // 0 for success, non-zero for errors
  "message": "OK",     // Human-readable message
  "data": {}           // Response payload
}
```

## Error Codes

Defined in `com.spt.learningmanage.exception.ErrorCode`:
- `PARAMS_ERROR`: Invalid request parameters
- `PROJECT_NAME_EMPTY`: Project name cannot be empty
- `PROJECT_ALREADY_EXISTS`: Project already exists
- `PROJECT_NOT_FOUND`: Project not found

## Configuration

### Profiles
- `application.yml`: Shared configuration
- `application-dev.yml`: Development environment
- `application-test.yml`: Test environment
- `application-prod.yml`: Production environment

### AI Configuration
```yaml
ai:
  api-key: ${ALIYUN_API_KEY:please_set_your_api_key_in_env}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus
```

## Controllers Overview

- `ProjectController`: Project CRUD operations
- `MilestoneController`: Milestone management
- `TaskController`: Task management
- `WeeklyReviewController`: Weekly review operations
- `AiController`: AI integration endpoints
- `StatsController`: Dashboard statistics
- `UserController`: User management
- `ExceptionDemoController`: Global exception handling demo

## Service Layer Pattern

Services follow a consistent pattern:
- `create()`: Create entities and return ID
- `getById()`: Retrieve single entity by ID
- `list()`: Paginated list with filtering
- `update()`: Update existing entities
- `delete()`: Logical delete (sets isDelete=1)
- `archive()`: Archive entities
- `recover()`: Restore archived entities

## Database Conventions

- All tables use `is_delete` for logical deletion
- MyBatis Plus configuration: `logic-delete-field: isDelete`
- Camel case mapping: `map-underscore-to-camel-case: true`

## Testing

```bash
# Run tests
./mvnw test

# Run with test profile
./mvnw test -Dspring-boot.run.profiles=test
```

## PR Submission

Use `.github/PULL_REQUEST_TEMPLATE.md` with:
- Requirement document link
- TODO document link
- Main changes
- Validation results
- Risk and rollback instructions

## DoD (Definition of Done)

- [ ] Code implementation complete and compiles
- [ ] Core path tests pass (automated or manual)
- [ ] Edge cases verified
- [ ] Response structure and error codes follow project standards
- [ ] Requirement/TODO/review/handover documents complete