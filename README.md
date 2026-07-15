# 🚀 Advanced API Quality Engineering Blueprint

An enterprise-grade, Java-based API test automation framework targeting comprehensive quality assurance for public and private APIs (e.g., Swagger Petstore). This framework strictly utilizes REST Assured for stateful integration testing, Pact for consumer-driven contract testing, and an innovative AI Layer (Claude) for autonomous test generation and dynamic schema drift detection.

---

## 🏗 Architectural Philosophy & Design Decisions

This framework intentionally separates structural contract verification from functional logic validation to eliminate the fragility associated with traditional end-to-end testing environments.

- **Integration Testing (REST Assured):** Focused exclusively on business logic, authentication flows, request chaining, and HTTP assertions against live environments. REST Assured is utilized for its powerful BDD Domain Specific Language and seamless integration with Jackson for POJO serialization.
- **Contract Testing (Pact JVM):** Ensures the structural integrity of the API between the consumer and provider. By mocking the provider during test execution and matching on regex/types rather than hardcoded values, Pact prevents brittle tests and rapidly identifies schema violations without network latency.
- **AI Quality Layer (Anthropic Claude):** Traditional static validation tools are limited to strict syntax checking and happy-path fuzzing. This framework leverages an LLM to parse OpenAPI specifications, autonomously generating complex, adversarial test payloads via JUnit 5 `@TestFactory`, while simultaneously flagging context-aware breaking changes before CI execution begins.

---

## 🛠 Technology Stack & Dependencies

- **Java 17+** (JDK environment)
- **Apache Maven 3.9+** (Build tool and dependency management)
- **JUnit 5 (Jupiter)** (Test execution framework and parameterized engines)
- **REST Assured 5.4.0** (BDD style API client)
- **Jackson Databind** (JSON-to-POJO serialization/deserialization)
- **Pact Consumer JUnit 5** (Consumer contract definition and mock server framework)
- **Allure Jupiter** (Reporting, AspectJ bytecode weaving, and telemetry capture)
- **Anthropic Claude API** (Semantic schema analysis and dynamic test factory)

---

## 📂 Directory Topology

```
rest-assured-ai-pact-blueprint/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/api/blueprint/
│   │           ├── client/           # Core REST Assured HTTP client wrappers and interceptors
│   │           ├── config/           # Environment, global configurations, and secrets management
│   │           ├── models/           # POJOs for Jackson serialization/deserialization
│   │           └── ai/               # AI (Claude) integration layer, HTTP client, and prompt builders
│   └── test/
│       ├── java/
│       │   └── com/api/blueprint/
│       │       ├── integration/      # REST Assured integration tests (business logic)
│       │       ├── contract/         # Pact consumer contract tests (structural agreements)
│       │       └── utils/            # Test data generators, dynamic payloads, and assertions
│       └── resources/
│           ├── allure.properties     # Allure reporting configuration directives
│           ├── test-env.properties   # Environment-specific configuration variables
│           └── schemas/              # Baseline OpenAPI specifications (YAML/JSON)
├── .github/
│   └── workflows/
│       └── ci-pipeline.yml           # GitHub Actions Continuous Integration pipeline
├── pom.xml                           # Maven dependencies, profiles, and AspectJ plugins
└── README.md                         # Comprehensive framework documentation and execution guide
```

---

## 🚀 Execution Guide

### 1. Environment Initialization

Ensure Java 17 and Maven are installed. To run the live Claude AI integrations, you must export your API key:

```bash
export ANTHROPIC_API_KEY="your-api-key"
```

> [!NOTE]
> If `ANTHROPIC_API_KEY` is not present, the framework automatically triggers an **offline fallback generator** for both the Dynamic Test Generator and Schema Drift Detector. This ensures the maven build remains green and executes successfully on local boxes and basic pull requests.

### 2. Executing the Test Suites

To execute the entire quality suite (over 100 tests consisting of 30 integration tests, 70 data-driven tests, 3 contract tests, and 10 dynamic tests):

```bash
mvn clean test
```

### 3. Running AI Schema Drift Detection (CI Pre-Hook)

To execute the AI-driven schema drift comparison before full suite test runs:

```bash
mvn compile exec:java -Dexec.mainClass="com.api.blueprint.ai.SchemaDriftDetector"
```

### 4. Compiling and Viewing Telemetry

The framework uses the `AllureRestAssured` AspectJ filter to intercept and capture all HTTP traffic seamlessly. To compile and view the interactive HTML report locally:

```bash
mvn allure:serve
```

---

## 📈 Test Coverage Metrics

| Layer | Test Type | Library / Framework | Generated Test Cases | Purpose |
|---|---|---|---|---|
| **Layer 1** | Data-Driven Tests (DDT) | JUnit 5 `@ParameterizedTest` | **70** | Extreme parameter boundary checks for Pet, Store, and User APIs. |
| **Layer 1** | Integration Tests | REST Assured | **30** | Chained HTTP workflows validating state transitions & APIs. |
| **Layer 2** | Contract Tests | Pact JVM JUnit 5 | **3** | Schema structures verification against Mock provider server. |
| **Layer 3** | AI-Driven Dynamic Tests | Anthropic Claude API | **10** | Dynamically synthesized test cases targeting adversarial payloads. |
| **Total** | | | **113** | **Robust QA Gate** |
