# Data-Mediation-and-Transformation-Platform

A no-code data mediation platform that lets users design integration pipelines visually and automatically generates deployable Docker images from them built as a PFE (final-year internship) project at NTT DATA Morocco.

## Overview

ESB Studio allows non-technical users to configure data mediation pipelines (source → mapping → destination) through a visual interface, without writing integration code. Once a pipeline is validated, the platform packages it into a ready-to-run Docker image, so the mediation logic can be deployed independently as a standalone service.

## Key Features

- **No-code pipeline design** — build integration pipelines through a visual UI, no scripting required
- **Mapping module** — five transformation types: Field Placement, Value Transform, Format Change, Calculated Field, and Restructuring
- **Pipeline / payload lifecycle** — status management from `DRAFT` → `CONFIGURED` → `VALIDATED`
- **Sandbox testing** — end-to-end validation and mapping tests before deployment
- **Automated Docker image generation** — turns a validated pipeline into a deployable image, with real-time build log streaming (SSE)
- **Engine mode** — generated images run a lightweight engine that dispatches data to configured providers based on pipeline rules
- **Monitoring dashboard** — KPI cards, charts, and live auto-refresh for deployed pipelines

## Architecture

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3 |
| Frontend | Angular 17 (standalone components, signals) |
| Database | PostgreSQL, with Flyway migrations |
| Orchestration | Docker / Docker Compose |
| Testing | JUnit 5, Mockito, AssertJ |



## Deployment

Deployment is orchestrated through a `Makefile` with `dev`, `prod`, and `down` targets, layering `docker-compose.yml` with either `docker-compose.dev.yml` or `docker-compose.prod.yml`.

**Services:**
- `postgres` — PostgreSQL 15 (alpine), health-checked via `pg_isready`
- `backend` — Spring Boot app on port 8080, mounts `/var/run/docker.sock` for Docker image generation; health-checked via `/actuator/health`
- `frontend` — Angular app on port 4200

**Dev vs. Prod:**
- **Dev**: backend runs via a mounted Maven image (`mvn spring-boot:run`); frontend via mounted `node:20-alpine` (`ng serve --poll=1000`)
- **Prod**: backend and frontend images are built from local Dockerfiles (`./backend`, `./frontend`)

The backend Dockerfile uses a multi-stage build: `maven:3.9-eclipse-temurin-17` (builder) → `eclipse-temurin:17-jre-alpine` (runtime).

### Environment Variables

Configuration is provided via `.env`, including:
- Database credentials and `SPRING_DATASOURCE_URL`
- JWT secret and expiration
- AES encryption key

## Getting Started

```bash
# Development
make dev

# Production
make prod

# Stop all services
make down
```

## Testing

The backend test suite uses JUnit 5 with Mockito and AssertJ, organized with `@Nested` classes and `@DisplayName` annotations, covering 300+ tests with JaCoCo coverage tracking.

## Team

Developed as a PFE internship project at NTT DATA Morocco, in collaboration between [Ismail Damouh](#) and 
[Aya Ait Sidi Abdelkrim](#).
