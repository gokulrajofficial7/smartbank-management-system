# SMARTBANK WORKSPACE EXECUTION & COMMAND APPROVAL POLICY

## 1. Workspace Context & Trust Boundary
- **Workspace Path:** `c:\Users\Gokulraj A R\OneDrive\Desktop\Banking Management System\smartbank`
- **Security Scope:** Local development, automated testing, continuous integration, and demo verification.

## 2. Pre-Authorized Routine Development Operations
The following routine, non-destructive development commands within this project directory are pre-approved for autonomous execution:
- **Build & Compilation:** `.\mvnw.cmd compile`, `.\mvnw.cmd test-compile`
- **Testing:** `.\mvnw.cmd test`, `.\mvnw.cmd test -Dtest=...`
- **Packaging:** `.\mvnw.cmd package`, `.\mvnw.cmd clean package`
- **Application Execution:** `java -jar target\smartbank-1.0.0.jar`, `.\mvnw.cmd spring-boot:run`
- **Local Endpoint Testing:** `Invoke-WebRequest`, `curl` targeting `http://localhost:8080/*`
- **Read-Only Database Audit:** Read-only `SELECT` queries against `smartbank` MySQL database or in-memory H2.
- **Log & File Inspection:** `Get-Content`, `dir`, `Select-String`, viewing test reports in `target/surefire-reports`.
- **Project File Editing:** Creating and modifying source code, templates, and test classes inside `src/`.

## 3. Strict Prohibitions & Actions Requiring Explicit Confirmation
The agent must **NEVER** autonomously execute the following operations without explicit user confirmation:
- Destructive database commands (`DROP DATABASE`, `TRUNCATE TABLE`, `DELETE FROM`, reset scripts).
- Mass recursive file deletion outside the `target/` build directory.
- File system access or modifications outside the project workspace root.
- Installing third-party software, executing downloaded executables, or modifying system execution policies.
- Printing, logging, or transmitting cleartext secrets, API keys, or credentials.
- Disabling Spring Security, CSRF protection, or authentication boundaries.
- Tampering with production banking networks or external services.

## 4. Test Data Safety Invariants
- All unit and integration tests must run against the isolated test profile (`@ActiveProfiles("test")`) using in-memory H2 or rollbacked transactions.
- Never alter production demo customer balances or ledger records during test runs.
- Double-entry ledger integrity (`CREDIT == DEBIT`) must remain balanced at all times.
