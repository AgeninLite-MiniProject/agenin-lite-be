# AgeninLite Backend (agenin-lite-be)

AgeninLite Backend is the core RESTful API service built for the AgeninLite agent management and financial transaction platform. It handles security authentication, role-based access control, transaction processing, agent network hierarchies, product management, and audit logging.

---

## Tech Stack & Architecture

- **Java Version**: Java 21 (LTS)
- **Framework**: Spring Boot 4.1.0
- **Security**: Spring Security (Stateless JWT Authentication)
- **Database**: PostgreSQL
- **Database Migration**: Flyway
- **Persistence**: Spring Data JPA / Hibernate
- **Code Coverage**: JaCoCo Plugin (Enforcing strict test coverage guidelines)
- **Build Tool**: Apache Maven (`mvnw` wrapper included)
- **Containerization**: Docker & Docker Compose

---

## Key Modules & Endpoints

| Endpoint Group | Access Control | Description |
| :--- | :--- | :--- |
| `/api/auth/**` | Public | Agent & Admin Registration, Login, and Token Refreshes |
| `/api/admin/**` | ADMIN Role | Administrative management, user access control, global audit logs |
| `/api/products/**` | Authenticated | Product catalog listing and product detail retrieval |
| `/api/transactions/**` | Authenticated | Transaction creation, processing, and transaction history |
| `/api/downline/**` | Authenticated | Agent network hierarchy and downline management |
| `/api/invitation/**` | Authenticated | Invitation link generation and acceptance workflows |
| `/api/dashboard/**` | Authenticated | Metrics, statistics, and financial performance summaries |
| `/api/audit/**` | ADMIN Role | System activity and audit trail tracking |

---

## Project Structure (Layer-First)

The codebase strictly adheres to a **Layer-First Architecture**:

```text
src/main/java/com/indivaragroup/ageninlite/
├── common/             # Global DTOs (ApiResponse wrapper), Exception Handler
├── config/             # Spring Security, CORS, and Bean configurations
├── controller/         # REST API Controllers (Layer-first packages)
│   ├── admin/
│   ├── audit/
│   ├── auth/
│   ├── dashboard/
│   ├── downline/
│   ├── invitation/
│   ├── product/
│   ├── transaction/
│   └── user/
├── dto/                # Request and Response Data Transfer Objects
├── entity/             # JPA Database Entities (mapped to database tables)
├── repository/         # Spring Data JPA Repository interfaces
├── security/           # JwtUtil, JwtAuthenticationFilter, UserDetailsService
└── service/            # Core business logic implementation
```

---

## Security Guidelines & Rules

1. **Layer Encapsulation**: Controllers must never accept or return JPA Entity classes directly. Controllers interact strictly via Request and Response DTOs using `ApiResponse<T>`.
2. **Stateless JWT**: Requests to protected routes must include the HTTP Authorization Header:
   ```http
   Authorization: Bearer <your_jwt_token>
   ```
3. **Role Enforcement**: Endpoint permissions are configured centrally in `SecurityConfig.java`.

---

## Environment Setup & Configuration

### Prerequisites
- JDK 21 installed and configured
- PostgreSQL database (or Docker Desktop)
- Maven 3.9+ (or use `./mvnw`)

### Environment Variables
Copy `.env.example` to `.env` in the root directory:

```bash
cp .env.example .env
```

Configure your environment settings in `.env`:

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5432/agenin_lite
DB_USER=agenin
DB_PASSWORD=agenin
JWT_SECRET=your_super_secret_jwt_key_must_be_at_least_256_bits_long
JWT_EXPIRATION_MS=86400000
```

### Running Database via Docker Compose
To launch a PostgreSQL container locally:

```bash
docker-compose up -d
```

Flyway will automatically execute all migration scripts located in `src/main/resources/db/migration/` on startup.

---

## Running the Application

### Using Maven Wrapper

```bash
# Clean and run the application locally
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## Testing & Code Coverage

### Running Unit & Integration Tests

```bash
./mvnw clean test
```

### JaCoCo Code Coverage Report
JaCoCo is preconfigured to generate code coverage reports upon running tests.

- Report Output Directory: `target/site/jacoco/index.html`
- Open the report in a browser to inspect line and branch coverage breakdown.

---

## Building for Production

### Package as JAR File

```bash
./mvnw clean package -DskipTests=false
```

The compiled executable JAR will be generated at:
`target/agenin-lite-be-0.0.1-SNAPSHOT.jar`

### Run Executable JAR

```bash
java -jar target/agenin-lite-be-0.0.1-SNAPSHOT.jar
```

### Docker Container Build

```bash
docker build -t agenin-lite-be .
docker run -p 8080:8080 --env-file .env agenin-lite-be
```
