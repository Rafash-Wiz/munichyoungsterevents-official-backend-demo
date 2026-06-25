# Youngster Backend

Backend for the Munich Youngster Events platform, centered around event management, booking workflows, authentication, and admin operations.

This app lets the platform:
- expose public event data
- register and authenticate users
- create, confirm, and cancel bookings
- manage attendee and admin flows securely
- enforce event and booking lifecycle rules
- support paginated admin and attendee dashboard data

## Features

- Public event APIs with support for open, coming soon, closed, and cancelled event states
- JWT-based authentication with register, login, logout, and current-user lookup
- Role-based authorization for attendee and admin access
- Core booking flow with booking creation, confirmation, cancellation, and pending booking lookup
- Attendee self-service endpoints for paginated personal bookings
- Admin endpoints for managing events, users, and bookings
- Event lifecycle actions including open, close, and cancel
- Booking lifecycle handling with pending, confirmed, and cancelled states
- Booking cancellation metadata tracking including cancellation reason, previous status, and cancellation timestamp
- Paginated list APIs for events, users, admin bookings, and attendee self-bookings
- Filtering support for admin dashboards across users and bookings
- Automatic expiration handling for stale pending bookings
- Integration test coverage for auth, authorization, booking flows, event lifecycle, and pagination behavior

## Tech Stack

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- JUnit
- MockMvc

## Architecture

The backend is organized around clear application layers:

- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `mapper`
- `security`
- `exception`

Key design choices:
- DTO-based API responses
- service-layer business rules
- repository-driven persistence with JPA
- explicit lifecycle endpoints for event state transitions
- stateless JWT authentication with bearer-token authorization

## Authentication & Authorization

Authentication is JWT-based.

Current auth flow includes:
- register
- login
- logout
- current authenticated user lookup via `/api/auth/me`

Current JWT behavior:
- `POST /api/auth/login` returns an access token
- protected routes expect `Authorization: Bearer <token>`
- `/api/auth/me` resolves the current user from the JWT-authenticated security context
- current logout is client-side oriented and does not yet revoke tokens server-side

Authorization is role-based:
- `ATTENDEE`
- `ADMIN`

Examples:
- attendees can manage their own bookings
- admins can manage events, users, and platform-wide bookings

## Event Lifecycle

Events currently support these statuses:
- `OPEN`
- `COMING_SOON`
- `CLOSED`
- `CANCELLED`

Lifecycle behavior includes:
- create events as `OPEN` or `COMING_SOON`
- open a `COMING_SOON` or `CLOSED` event
- close an `OPEN` event
- cancel a non-cancelled event
- block status changes through normal event update requests

Additional event response fields include:
- `availableSpots`
- `bookedCount`
- `confirmedCount`
- `pendingCount`
- `cancelledConfirmedCount`

## Booking Lifecycle

Bookings currently support these statuses:
- `PENDING`
- `CONFIRMED`
- `CANCELLED`

Cancellation metadata includes:
- `cancellationReason`
- `cancelledFromStatus`
- `cancelledAt`

Current cancellation reasons include:
- `USER_REQUEST`
- `ADMIN_ACTION`
- `EXPIRED`
- `EVENT_CLOSED`
- `EVENT_CANCELLED`

Business rules include:
- only `OPEN` events can be booked
- closing an event cancels pending bookings but keeps confirmed bookings
- cancelling an event cancels all active bookings
- expired pending bookings are cancelled automatically

## Pagination & Filtering

The backend uses pageable-style pagination for frontend list views.

Paginated response shape includes:
- `content`
- `number`
- `size`
- `totalElements`
- `totalPages`
- `first`
- `last`

Current paginated endpoints include:
- `GET /api/events`
- `GET /api/users`
- `GET /api/bookings`
- `GET /api/bookings/me`

Filtering support includes:

Users:
- `role`
- `id`
- `firstName`
- `lastName`

Bookings:
- `userId`
- `eventId`
- `status`

## Database

The backend uses PostgreSQL.

The project supports separate databases for:
- development/live usage
- tests

Example setup:
- `youngster`
- `youngster_test`

Test runs use the test profile so integration tests do not wipe live frontend/demo data.

## Scripts

Run the application:

```bash
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```

Compile only:

```bash
./mvnw compile
```

Package the application:

```bash
./mvnw package
```

## Environment

The backend reads configuration from:

- `src/main/resources/application.properties`

Typical configuration includes:
- PostgreSQL datasource URL
- database username/password
- admin bootstrap credentials
- app-specific settings such as pending booking expiration timing

Test-specific overrides live in:
- `src/test/resources/application-test.properties`

## Testing

The backend includes integration-focused test coverage for:
- authentication
- role-based authorization
- booking ownership rules
- booking concurrency
- booking lifecycle transitions
- event lifecycle transitions
- pending booking expiration
- pagination stability

Tests run against the separate test database profile.

## Project Notes

- Authentication is JWT-based and the backend is stateless.
- Business rules are enforced in the service layer, not trusted to the frontend.
- Event status transitions use dedicated lifecycle endpoints instead of free-form status updates.
- Pagination and filtering were designed to support both admin dashboard and attendee dashboard flows.
- Booking and event lifecycle behavior is modeled explicitly to support future payment integration cleanly.
- Refresh tokens and server-side token revocation are planned as future production-hardening steps.
