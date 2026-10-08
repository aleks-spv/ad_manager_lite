# Active Directory Manager Lite

> Lightweight Android client for Active Directory user and group management over LDAP.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-28%2B-brightgreen.svg)](https://developer.android.com/about/versions/pie)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

---

## Overview

ADM Lite is a native Android application for IT administrators who need to manage Active Directory users and groups on the go. Connect to one or more AD servers, search users, change passwords, modify group memberships, rename accounts, and toggle account states — all from a clean Material 3 interface.

## Features

### Server Management
- **Multi-server support** — add, edit, and switch between multiple AD servers
- **TLS flexibility** — LDAPS, START TLS, or plain LDAP connections
- **Credential storage** — passwords encrypted with Android Keystore (AES-256-GCM)
- **Custom CA certificates** — import your organization's root CA for internal PKI
- **Trust-all mode** — skip certificate validation for lab/dev environments (with security warning)

### User Operations
- **Search users** — by `sAMAccountName`, `displayName`, `mail`, `userPrincipalName`, or UPN with paged results
- **Change password** — with secure channel enforcement (configurable)
- **Modify group membership** — add or remove users from AD groups
- **Rename users** — updates `givenName`, `sn`, `displayName` and renames CN/DN. `sAMAccountName` and UPN are unchanged
- **Toggle account state** — enable or disable user accounts
- **Unlock account** — reset `lockoutTime` and `badPwdCount`
- **Account properties** — force password change, password never expires, user cannot change password
- **Reset password** — admin reset over a secure channel

### Group Operations
- **Search groups** — by `cn` or `displayName` with paged results
- **Modify group membership** — add or remove the user from selected groups

### Settings & Data
- **Secure channel enforcement** — require TLS for password change operations
- **Connection timeouts** — configurable connect and response timeouts
- **Data export/import** — backup and restore server configurations as JSON

## Architecture

```
com.adm.lite/
├── AdmApplication.kt          # Application entry point
├── MainActivity.kt            # Single-activity host
├── data/
│   ├── crypto/                # Android Keystore credential encryption
│   ├── db/                    # Room database (server persistence)
│   ├── prefs/                 # DataStore preferences (app settings)
│   └── repo/                  # Server repository (data layer)
├── di/                        # Manual dependency injection
├── ldap/
│   ├── AdAttributeCodec.kt    # AD attribute encoding (unicodePwd)
│   ├── LdapConnectionFactory.kt  # TLS/plain connection builder
│   ├── LdapErrorMapper.kt     # LDAP error → user-friendly messages
│   ├── LdapSession.kt         # Connection lifecycle management
│   └── ops/                   # Use case implementations
│       ├── AccountPropertiesImpl.kt
│       ├── ChangePasswordImpl.kt
│       ├── ModifyGroupsImpl.kt
│       ├── EditNameImpl.kt
│       ├── SearchGroupsImpl.kt
│       ├── SearchUsersImpl.kt
│       ├── ToggleAccountEnabledImpl.kt
│       ├── UnlockAccountImpl.kt
│       └── UseCases.kt        # Use case interfaces + domain models
├── model/                     # Domain models (AdServer, AdUser, AdGroup, etc.)
└── ui/
    ├── actions/               # User action dialogs (password, groups, rename)
    ├── connect/               # Login/connection screen
    ├── nav/                   # Navigation graph
    ├── servers/               # Server list + editor
    ├── settings/              # App settings
    ├── theme/                 # Material 3 theme
    └── users/                 # User list + search
```

### Design Decisions

- **Manual DI** — no Hilt/Dagger; `AppContainer` wires dependencies at startup for simplicity
- **Use-case pattern** — each LDAP operation is a standalone use case behind an interface, enabling testability
- **AdError with enum Kind** — covers auth, timeout, TLS, and server errors with structured data
- **Room for persistence** — server configurations survive app restarts
- **DataStore for preferences** — lightweight settings storage replacing SharedPreferences

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose (BOM 2026.08) + Material 3 |
| Navigation | Navigation Compose 2.9.8 |
| Database | Room 2.8.4 |
| Preferences | DataStore 1.1.7 |
| LDAP | UnboundID LDAP SDK 7.0.5 |
| Crypto | Android Keystore (AES-256-GCM) |
| Concurrency | Kotlin Coroutines 1.11.0 |
| Testing | JUnit 4 + Robolectric 4.17 |
| Build | Gradle Kotlin DSL + KSP |

## Requirements

- **Android 9.0+** (API level 28)
- **Target SDK 37**
- **JDK 17+** (build toolchain)
- Network access to your Active Directory server

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17+

### Build

```bash
# Clone the repository
git clone https://github.com/aleks-spv/ad_manager_lite.git
cd active-directory-apk

# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

Без локального JDK/SDK (нужен только Docker):

```bash
./build-env/build.sh    # APK
./build-env/test.sh     # unit-тесты
./build-env/lint.sh     # Android lint
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

### Connecting to Active Directory

1. Open the app and tap **+** to add a server
2. Enter your AD server details:
   - **Name** — display name for this server
   - **Host** — hostname or IP (e.g., `dc01.example.com`)
   - **Port** — typically `636` (LDAPS), `389` (START TLS), or `389` (plain)
   - **Base DN** — search base (e.g., `DC=example,DC=com`)
   - **Bind DN** — admin account (e.g., `CN=Admin,OU=Users,DC=example,DC=com`)
   - **Password** — bind password (encrypted with Android Keystore if "Remember" is checked)
   - **TLS Mode** — LDAPS / START TLS / Plain

3. Tap **Save**, then **Connect**

### Security Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Require secure channel for password | **On** | Block password changes over unencrypted connections |
| Trust all certificates | **Off** | Skip TLS certificate validation (use only in lab environments) |
| Custom CA PEM | Empty | Paste your organization's root CA certificate for internal PKI |

> **Note:** Even with "Require secure channel" disabled, Active Directory itself will refuse `unicodePwd` writes over unencrypted connections (except AD LDS with specific configuration).

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | LDAP connection to AD servers |

## Testing

```bash
./gradlew testDebugUnitTest   # или ./build-env/test.sh
./gradlew lintDebug           # или ./build-env/lint.sh
```

The test suite covers:
- LDAP use cases (search, password change, rename, groups, account properties, unlock, toggle)
- Security policy: secure-channel enforcement and export/import of credentials
- Domain model validators, error mapping, Room DAO, settings store
- Android Keystore credential codec (Robolectric)

## Export / Import

Backup your server configurations via **Settings → Export**. The exported JSON file can be restored on the same or another device via **Settings → Import**. Passwords are never included in the export.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

### Code Style

- Kotlin coding conventions
- Compose best practices (state hoisting, preview functions)
- Each use case gets its own file under `ldap/ops/`
- Tests for every use case and validator

## Security

- Passwords are encrypted at rest using **AES-256-GCM** via Android Keystore
- Credentials never leave the device
- TLS certificate validation is enforced by default
- No analytics, telemetry, or network calls beyond LDAP

### Security considerations

- **TLS** — LDAPS and START TLS validate the certificate chain against system CAs (or a custom CA PEM) and the server hostname via `HostNameSSLSocketVerifier`. Hostname verification is only disabled when *Trust all certificates* is explicitly enabled in Settings.
- **Custom CA PEM** — every certificate in the PEM is loaded, so a chain can be pasted as-is. A malformed PEM surfaces as an `AdError.Kind.TLS` error instead of a crash.
- **Plain LDAP** — `PLAIN` mode sends the bind password in clear text. The editor and the connect screen show a warning. *Require secure channel for password change* (on by default) refuses password operations over `PLAIN`.
- **LDAP filters** — all filters are built with `Filter.create*` factories; user input is never concatenated into filter strings.
- **Credentials** — stored encrypted in DataStore via Android Keystore, never included in export/import JSON. A corrupted or undecryptable record is discarded and the password must be re-entered.
- **Errors** — LDAP failures are mapped through `LdapErrorMapper` to a closed set of `AdError.Kind` values.

### Known limitations

- No certificate pinning — trust anchors are system CAs or a user-supplied CA.
- *Trust all certificates* disables both chain and hostname verification; lab use only.
- Rename updates `givenName`, `sn`, `displayName` and the CN/DN. `sAMAccountName` and `userPrincipalName` are intentionally never rewritten, so UPN-based logins keep working.
- One active directory session at a time; the last bind credentials are cached in memory for transparent reconnection.
- No MFA, no client-certificate authentication, no SASL/GSSAPI (Kerberos) bind.
- Directory-level delegation/rights are not modelled — an operation can fail with `INSUFFICIENT_RIGHTS` at any time.

If you discover a security vulnerability, please report it responsibly by email to **aleks.spv@gmail.com** (see [SECURITY.md](SECURITY.md)). Please do not open a public issue.

## License

```
Copyright 2026 Alexandr <aleks.spv@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
