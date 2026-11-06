# Learning internal JWT signing and outbound authentication

Learning can generate its own short-lived service JWT using its own RSA private key.
Outbound User requests can attach a freshly signed token when explicitly enabled.
Inbound user authentication is preserved. User's internal endpoint now validates Learning's service identity through its dedicated JWKS owner URL.
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
| `USER_SERVICE_INTERNAL_JWT_ENABLED` | `services.user.internal-jwt-enabled` | Local default false, Compose true; attach a fresh service JWT on every User-client request |

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
learningservice-public-v1.pem    SubjectPublicKeyInfo public key, published by JWKS for the Learning owner
```

Root `data/` and `.env.learning-api` are already Git-ignored. The example file is tracked.
Never stage real keys or real environment files. No private key belongs in an image or Git.

Compose mounts only the configured private PEM at `/run/keys/learning/learningservice-private-v1.pem`, read-only, with
`create_host_path: false`. User and JWKS also use separate individual-file mounts; JWKS receives no private key.
The directory must exist and the private files must be readable by container UID/GID 10001.
Keys are loaded at startup; replacing files or switching the active key requires a restart.

The Dockerfile needs no change: it builds the Java 17 target using JDK 21 and runs as UID 10001.
Use `docker compose config --quiet` to validate configuration before a targeted rebuild.
The key bind remains required by this Compose definition even if signing is disabled.

## Local PowerShell run

Configure the existing database, user-token issuer and JWKS variables as usual, then add:

```powershell
$env:LEARNING_INTERNAL_JWT_ENABLED='true'
$env:USER_SERVICE_INTERNAL_JWT_ENABLED='true'
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
The `PRIVATE_KEY_PATH` spelling produces nested `private.key.path` for direct indexed environment binding, not `privateKeyPath`. Learning's environment names use the canonical spelling consistently and have
a regression test binding two complete indexed keys from a system-environment property source.

Future rotation with a verifier must publish the new public key before selecting its private
key and retain old verification keys until issued tokens expire. Register new public keys with OWNER=LEARNING and add explicit private/public file mounts to their respective containers.

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
continue exercising the unchanged inbound security configuration. User verification uses its independent internal decoder. See the root security integration report for live checks actually performed.

## Authenticated User requests

Compose enables `USER_SERVICE_INTERNAL_JWT_ENABLED=true` and signing. Every request through
`userRestClient` receives a fresh Learning-signed bearer token; incoming user tokens are replaced,
not forwarded. JWKS clients do not receive the token. Signing failures send no anonymous request.

User now uses an isolated internal decoder at `/api/v1/jwks/learning`. External user-token
validation in both applications uses `/api/v1/jwks/user`. The internal endpoint rejects anonymous
requests and normal user tokens. Do not change Learning's inbound `JWT_ISSUER` to Learning's issuer.

Run `mvnw.cmd -B -ntp verify` for signing, HTTP attachment and inbound user-token regressions.
See root `JWT-AUTHENTICATION.md` for the complete environment and trust configuration.
