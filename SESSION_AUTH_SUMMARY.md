# Session-Based Auth Summary

This document summarizes what session-based authentication means and how it was implemented in the Youngster Events backend.

## What Session-Based Auth Is

Session-based auth means:

- the user logs in with credentials
- the backend verifies the credentials
- the backend creates authenticated server-side session state
- the browser keeps a session cookie
- later requests send that cookie automatically
- the backend reads the session and knows who the user is

In this model:

- authentication state lives on the server
- the client mainly holds a session identifier cookie

## Why We Used It

For this project, session auth was a reasonable choice because:

- it is simpler than a full JWT setup
- Spring Security supports it naturally
- role-based access control works cleanly with it
- the frontend could use `withCredentials: true`
- it was enough for attendee/admin flows

## High-Level Flow

The auth flow in this project is:

1. user registers
2. user logs in with email and password
3. backend authenticates the user
4. Spring stores authenticated user context in the session
5. browser keeps the session cookie
6. protected requests reuse that session
7. backend resolves current user from Spring Security context

## How We Implemented It

### 1. User Model

We used a `User` entity as the main identity model.

Important fields:

- `id`
- `email`
- `passwordHash`
- `role`
- `enabled`
- `firstName`
- `lastName`

Roles:

- `ATTENDEE`
- `ADMIN`

### 2. User Lookup for Security

We implemented a custom user lookup service for Spring Security.

Purpose:

- load user by email
- provide password hash to Spring Security
- provide authorities/role information

This is how Spring knows how to authenticate a login request.

### 2.1 What `UserDetailsService` Does in Code

In Spring Security, `UserDetailsService` is the bridge between:

- your application user table
- Spring Security's authentication system

Its job is:

1. receive a username value during login
2. load the matching user from the database
3. convert that user into a Spring Security `UserDetails` object

In this project:

- the username is the user's `email`
- the service loads the `User` from `UserRepository`
- it returns the stored password hash and authorities/role to Spring Security

That means Spring can then:

- compare the incoming raw password against the stored hash
- build an authenticated `Authentication` object if valid

### 2.2 Why We Needed `UserDetailsService`

Spring Security does not automatically know:

- where our users are stored
- which field should be used for login
- how roles map into authorities

So `CustomUserDetailsService` gave Spring those answers.

### 3. Password Handling

Passwords were never stored as plain text.

We used a password encoder so that:

- raw password is hashed on register
- hashed password is stored in DB
- login checks raw password against stored hash

### 3.1 Password Encoding in Code

Spring Security authentication expects:

- raw password from login request
- encoded password already stored in DB

So we configured a `PasswordEncoder` bean and used it in:

- register flow to encode passwords before saving
- login flow indirectly when Spring compares passwords

This matters because Spring Security's authentication manager uses the configured encoder when validating credentials.

### 4. Register Flow

Register flow in the project:

1. client sends register request
2. backend validates request
3. backend checks email uniqueness
4. backend hashes password
5. backend creates and saves user
6. backend returns auth/user response DTO

At this stage, register creates the account.

### 5. Login Flow

Login flow in the project:

1. client sends email and password
2. backend authenticates using Spring Security `AuthenticationManager`
3. if valid, authentication is placed into the Spring Security context
4. the HTTP session stores the authenticated state
5. response returns current user info

The important part is:

- the backend does not return a JWT
- the browser continues authenticated because of the session cookie

### 5.1 Login Flow in Code

In code, login worked roughly like this:

1. receive login request DTO
2. create a `UsernamePasswordAuthenticationToken`
3. pass it to `AuthenticationManager`
4. Spring Security calls `CustomUserDetailsService`
5. Spring loads the stored password hash and role
6. Spring compares passwords using the configured `PasswordEncoder`
7. if valid, the returned `Authentication` is stored in the `SecurityContext`
8. the HTTP session keeps that authenticated state

That is the key difference from JWT:

- session auth stores authenticated state on the server side
- JWT would return signed token state to the client instead

### 6. Security Configuration

Spring Security was configured to:

- allow public routes like public event browsing and auth endpoints
- protect attendee/admin routes
- enforce role-based access

Examples:

- admin-only event management
- attendee self-booking flows
- admin access to all bookings/users

This was done in `SecurityConfig`.

### 6.1 What `SecurityConfig` Did in Code

`SecurityConfig` was the core place where we defined:

- which routes are public
- which routes require authentication
- which routes are admin-only
- how login/logout/session behavior works

Typical responsibilities in this project:

- allow public auth endpoints
- allow public event browsing routes
- restrict admin routes with role checks
- require authentication for attendee self-service routes
- keep the standard session-based security model active

Conceptually, it told Spring Security:

- "these endpoints are public"
- "these endpoints require a logged-in user"
- "these endpoints require admin role"

### 6.2 Session Behavior in Security Config

Because this project used session auth:

- Spring Security kept authenticated state in the session
- each later request reused that state through the session cookie

So unlike JWT:

- no bearer-token filter was needed
- no token parsing step was needed on each request

### 7. Current User Resolution

To get the current authenticated user inside services, we used:

- `SecurityContextHolder`

Typical pattern:

1. read current `Authentication`
2. get current principal/email
3. load the real `User` from repository

This was used for:

- creating attendee-owned bookings
- ownership checks
- `/api/auth/me`
- attendee self-booking endpoints

### 7.1 Current User Resolution in Code

In services, we often needed the real domain `User`, not just a Spring principal string.

So the pattern was:

1. get `Authentication` from `SecurityContextHolder`
2. check whether it is authenticated
3. read the authenticated username/email
4. load the `User` entity from `UserRepository`

This allowed service methods to enforce business rules like:

- attendee can only create their own booking
- attendee can only see their own bookings
- admin can manage all records

So the session did not just secure endpoints.
It also supported domain ownership checks inside the service layer.

### 8. Logout

Logout in the project:

- invalidates the session
- clears authentication state

After logout:

- the old session is no longer valid
- protected endpoints stop working for that client

### 9. Frontend Interaction

Frontend worked with session auth by:

- calling auth endpoints normally
- relying on browser cookie handling
- sending requests with `withCredentials: true`

So the frontend did not manually attach tokens.

### 9.1 What `withCredentials: true` Meant

Because auth was session-based:

- the browser had to send the session cookie back with later requests

So the frontend API client used:

- `withCredentials: true`

That made the browser include cookies on backend requests.

Without that:

- login might succeed
- but later protected requests would not carry the session

### 10. Authorization in Business Flows

Session auth was not only used for login.

It also drove business rules like:

- attendee can only see own bookings
- admin can manage all users/bookings/events
- attendee booking owner is resolved from logged-in user

So authentication and authorization were tightly connected to service-layer business logic.

### 10.1 Why Endpoint Security Alone Was Not Enough

Even with route protection in `SecurityConfig`, we still needed service-layer authorization.

Why:

- route-level security answers "can this user reach this endpoint?"
- service-level checks answer "can this user access this specific record?"

Example:

- an attendee can reach `/api/bookings/{id}`
- but still must be blocked from reading another attendee's booking

So we used both:

- Spring Security route protection
- service-layer ownership checks

## Endpoints Involved

Main auth-related endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Protected business endpoints relied on the session automatically.

## Pros of This Approach

- simple to implement with Spring Security
- good for server-rendered or same-site app setups
- straightforward role-based auth
- no token parsing logic needed

## Limitations

- server keeps session state
- less convenient for cross-client/mobile/API token scenarios
- frontend depends on cookies and credentialed requests
- scaling and auth portability are less flexible than JWT

## Why We Are Considering JWT Now

We are considering switching to JWT because:

- frontend/backend separation is clearer
- auth becomes stateless
- easier for API-style clients
- bearer token flow is more portable

But session auth was a valid and workable first implementation for this project.

## Step-by-Step Summary

In short, we implemented session auth like this:

1. created `User` entity with email, password hash, and role
2. added password hashing
3. implemented custom user loading for Spring Security
4. configured security rules in `SecurityConfig`
5. built register endpoint
6. built login endpoint using Spring authentication/session
7. added `/api/auth/me`
8. added logout/session invalidation
9. resolved current user from `SecurityContextHolder` in services
10. enforced authorization rules across bookings, users, and events

## Code-Level Summary

The key code pieces were:

- `User`
  - application user identity with email, password hash, and role

- `UserRepository`
  - used to load users by email and by ID

- `CustomUserDetailsService`
  - loaded users for Spring Security during login

- `PasswordEncoder`
  - encoded passwords on register
  - validated passwords during login

- `AuthenticationManager`
  - handled login credential authentication

- `SecurityConfig`
  - defined public routes, protected routes, admin-only routes, and session-based security behavior

- `SecurityContextHolder`
  - used inside services to get the currently authenticated user

- auth controller/service
  - implemented register, login, logout, and `/api/auth/me`

Together, these pieces formed the full session-based auth setup in the project.
