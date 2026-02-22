# LearningManage Backend

Spring Boot + MyBatis Plus + MySQL backend skeleton for the learning management project.

## Quick Start

### Requirements
- JDK 17
- Maven 3.9+ (or use `mvnw`)
- MySQL 8.x

### Run
- Default profile: `dev`
- Command:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### Run with profile

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Base Config
- Port: `8123`
- API prefix: `/api`
- Health endpoint: `GET /api/health`

## Environment Config Files
- `src/main/resources/application.yml` (shared config + active profile)
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-test.yml`
- `src/main/resources/application-prod.yml`

## Response Format

Success example (`GET /api/health`):

```json
{
  "code": 0,
  "message": "OK",
  "data": "ok"
}
```

Error example (`GET /api/demo/error/business`):

```json
{
  "code": 1000,
  "message": "Demo business exception",
  "data": null
}
```

## Global Exception Demo Endpoints
- Business exception: `GET /api/demo/error/business`
- System exception: `GET /api/demo/error/system`
- Validation exception: `GET /api/demo/error/validate?value=0`
