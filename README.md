# Learning API

Accepts user-authenticated Learning requests and calls User API with a separate
Learning service identity. This guide describes the existing behavior.

## Code map

Paths are relative to `src/main/java/com/japaneselearning/learning/`.

| Package | Responsibility | Start here |
| --- | --- | --- |
| `controller` | Incoming HTTP endpoints | `LearningController`, `InternalUserController` |
| `service/impl` | Application operations | `InternalUserService`, `JwksIntegrationServiceImpl` |
| `client` | Outgoing HTTP requests and response checks | `UserServiceClient`, `JwksServiceClient` |
| `config` | HTTP clients, endpoints and OpenAPI | `ServiceClientConfig`, `ServiceEndpointProperties` |
| `security/config` | Incoming request permissions | `UserSecurityConfig` |
| `security/jwt/user` | User JWT verification, roles and properties | `JwtDecoderConfig`, `JwtRoleConverter` |
| `security/jwt/internal` | Learning JWT generation, keys, properties and attachment | `InternalJwtConfig`, `InternalJwtTokenService`, `ServiceTokenInterceptor` |
| `security/jwks` | Diagnostic headers for User and JWKS calls | `CallerMetadataInterceptor` |
| `dto` | Response contracts | `InternalUserDTO`, `response/InternalUserResponse` |
| `common/exception` | Application errors and response mapping | `GlobalExceptionHandler`, `ErrorCode` |

Learning has one incoming security chain. Its internal JWT package handles
outgoing service identity; it does not create a second incoming filter chain.
The security configuration retains the bean name `securityConfig`, and the
metadata-interceptor factory retains `jwksCallerInterceptor` for compatibility.

## Permissions and token boundaries

`UserSecurityConfig.securityFilterChain()` configures stateless user authentication.

| Route | Permission |
| --- | --- |
| GET health and documented Swagger/OpenAPI paths | No authentication required |
| `/api/v1/integration/**` | No authentication required |
| `/api/v1/learning/**` | User token with `ROLE_USER` or `ROLE_ADMIN` |
| Other routes | Authenticated |

The user JWT is verified against User's public keys, configured issuer and
timestamp/required-claim rules. Caller metadata headers do not authenticate a request.

The user lookup accepts any requested UUID after the role check. It currently
does not compare that UUID with the authenticated user's subject. No ownership
restriction is added by this reorganization.

## Follow a request through both services

Log in through User's `POST /auth/login`, then send the returned user access token:

```http
GET /api/v1/learning/internal-test/users/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <user-access-token>
```

1. Spring Security verifies the user token through `JwtDecoderConfig` and applies
   the role rules in `UserSecurityConfig` before invoking a controller.
2. `InternalUserController.getUser(id)` calls `InternalUserService.getUser(id)`.
3. That service calls `UserServiceClient.getUser(uuid)`.
4. The client builds GET `{services.user.base-url}{services.user.get-user}/{id}`
   using the `userRestClient` bean configured by `ServiceClientConfig`.
5. `CallerMetadataInterceptor.intercept()` adds caller and correlation headers.
6. When attachment is enabled, `ServiceTokenInterceptor.intercept()` invokes
   `InternalJwtTokenService.generateToken()` and sets the bearer header. This
   replaces any existing Authorization header rather than forwarding the user's token.
7. The token generator signs with Learning's active private key. Its subject and
   issuer identify Learning, and its audience identifies User API.
8. `execution.execute(request, body)` sends the request. User authenticates Learning,
   reads the database, and returns its `UserResponse` JSON.
9. `.body(InternalUserResponse.class)` deserializes that JSON. The client checks
   that a user exists in the response and its ID matches the requested UUID.
10. The response returns through Learning's service and controller to the caller.

This lookup returns a top-level `user` object directly. `/api/v1/learning/me`
uses the `APIResponse` envelope and reports the authenticated token's subject,
authorities and issuer; it does not perform the internal User lookup.

## Existing error behavior

| Situation | Handling |
| --- | --- |
| Missing/invalid incoming user token, including JWKS authentication failure | 401 through Spring Security |
| Authenticated user without a permitted role | 403 through Spring Security |
| User API returns an HTTP error, including 401 or 404 | Client throws `BusinessException(INTERNAL_SERVER_ERROR)`; Learning returns 500 |
| User connection/read failure | Same 500 mapping |
| Missing response/user or mismatched returned ID | Same 500 mapping |

`GlobalExceptionHandler` maps application errors into the `APIResponse` error
envelope. Security-filter failures have separate handling and should not be
assumed to use that envelope. The reorganization does not change error translation.

## Run locally

Use the Maven Wrapper with Java 17 or a compatible later JDK, reachable PostgreSQL,
JWKS, and User services. Supply environment variables through your IDE or shell;
Spring Boot does not automatically load `.env` files.

Core settings in `application.properties` include:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWKS_SERVICE_BASE_URL
JWKS_SERVICE_GET_PUBLIC_KEYS
JWT_JWK_SET_URI
JWT_ISSUER
```

For the authenticated User lookup, also configure:

```text
USER_SERVICE_BASE_URL=http://localhost:<user-port>
LEARNING_INTERNAL_JWT_ENABLED=true
USER_SERVICE_INTERNAL_JWT_ENABLED=true
APP_SECURITY_SIGNING_KEYS_0_KID=<learning-key-id>
APP_SECURITY_SIGNING_KEYS_0_PRIVATEKEYPATH=<host-path-to-learning-private-key>
APP_SECURITY_SIGNING_KEYS_0_ACTIVE=true
```

Both signing and attachment switches default to false in application properties.
Enabling attachment without the signer fails startup. Learning's matching public
key and key ID must be published in the JWKS source trusted by User. The issuer,
subject and audience must agree with User's internal-token validation settings.

`SERVER_PORT` defaults to `8080`; use different ports for local User and Learning
instances. Container hostnames such as `user-api` need host-reachable equivalents
for an IDE run. See [INTERNAL-JWT.md](INTERNAL-JWT.md) for signing settings and rotation.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## Debug the internal call

Start `LearningApiApplication` and User's application in Debug mode. Set breakpoints
on these statements, then call the Learning lookup endpoint with a valid user token:

| File | Breakpoint statement | Inspect |
| --- | --- | --- |
| `controller/InternalUserController.java` | `return internalUserService.getUser(id);` | Requested UUID |
| `service/impl/InternalUserService.java` | `return userServiceClient.getUser(uuid);` | Delegation |
| `client/UserServiceClient.java` | `response = restClient` | `properties`, `uuid` |
| `security/jwt/internal/InternalJwtTokenService.java` | `jwt.sign(new RSASSASigner(activeKey.privateKey()));` | Claims, header and key ID |
| `security/jwt/internal/ServiceTokenInterceptor.java` | `return execution.execute(request, body);` | Outgoing URI and attached Authorization header |
| `client/UserServiceClient.java` | `if (response == null` | Deserialized User response |

Resume Learning to send the request, then switch to User's debugger. Stepping
does not cross JVMs. User's README describes its breakpoints. To allow inspection
while User is paused, increase Learning's local `USER_SERVICE_READ_TIMEOUT` (for
example `10m`). Service tokens expire too: the default lifetime is `60s`; a local
`LEARNING_INTERNAL_JWT_TTL=300s` must remain within User's configured maximum lifetime.

Security tests mirror the main packages. `JwtAuthenticationTest` exercises incoming
JWT validation; `InternalJwtTokenServiceTest` covers signing and its configuration.
`UserServiceClientTest` verifies outgoing token attachment, timeout/error handling
and response checks. `CallerMetadataInterceptorTest` covers diagnostic headers.
