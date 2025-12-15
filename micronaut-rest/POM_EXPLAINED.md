# `micronaut-rest` — `pom.xml` explained (comprehensive)

This document explains **what every section in** `micronaut-rest/pom.xml` **does**, why each dependency/plugin exists, and how Micronaut version alignment works.

## Project coordinates & packaging
- **GAV**: `com.mavharsha:micronaut-rest:0.1`
- **Packaging**: `<packaging>${packaging}</packaging>` with `<packaging>jar</packaging>` in `<properties>`
  - This is slightly unusual (most POMs hardcode `<packaging>jar</packaging>`), but here it enables a convenient pattern used later: `aot-${packaging}.properties`.

## Parent POM: what `io.micronaut.platform:micronaut-parent` gives you
In `pom.xml`:
- **Parent**: `io.micronaut.platform:micronaut-parent:4.10.4`

This parent is the single most important piece of the build because it provides:
- **Version alignment (BOM / platform)**:
  - You intentionally do **not** specify versions for most Micronaut modules in `<dependencies>`.
  - The parent imports Micronaut’s “platform” / BOM so that modules like `micronaut-http-client`, `micronaut-serde-jackson`, etc. resolve to a compatible set of versions.
- **Managed properties you can reference**:
  - You use inherited properties such as `${micronaut.core.version}` and `${micronaut.serialization.version}` in the compiler’s annotation processor paths.
- **Plugin management defaults**:
  - The parent configures sensible default versions/config for common Maven plugins and Micronaut’s Maven plugin.
- **Enforcer rule baseline**:
  - The enforcer plugin is present to guard against unsupported Maven/JDK combinations and dependency issues.

### Important nuance: `micronaut-parent` vs `micronaut.version` property
This POM also defines:
- `<micronaut.version>4.10.4</micronaut.version>`

However, **the parent version is what actually controls the Micronaut platform**. The `micronaut.version` property is harmless, but it’s largely informational unless some plugin/config explicitly references it (this current `pom.xml` doesn’t).

## Properties (build/runtime switches)
Defined in `<properties>`:
- **Java toolchain / release**:
  - `jdk.version=17`
  - `release.version=17`
  - (The parent and compiler plugin cooperate to compile with the desired Java release.)
- **Runtime selection**:
  - `micronaut.runtime=netty` selects Netty as the HTTP runtime implementation.
- **AOT**:
  - `micronaut.aot.enabled=false` disables Micronaut AOT by default.
  - `micronaut.aot.packageName=com.mavharsha.aot.generated` is where generated AOT sources (if enabled) will go.
- **Main class**:
  - `exec.mainClass=com.mavharsha.Application` is used by run tooling (and is helpful for plugin defaults).

## Repositories
`<repositories>` includes:
- **Maven Central** (`https://repo.maven.apache.org/maven2`)

This is the standard default repository; keeping it explicit can make the build’s expectations clearer.

## Dependencies (what each one is for)
Micronaut module versions are **managed by the parent**, so these dependencies intentionally omit `<version>`.

### HTTP server (inbound requests)
- **`io.micronaut:micronaut-http-server-netty`** (scope: `compile`)
  - Provides the Netty-based embedded HTTP server used to serve controllers (e.g., `UsersController`, `HealthController`).
  - Ties into the `micronaut.runtime=netty` choice.

### HTTP client (outbound requests)
- **`io.micronaut:micronaut-http-client`** (scope: `compile`)
  - Provides Micronaut’s non-blocking HTTP client APIs used for calling external services (your JSONPlaceholder client is an example use case).
  - Works naturally with Netty; the client can be used with declarative clients (`@Client`) and/or programmatic clients.

### Reactor integration (reactive types)
- **`io.micronaut.reactor:micronaut-reactor`** (scope: `compile`)
  - Adds integration so Micronaut can seamlessly work with Reactor’s `Mono` / `Flux` types in controllers and clients.
  - Without this, you can still build a Micronaut app, but Reactor-based return types/conversions won’t be fully supported/idiomatic.

### JSON serialization / deserialization (Serde)
- **`io.micronaut.serde:micronaut-serde-jackson`** (scope: `compile`)
  - Enables JSON encoding/decoding using **Micronaut Serialization (Serde)** with a Jackson backend.
  - Pairs with the `micronaut-serde-processor` annotation processor to generate serializers/deserializers at compile time.

### Logging
- **`ch.qos.logback:logback-classic`** (scope: `runtime`)
  - Concrete SLF4J backend used at runtime.

### Testing
- **`io.micronaut.test:micronaut-test-junit5`** (scope: `test`)
  - Micronaut’s JUnit 5 integration: starts an application context for tests, supports injection, embedded server tests, etc.
- **`org.junit.jupiter:junit-jupiter-api`** + **`org.junit.jupiter:junit-jupiter-engine`** (scope: `test`)
  - JUnit 5 API and the engine used by Maven Surefire to execute tests.

## Build plugins (and why they’re here)

### `io.micronaut.maven:micronaut-maven-plugin`
Configured with:
- `configFile=aot-${packaging}.properties` (for a jar build, this resolves to `aot-jar.properties`)

What it does:
- Provides Micronaut-specific Maven goals (e.g., `mn:run`).
- Integrates Micronaut AOT (when enabled) using the referenced AOT config file.

How to enable AOT:
- Set `micronaut.aot.enabled=true` (example below).

### `org.apache.maven.plugins:maven-enforcer-plugin`
Why it exists:
- Ensures the build runs with supported Maven/Java versions and helps prevent classpath/version drift.
- Most rules/config come from the Micronaut parent’s plugin management.

### `org.apache.maven.plugins:maven-compiler-plugin`
This build relies on **annotation processing** (central to Micronaut).

#### Annotation processor paths
Configured processors:
- **`io.micronaut:micronaut-http-validation`** (`${micronaut.core.version}`)
  - Adds compile-time support for Micronaut’s HTTP validation integration (validation annotations, parameter/body validation hooks).
- **`io.micronaut.serde:micronaut-serde-processor`** (`${micronaut.serialization.version}`)
  - Generates Serde serializers/deserializers (used by `micronaut-serde-jackson` at runtime).
  - Excludes `io.micronaut:micronaut-inject` to avoid duplicating inject-related processing via this path.

#### Compiler args (Micronaut incremental processing metadata)
- `-Amicronaut.processing.group=com.mavharsha`
- `-Amicronaut.processing.module=micronaut-rest`

These help Micronaut’s annotation processors understand your module identity and can improve incremental compilation behavior.

## Upgrading / changing dependencies safely
- **Upgrade Micronaut**: bump the parent version:
  - `<parent> ... <version>4.10.4</version> ... </parent>`
  - Then verify everything compiles/tests (and check the Micronaut 4.x → 4.y release notes if you jump versions).
- **Add Micronaut modules**: prefer adding dependencies **without versions** and let the parent manage them.
- **Add non-Micronaut libraries**: you usually specify a version unless the parent already manages it.

## Useful Maven commands
- **Run the app**:
  - macOS/Linux: `./mvnw mn:run`
  - Windows (PowerShell): `.\mvnw mn:run` (or `.\mvnw.bat mn:run`)
- **Run tests**:
  - macOS/Linux: `./mvnw test`
  - Windows (PowerShell): `.\mvnw test` (or `.\mvnw.bat test`)
- **Build a jar**:
  - macOS/Linux: `./mvnw package`
  - Windows (PowerShell): `.\mvnw package` (or `.\mvnw.bat package`)
- **Build with AOT enabled**:
  - macOS/Linux: `./mvnw -Dmicronaut.aot.enabled=true package`
  - Windows (PowerShell): `.\mvnw -Dmicronaut.aot.enabled=true package` (or `.\mvnw.bat -Dmicronaut.aot.enabled=true package`)
