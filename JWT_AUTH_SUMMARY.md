# JWT Auth Summary

This document summarizes the current JWT-based authentication model in the Youngster Events backend and what changed from the old session-based setup.

## Current Auth Model

The backend now uses stateless JWT authentication.

That means:

- login returns a signed access token
- the frontend stores that token
- protected requests send:
  - `Authorization: Bearer <token>`
- the backend validates the token on each request
- no server-side login session is required for protected API access

## Why We Switched

We moved away from session auth because:

- the frontend/backend separation is cleaner with bearer tokens
- the backend can be stateless
- auth becomes easier to reason about for API-style clients
- the frontend can restore auth state from a stored token

## Current Scope

The current implementation is intentionally a first-step JWT version:

- access token only
- no refresh token yet
- no server-side token revocation yet
- logout is effectively client-side token removal

This is a good migration checkpoint, even though it is not yet the final production-ready auth design.

## Current Flow

### Register

- user registers with email, password, first name, and last name
- backend creates the user
- register returns user info
- register does not auto-issue a token

### Login

- client sends email and password
- Spring Security authenticates credentials
- backend generates JWT
- backend returns auth response including token

### Protected Requests

- client sends bearer token in `Authorization` header
- JWT filter extracts and validates token
- filter loads the user through `CustomUserDetailsService`
- authenticated user is placed into `SecurityContextHolder`

### Current User Lookup

- `/api/auth/me` reads the authenticated principal from the security context
- backend resolves the real domain `User`
- response returns current user info

### Logout

- current `/api/auth/logout` is a lightweight endpoint
- it does not revoke the token
- frontend should remove the token locally

## Main Code Pieces

### 1. `JwtService`

File:
- `src/main/java/com/ashraf/munichyoungsterevents/security/JwtService.java`

Responsibilities:
- generate token
- extract username
- extract role claim
- validate token
- check expiration

Current token claims include:
- subject = user email
- role
- issued at
- expiration

### 2. `JwtAuthenticationFilter`

File:
- `src/main/java/com/ashraf/munichyoungsterevents/security/JwtAuthenticationFilter.java`

Responsibilities:
- read `Authorization` header
- check for `Bearer ` prefix
- extract username from token
- load user details
- validate token
- populate Spring Security context

This is what makes JWT-authenticated requests work across the backend.

### 3. `SecurityConfig`

File:
- `src/main/java/com/ashraf/munichyoungsterevents/security/SecurityConfig.java`

Important JWT-related changes:
- `SessionCreationPolicy.STATELESS`
- JWT filter added before `UsernamePasswordAuthenticationFilter`
- route authorization rules preserved
- form login and http basic remain disabled

This keeps the same authorization model while changing how authentication is established.

### 4. `CustomUserDetailsService`

File:
- `src/main/java/com/ashraf/munichyoungsterevents/security/CustomUserDetailsService.java`

Still used in the JWT model.

Responsibilities:
- load user by email
- expose password hash and authorities to Spring Security

It is used:
- during login credential authentication
- and again when resolving token-authenticated requests

### 5. Auth Service / Controller

Files:
- `service/AuthService.java`
- `service/AuthServiceImpl.java`
- `controller/AuthController.java`

JWT-related changes:
- login no longer writes auth into HTTP session
- login now returns token in `AuthResponseDTO`
- `/me` still works using authenticated principal
- logout is no longer session invalidation logic

### 6. `AuthResponseDTO`

Now includes:
- `token`
- `userId`
- `email`
- `role`
- `firstName`
- `lastName`

Current behavior:
- login returns token
- register returns `token = null`
- `/me` returns `token = null`

## Frontend Expectations

The frontend should now:

- store the JWT after login
- send it in `Authorization: Bearer <token>`
- restore auth state on refresh using stored token + `/api/auth/me`
- clear the token on logout

## What Stayed the Same

These parts did not fundamentally change:

- `User` domain model
- `Role`
- role-based authorization rules
- service-layer ownership checks
- `/api/auth/me` concept
- event and booking business logic

So the migration changed the auth mechanism, not the business model.

## Testing Status

Integration tests were updated from session-based auth to bearer-token auth.

That includes:
- auth flow tests
- protected endpoint tests
- attendee/admin authorization tests

Current result:
- tests pass with JWT auth

## Current Limitations

Not implemented yet:

- refresh token flow
- token revocation / blacklist
- server-side logout invalidation
- silent token refresh

These are intentional future improvements, not bugs in the current migration.

## Production-Ready Next Step

The next production-hardening phase should be:

- short-lived access token
- refresh token
- refresh token in secure `httpOnly` cookie
- token rotation / revocation strategy

That is the planned improvement path after this current JWT milestone.
