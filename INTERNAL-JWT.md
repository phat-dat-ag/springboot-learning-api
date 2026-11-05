# Learning internal JWT signing and outbound authentication

Learning can generate its own short-lived service JWT using its own RSA private key.
Outbound User requests can attach a freshly signed token when explicitly enabled.
Inbound user authentication, User's internal endpoint security and JWKS are unchanged.
There is no token-generation endpoint.

## Configuration

`InternalJwtConfig` enables the signer only when `app.security.internal-jwt.enabled=true`.
Disabled local/test contexts do not load signing keys. When enabled, invalid configuration
or unreadable/invalid keys fail startup rather than delaying failure until a request.

| Environment variable | Spring property | Default / meaning |
| --- | --- | --- |
| `LEARNING_INTERNAL_JWT_ENABLED` | `app.security.internal-jwt.enabled` | Local default false; Compose default true |
| `LEARNING_INTERNAL_JWT_ISSUER` | `app.security.internal-jwt.issuer` | `learning-service` |
| `LEARNING_INTERNAL_JWT_AUDIENCE` | `app.security.internal-jwt.audience` | `user-service` |
| `LEARNING_INTERNAL_JWT_TTL` | `app.security.internal-jwt.ttl` | `60s`; whole seconds, 1..300 |
| `APP_SECURITY_SIGNING_KEYS_0_KID` | `app.security.signing.keys[0].kid` | Required when enabled; `learning-rsa-v1` |
| `APP_SECURITY_SIGNING_KEYS_0_PRIVATEKEYPATH` | `app.security.signing.keys[0].private-key-path` | Required when enabled; path to Learning's PKCS#8 PEM |
| `APP_SECURITY_SIGNING_KEYS_0_ACTIVE` | `app.security.signing.keys[0].active` | Exactly one configured key must be true |
| `LEARNING_SIGNING_KEYS_DIR` | Compose bind source only | `./data/keys/learning` on the host |
| `USER_SERVICE_INTERNAL_JWT_ENABLED` | `services.user.internal-jwt-enabled` | Default false; attach a fresh service JWT on every User-client request |

Identity, TTL, key IDs and file paths are configuration, not key contents. Do not put PEM
contents or signed JWTs into environment files or logs. The private PEM itself is secret.

The existing `JWT_ISSUER` remains the issuer of user tokens accepted by Learning; do not
change it to Learning's internal-token issuer.

## Keys and Docker

From the root, copy `.env.learning-api.example` to `.env.learning-api` on a new checkout.
Compose reads that file using Learning's own `env_file`, just as User reads `.env.user-api`.
Identical indexed variable names are isolated to the two containers; do not copy User's keys.

The development Learning pair is stored under root `data/keys/learning`:

```text
learningservice-private-v1.pem   PKCS#8 RSA private key, Learning only
learningservice-public-v1.pem    SubjectPublicKeyInfo public key, for tests/future publication
```

Root `data/` and `.env.learning-api` are already Git-ignored. The example file is tracked.
Never stage real keys or real environment files. No private key belongs in an image or Git.

Compose mounts only the Learning directory at `/run/keys/learning`, read-only, with
`create_host_path: false`. It is separate from the existing User/JWKS `./key` mount.
The directory must exist and the private files must be readable by container UID/GID 10001.
Keys are loaded at startup; replacing files or switching the active key requires a restart.

The Dockerfile needs no change: it builds the Java 17 target using JDK 21 and runs as UID 10001.
Use `docker compose config --quiet` to validate configuration before a targeted rebuild.
The key bind remains required by this Compose definition even if signing is disabled.

## Local PowerShell run

Configure the existing database, user-token issuer and JWKS variables as usual, then add:

```powershell
$env:LEARNING_INTERNAL_JWT_ENABLED='true'
$env:LEARNING_INTERNAL_JWT_ISSUER='learning-service'
$env:LEARNING_INTERNAL_JWT_AUDIENCE='user-service'
$env:LEARNING_INTERNAL_JWT_TTL='60s'
$env:APP_SECURITY_SIGNING_KEYS_0_KID='learning-rsa-v1'
$env:APP_SECURITY_SIGNING_KEYS_0_PRIVATEKEYPATH='C:\Personal\springboot-project\data\keys\learning\learningservice-private-v1.pem'
$env:APP_SECURITY_SIGNING_KEYS_0_ACTIVE='true'
```

An IDE/Maven run does not automatically load the root Compose `.env.learning-api` file.
Local private-key paths refer to Windows files; the example env file uses container paths.

## Multiple keys and rotation

Use consecutive indices, starting at zero, matching User's signing-key configuration:

```dotenv
APP_SECURITY_SIGNING_KEYS_0_KID=learning-rsa-v1
APP_SECURITY_SIGNING_KEYS_0_PRIVATEKEYPATH=/run/keys/learning/learningservice-private-v1.pem
APP_SECURITY_SIGNING_KEYS_0_ACTIVE=false
APP_SECURITY_SIGNING_KEYS_1_KID=learning-rsa-v2
APP_SECURITY_SIGNING_KEYS_1_PRIVATEKEYPATH=/run/keys/learning/learningservice-private-v2.pem
APP_SECURITY_SIGNING_KEYS_1_ACTIVE=true
```

Generate a new dedicated pair before enabling the v2 entry; no v2 production key is generated
by this implementation. All configured keys, including inactive keys, must be readable RSA
PKCS#8 private keys of at least 2048 bits. Duplicate/blank IDs, invalid paths, an empty list,
or zero/multiple active keys fail startup. Each generated JWT's `kid` comes from the selected
active key. Configuration changes do not mutate the key cached by an already running process.

Use `PRIVATEKEYPATH` without underscores between words for direct Spring environment binding.
User's first-key configuration uses an explicit `PRIVATE_KEY_PATH` placeholder, but copying that
spelling to additional automatically bound entries produces nested `private.key.path`, not
`privateKeyPath`. Learning's environment names use the canonical spelling consistently and have
a regression test binding two complete indexed keys from a system-environment property source.

Future rotation with a verifier must publish the new public key before selecting its private
key and retain old verification keys until issued tokens expire. This implementation changes neither
JWKS configuration nor receiver validation.

## Token contract and usage

`ServiceClientConfig` uses `InternalJwtTokenService` for User requests when attachment is enabled.
For other internal callers, the signing API is:

```java
var generated = internalJwtTokenService.generateToken();
// generated.token() is sensitive. Do not log it or expose it through an HTTP endpoint.
// generated.expiresIn() is the lifetime in seconds.
```

Headers: `alg=RS256`, `typ=internal+jwt`, and the active Learning `kid`.
Claims: `iss=learning-service`, `sub=learning-service`, `aud=[user-service]`, current `iat`,
`exp=iat+TTL`, and a fresh UUID `jti`. No user roles, email, user UUID or extra permissions.
There is no token caching. Tokens and key records redact sensitive
values in `toString()`. A unique `jti` alone does not prevent replay.

The implementation uses existing Nimbus dependencies, not another JWT library or User's
Jackson 2 utilities. It reads the private key once and creates a new signer for each issuance.

## Validation

Run inside `springboot-learning-api` (Windows CMD):

```cmd
mvnw.cmd -B -ntp -Dtest=InternalJwtTokenServiceTest test
mvnw.cmd -B -ntp test
mvnw.cmd -B -ntp package -DskipTests
```

Tests generate temporary keys. They verify RS256 signatures, exact header/claims, expiration,
issuer semantics, tampering, wrong-key rejection, unique IDs, redacted output, cached key loading,
invalid/weak/non-RSA keys, indexed configuration, active-key rotation and disabled behavior.
They neither read the development private key nor print JWT values. Existing user JWT tests
continue exercising the unchanged inbound security configuration. No live two-service internal
token authentication is claimed: User verification is not implemented here.

## Attaching tokens to User requests

Set `USER_SERVICE_INTERNAL_JWT_ENABLED=true` in the root `.env` (or the local process environment)
only after User is configured to trust Learning's issuer and public keys. Compose forwards this
setting to Learning. `LEARNING_INTERNAL_JWT_ENABLED=true` is also required; otherwise Learning
fails startup with a clear configuration error rather than silently sending anonymous requests.

The User RestClient adds `Authorization: Bearer <internal JWT>` for every request through that
client. It generates a fresh token for each execution, replacing an existing Authorization header
instead of forwarding a user's access token. Caller/correlation metadata and timeouts remain intact.
If signing fails, the request is not sent. JWKS clients do not receive this interceptor.

Attachment defaults to false because User currently uses its user-token decoder for all bearer
headers, including requests to anonymously permitted routes. Attaching Learning's token now makes
that decoder reject it with 401; `permitAll` does not skip bearer authentication. Do not enable
attachment against the current User configuration. User/JWKS verification changes are separate work.

Validation performed for this implementation:

- Focused Learning suite after outbound attachment: 53 passed, including all 21 internal-signing
  tests, per-request bearer/signature checks, no anonymous fallback on signing failure,
  disabled behavior and JWKS isolation.
- Prior full Learning suite (before attachment): 71 run, 70 passed, 1 failed in the existing
  `JwtAuthenticationTest.userTokenWithV1` authority-order assertion: expected `ROLE_USER`
  at index zero, received `FACTOR_BEARER`. That test and inbound authentication code are unchanged.
- User's full regression suite: 39 passed; no User source/configuration changes.
- Learning `package -DskipTests`: passed (tests compiled, not executed by this command).
- Compose configuration and Git diff whitespace checks passed.
- A temporary network-isolated container using the existing Learning image confirmed UID/GID
  10001 can read the dedicated key mount, without printing its contents.
- Running services were not rebuilt or restarted; no end-to-end internal-token authentication
  was performed. JWKS remains unchanged.
- Postman CLI validation is unavailable because its managed Private Cloud manifest cannot
  be read. Outbound HTTP tests use a local JDK server with the real configured RestClient.
