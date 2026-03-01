# E-Commerce Micronaut Multi-Module Project

A demonstration project showing Maven multi-module architecture with Micronaut framework.

## Project Structure

```
ecommerce-micronaut/
├── pom.xml                      # Parent POM (versions, plugins, module list)
├── ecommerce-common/            # Shared DTOs, exceptions
├── ecommerce-domain/            # Entities, repository interfaces
├── ecommerce-service/           # Business logic, in-memory repositories
└── ecommerce-api/               # REST controllers, application entry point
```

## Module Dependency Graph

```
ecommerce-common (foundation - no internal dependencies)
       ↑
ecommerce-domain (depends on: common)
       ↑
ecommerce-service (depends on: domain, common)
       ↑
ecommerce-api (depends on: service) ← Runnable application
```

## Quick Start

```bash
# Build all modules
mvn clean install

# Run the application
cd ecommerce-api
mvn mn:run

# Or run the JAR directly
java -jar ecommerce-api/target/ecommerce-api-1.0.0-SNAPSHOT.jar
```

## API Endpoints

| Method | Endpoint                      | Description           |
|--------|-------------------------------|-----------------------|
| GET    | /api/products                 | List all products     |
| GET    | /api/products/{id}            | Get product by ID     |
| POST   | /api/products                 | Create product        |
| PUT    | /api/products/{id}            | Update product        |
| DELETE | /api/products/{id}            | Delete product        |
| GET    | /api/orders                   | List all orders       |
| GET    | /api/orders/{id}              | Get order by ID       |
| POST   | /api/orders                   | Create order          |
| POST   | /api/orders/{id}/confirm      | Confirm order         |
| POST   | /api/orders/{id}/cancel       | Cancel order          |

## Sample Requests

```bash
# List products (pre-seeded with sample data)
curl http://localhost:8080/api/products

# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-123",
    "items": [
      {"productId": "<product-id>", "quantity": 2}
    ]
  }'
```

---

# Maven Multi-Module Guide

## How Parent-Child Relationship Works

### Parent POM Responsibilities

The parent POM (`ecommerce-parent`) manages:

1. **Module Declaration** - Lists all child modules
2. **Version Management** - Centralizes dependency versions via `<dependencyManagement>`
3. **Plugin Configuration** - Shared build configuration via `<pluginManagement>`
4. **Common Dependencies** - Dependencies inherited by ALL modules

```xml
<!-- Parent POM -->
<packaging>pom</packaging>

<modules>
    <module>ecommerce-common</module>
    <module>ecommerce-domain</module>
    <module>ecommerce-service</module>
    <module>ecommerce-api</module>
</modules>
```

### Child Module Structure

Each child references the parent:

```xml
<!-- Child POM -->
<parent>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>ecommerce-common</artifactId>
<!-- Version inherited from parent -->
```

## Dependency Inheritance

### What Gets Inherited

| Section | Inherited? | Notes |
|---------|------------|-------|
| `<dependencies>` | Yes | All children get these dependencies |
| `<dependencyManagement>` | No* | Versions available, but must declare dependency |
| `<properties>` | Yes | All properties available in children |
| `<pluginManagement>` | No* | Configuration available, but must declare plugin |
| `<plugins>` | Yes | All children run these plugins |

*Available but not automatically applied - must be explicitly declared in child.

### dependencyManagement vs dependencies

```xml
<!-- In Parent: dependencyManagement -->
<!-- Defines VERSION only, does NOT add to classpath -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>some-lib</artifactId>
            <version>1.0.0</version>  <!-- Version defined here -->
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- In Child: just reference, no version needed -->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>some-lib</artifactId>
        <!-- Version inherited from parent's dependencyManagement -->
    </dependency>
</dependencies>
```

## Version Management

### Current Versions (defined in parent)

```xml
<properties>
    <micronaut.version>4.3.0</micronaut.version>
    <junit.version>5.10.2</junit.version>
</properties>
```

### Upgrading Versions

**Upgrade a framework/library version:**
1. Change version in parent `pom.xml` properties
2. Run `mvn clean install` from parent directory
3. All modules automatically use new version

```bash
# Example: Upgrade Micronaut from 4.3.0 to 4.4.0
# 1. Edit parent pom.xml:
#    <micronaut.version>4.4.0</micronaut.version>

# 2. Rebuild all modules
mvn clean install
```

### Can I Upgrade Just One Module?

**Short answer: It depends on what you're upgrading.**

#### Scenario 1: Upgrading the Module's Own Version

Yes, but **not recommended** in most cases.

```xml
<!-- Child can override its own version -->
<parent>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>ecommerce-service</artifactId>
<version>1.1.0-SNAPSHOT</version>  <!-- Different from parent -->
```

**Problems with mixed versions:**
- Other modules referencing this module need explicit version
- Breaks `${project.version}` consistency
- Complicates release management

#### Scenario 2: Module-Specific Dependency Version

Yes, you can override a dependency version in a specific module:

```xml
<!-- In child pom.xml - overrides parent's version -->
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.16.0</version>  <!-- Explicit version overrides parent -->
    </dependency>
</dependencies>
```

**When this makes sense:**
- Testing a new version in one module before rolling out
- Module has specific compatibility requirements
- Temporary fix while waiting for other modules to catch up

#### Scenario 3: Building/Deploying One Module

Yes, you can build a single module:

```bash
# Build only ecommerce-service (and its dependencies)
mvn install -pl ecommerce-service -am

# Flags:
# -pl (--projects)        : Build specific module
# -am (--also-make)       : Also build dependencies
# -amd (--also-make-dependents) : Also build modules that depend on this
```

## Build Commands

```bash
# Build everything
mvn clean install

# Build single module + its dependencies
mvn install -pl ecommerce-api -am

# Skip tests
mvn install -DskipTests

# Build without installing to local repo
mvn package

# Run from specific module
cd ecommerce-api && mvn mn:run

# Dependency tree (useful for debugging)
mvn dependency:tree

# Check for dependency updates
mvn versions:display-dependency-updates
```

## Adding a New Module

1. Create module directory with `pom.xml`:

```xml
<parent>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>ecommerce-new-module</artifactId>
```

2. Add to parent's module list:

```xml
<modules>
    <module>ecommerce-common</module>
    <module>ecommerce-domain</module>
    <module>ecommerce-service</module>
    <module>ecommerce-api</module>
    <module>ecommerce-new-module</module>  <!-- Add here -->
</modules>
```

3. Add to parent's `dependencyManagement` if other modules will depend on it:

```xml
<dependency>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-new-module</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Best Practices

1. **Keep all versions in parent** - Single source of truth
2. **Use properties for versions** - Easy to update and reference
3. **Module order matters** - List dependencies before dependents
4. **Use dependencyManagement** - Don't repeat versions in children
5. **Consistent versioning** - All modules same version (usually)
6. **Minimal dependencies** - Each module only includes what it needs
7. **Clear boundaries** - Modules should have single responsibility
