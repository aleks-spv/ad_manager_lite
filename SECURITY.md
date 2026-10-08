# Security Policy

## Reporting a vulnerability

Please report security issues privately by emailing **aleks.spv@gmail.com** with the
subject line `[SECURITY] AD Manager Lite`. Do not open a public issue first.

You should receive an acknowledgement within 7 days. Please allow reasonable time for a
fix before any public disclosure.

## Supported versions

Only the latest published release receives security fixes.

## Threat model

AD Manager Lite is a client for Active Directory. It holds, in memory and at rest:

| Data | Where | Protection |
|------|-------|-----------|
| AD bind password | DataStore, only when *Remember password* is on | AES-256-GCM, Android Keystore key |
| Server host/port/base DN/bind DN | Room database | Plain — not secret |
| App settings (timeouts, TLS flags, custom CA) | DataStore | Plain — not secret |

The exported JSON contains server configuration only. **Passwords are never exported.**

## Transport security

- `TlsMode.LDAPS` (port 636) and `TlsMode.START_TLS` are the recommended modes.
- Certificate chains are validated against system CAs, or against every certificate in a
  user-supplied PEM when *Custom CA* is configured.
- The server hostname is validated against the certificate CN/SAN by
  `HostNameSSLSocketVerifier`. This applies to both LDAPS and START TLS.
- Hostname and chain verification are disabled **only** when *Trust all certificates* is
  explicitly turned on in Settings. That flag is intended for lab environments.
- `TlsMode.PLAIN` sends the bind password in clear text. A warning is shown in the server
  editor and on the connect screen.

### Password operations

*Require secure channel for password change* is **on by default**. While it is enabled,
password change and admin reset are refused on a `PLAIN` connection with
`AdError.Kind.INSECURE_CHANNEL`.

## Input handling

- LDAP filters are built exclusively with `Filter.create*` factory methods. User input is
  never concatenated into a filter string.
- Renamed CNs are validated by `Validators.validateNewCn`, which rejects the RFC 4514
  special characters `, + " \ < > ; = #` and enforces a length limit.
- Bind DN input is validated as a DN, a UPN (`user@domain`) or a `DOMAIN\user` string.
- `Base64`-encoded paging cookies are decoded before being placed on the wire; a malformed
  cookie surfaces as an error rather than an unhandled exception.

## Dependency posture

| Dependency | Version |
|------------|---------|
| UnboundID LDAP SDK | 7.0.5 |
| AndroidX Security-crypto / Keystore | platform |
| Jetpack Compose / Material 3 | BOM 2026.08 |

`android:usesCleartextTraffic="false"` and a `networkSecurityConfig` are set in the
manifest. The only manifest permission used is `INTERNET`.

## Out of scope

- Certificate pinning (not implemented).
- MFA, client certificates, SASL/GSSAPI (Kerberos) bind — not implemented.
- Compromise of a device where the user has enabled *Trust all certificates*.
- Privileges of the bound AD account itself.
