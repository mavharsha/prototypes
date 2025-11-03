# Spring Profiles Configuration Guide

This application uses Spring Profiles to manage different configurations for development and production environments.

## Available Profiles

### 🔧 `dev` Profile (Default)
- **Database**: H2 in-memory
- **DDL Auto**: create-drop (recreates schema on each restart)
- **H2 Console**: Enabled at `/h2-console`
- **SQL Logging**: Verbose (DEBUG level)
- **Use Case**: Local development and testing

### 🚀 `prod` Profile
- **Database**: MySQL 8.0
- **DDL Auto**: update (updates schema without dropping data)
- **H2 Console**: Disabled
- **SQL Logging**: Minimal (INFO level)
- **Use Case**: Production deployment

## Configuration Files

```
src/main/resources/
├── application.properties           # Common configuration + default profile
├── application-dev.properties       # Development-specific settings
└── application-prod.properties      # Production-specific settings
```

## How to Use

### Method 1: Command Line (Maven)

**Development:**
```bash
mvn spring-boot:run
# or explicitly
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Production:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Method 2: Command Line (JAR)

**Build the application:**
```bash
mvn clean package
```

**Run with profile:**
```bash
# Development
java -jar target/spring-todo-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# Production
java -jar target/spring-todo-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Method 3: Environment Variable

**Windows (PowerShell):**
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
mvn spring-boot:run
```

**Linux/Mac:**
```bash
export SPRING_PROFILES_ACTIVE=prod
mvn spring-boot:run
```

### Method 4: application.properties

Edit `src/main/resources/application.properties`:
```properties
spring.profiles.active=prod
```

## Database Setup

### Development (H2)
No setup required! Database is created in memory automatically.

### Production (MySQL)

#### Option 1: Docker Compose (Recommended)
```bash
docker-compose up -d
```

#### Option 2: Docker Run
```bash
docker run -d \
  --name spring-todo-mysql \
  -e MYSQL_ROOT_PASSWORD=mypassword \
  -e MYSQL_DATABASE=todo_db \
  -p 3306:3306 \
  mysql:8.0
```

#### Option 3: Local MySQL Installation
1. Install MySQL 8.0
2. Create database:
   ```sql
   CREATE DATABASE todo_db;
   ```
3. Update credentials in `application-prod.properties` if needed

## Verifying Active Profile

When the application starts, check the console output:
```
The following 1 profile is active: "dev"
```
or
```
The following 1 profile is active: "prod"
```

## Database Connection Details

### Development (dev profile)
```
URL:      jdbc:h2:mem:tododb
Driver:   org.h2.Driver
Username: sa
Password: (empty)
Console:  http://localhost:8080/h2-console
```

### Production (prod profile)
```
URL:      jdbc:mysql://localhost:3306/todo_db
Driver:   com.mysql.cj.jdbc.Driver
Username: root
Password: mypassword
Console:  Disabled
```

## Customizing Configuration

### Override Database Password (Production)
```bash
java -jar target/spring-todo-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.password=your_secure_password
```

### Override Database URL (Production)
```bash
java -jar target/spring-todo-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://your-mysql-server:3306/todo_db \
  --spring.datasource.username=your_username \
  --spring.datasource.password=your_password
```

## Best Practices

1. **Never commit production passwords** - Use environment variables or external configuration
2. **Default to dev profile** - Safer for local development
3. **Use `update` in production** - Prevents accidental data loss (set in prod profile)
4. **Use `create-drop` in dev** - Ensures clean state for testing (set in dev profile)
5. **Disable SQL logging in production** - Better performance and security

## Troubleshooting

### Profile not loading?
Check the console output for: `The following 1 profile is active:`

### Can't connect to MySQL?
1. Ensure MySQL container is running: `docker ps`
2. Check MySQL logs: `docker logs spring-todo-mysql`
3. Verify credentials in `application-prod.properties`

### Tables not created in MySQL?
With `ddl-auto=update`, run the app once to create tables automatically.

### Want to reset MySQL database?
```bash
docker-compose down -v
docker-compose up -d
```

## Environment-Specific Configuration Matrix

| Setting | Dev Profile | Prod Profile |
|---------|-------------|--------------|
| Database | H2 (in-memory) | MySQL |
| DDL Auto | create-drop | update |
| H2 Console | ✅ Enabled | ❌ Disabled |
| SQL Logging | 🔊 Verbose | 🔇 Minimal |
| Data Persistence | ❌ Lost on restart | ✅ Persistent |
| Setup Required | None | MySQL setup |

