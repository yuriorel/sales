# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

E-commerce engine, exposed as a REST API and written in Java.

## Current State

The repository has no code yet, only `LICENSE` and this file. The first step is to write `./docs/PLAN.md` (see Strategy). Once the project is scaffolded, add the build, test, single-test and run commands here, along with a short architecture overview.

## Technical Requirements

- Java 25 SDK (installed locally), with `--enable-preview` (needed for structured concurrency).
- Spring Boot, latest version (Spring Web MVC). Endpoints are hand-written; Spring Authorization Server is not used.
- Build with Maven (Maven Wrapper).
- Postgresql will be used as database. In development environment postgres server IP address is 192.168.0.101, port 5432, dtabase name is 'sales', schema name is 'sales_online', user name is 'sales-person', password is 'changeit'.
- Database object what required for e-commerce should be created.
- Please download from Internet aroubd hundreds products of ten categories for testing. Each product should have user guide stored as text data type and will be used in full text search and AI analisys so user guide will be vectorized and embeddings will be stored in the same postgrs database. Required extension was installed. Please be sure that user guide are very detailed.
- Implement Cache-Aside pattern using in development environment local Redis on the default port 6379. Redis server already installed.
- Apache Kafka will be used in application as well. In development environment kafka server deployed to localhost. Host is windows 11. Kafka root folder is 'D:\application\kafka_2.13-4.3.1'. Topik name should be 'sales_online'. All e-commerce transactions should be publish to kafka topic. This is not required in general however this project developed for learning and kafka for now just to demonstrate java 25 virtual threads and structured concurrency so transactions should be written in parallel to postgres & kafka.
- Database and streaming system should be flexible so interfaces should be defined and real implementation classes should be configurable for example instead of postgres & kafka AWS dynamodn & kanisis may be used. 
- API should support username/password and OAuth2 authentication.
- Implement error handling.
- Implement logging using logback. Add logging at DEBUG level to all steps and log objects in context to help resolving issues.
- Integrate OpenAPI using Code-First Generation to make available api-docs.
- Implement Swagger endpoint as well.
- Use self-signed certificates for TLS during testing.
- This project is meant to be a guide to modern Java features. Use each of these where it fits naturally:
  1. Virtual threads for concurrency
  2. Structured concurrency where needed
  3. Records wherever possible
  4. Sealed classes and interfaces
  5. Local variable type inference (`var`)
  6. Switch expressions
  7. Pattern matching for `switch` and `instanceof`
  8. Text blocks
  9. Unnamed patterns and variables (`_`)
  10. Stream API enhancements (e.g. gatherers)
  11. Module import declarations
  12. Scoped values
  13. Flexible constructor bodies
  14. Use markdowd documentation comments
  - Define mermaid java classes, sequence and database diagrams

## Strategy

1. Write the plan to `./docs/PLAN.md`, with success criteria to check off for each phase. Include project scaffolding (with `.gitignore`) and rigorous unit testing.
2. Carry out the plan and make sure every criterion is met.
3. Run extensive integration tests (Playwright or similar) and fix the defects they find.
4. The work is done only when the MVP is finished and tested, and the server is running and ready for the user.

## Coding Standards

1. Use the latest library versions and current idiomatic approaches.
2. Keep it simple: never over-engineer, always simplify, and avoid unnecessary defensive programming. Add no extra features.
3. Be concise. Keep the README minimal. Never use emojis.
