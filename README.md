# SmartBank — Enterprise Banking Management System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Security](https://img.shields.io/badge/Spring%20Security-6.x-blue.svg)](https://spring.io/projects/spring-security)
[![Tests](https://img.shields.io/badge/Tests-151%20Passed-success.svg)]()
[![Build](https://img.shields.io/badge/Build-SUCCESS-success.svg)]()

SmartBank is a comprehensive simulated Core Banking Management System designed for enterprise demonstration and academic internship evaluation. Built on **Java 21** and **Spring Boot 3.2.0**, it features zero-trust authorization, double-entry bookkeeping, dual-control Maker-Checker workflows, deterministic concurrency control, and simulated Indian banking statutory compliance (RBI & Income Tax).

---

## Architecture & Security Highlights

1. **Zero-Trust Authorization & Data Isolation (Zero IDOR):**
   - Role-Based Access Control supporting 13 banking roles.
   - Server-side account ownership verification (`AuthorizationService`) prevents horizontal privilege escalation.
   - Customer sessions are strictly isolated; customers can only view and operate on accounts registered to their identity.

2. **Double-Entry Bookkeeping Ledger:**
   - Every financial transaction (Deposit, Withdrawal, Transfer) records balanced Debit and Credit entries across internal bank accounts (`CASH_VAULT`, `CUSTOMER_DEPOSITS`, `CLEARING_ACCOUNT`, `TDS_PAYABLE`).
   - Automated ledger integrity verification ensures $\sum \text{Debits} = \sum \text{Credits}$ at all times.

3. **Dual-Control Maker-Checker Governance:**
   - High-value transactions exceeding role thresholds automatically enter a `PENDING_APPROVAL` queue.
   - Enforces strict dual control: the initiating Maker cannot act as Checker for their own transaction.

4. **Deterministic Concurrency & Debounce Protection:**
   - Consistent alphanumeric pessimistic row locking prevents deadlocks on concurrent bidirectional transfers.
   - Double-checked locking with a 3-second debounce window serializes simultaneous identical submissions and prevents duplicate debiting.

5. **Indian Banking & Tax Statutory Simulation:**
   - **Payment Channel Rules:** RTGS minimum ₹2,00,000 threshold (RBI Master Direction), UPI ₹1,00,000 cap, IMPS ₹5,00,000 cap.
   - **Cash Deposit Compliance:** Rule 114B PAN/Form 60 validation for cash deposits > ₹50,000; Section 269ST(iii) banking exemption logging; Rule 114E SFT-005 reporting for aggregate cash deposits $\ge$ ₹10,00,000 in a Financial Year.
   - **TDS Withholding:** Section 194N simulated 2% TDS on annual cash withdrawals exceeding ₹1 Crore (or exceeding ₹20 Lakh for non-ITR filers).
   - **Customer Protection:** RBI circular on customer liability in unauthorized electronic transactions (3-day zero liability, shadow credit, 90-day dispute resolution).
   - **Positive Pay System (PPS):** Cheque re-verification simulation for cheques > ₹50,000.

---

## Technology Stack

- **Backend:** Java 21, Spring Boot 3.2.0 (Spring MVC, Spring Data JPA, Spring Security, Validation)
- **Database:** MySQL 8.x (production/runtime), H2 In-Memory (automated test suite)
- **ORM / Persistence:** Hibernate 6.4 (Jakarta Persistence API)
- **Frontend / Templating:** Thymeleaf, Bootstrap 5.3.2, Bootstrap Icons
- **Build Tool:** Apache Maven 3.9+ (via Maven Wrapper `mvnw`)

---

## Getting Started

### Prerequisites
- **JDK 21** installed and configured (`JAVA_HOME` pointing to JDK 21).
- **MySQL 8.x** running locally (or cloud MySQL provider).

### Environment Configuration
The application reads environment variables with safe development defaults:

| Variable | Description | Default Fallback |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for MySQL database | `jdbc:mysql://localhost:3306/smartbank?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `root` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `gokul@123` |
| `PORT` | HTTP server listening port | `8080` |

### Running Automated Tests
```powershell
.\mvnw.cmd test
```
*Current test suite: 151 tests passed across 10 test classes, 0 failures, 0 errors.*

### Packaging for Production
```powershell
.\mvnw.cmd clean package
```
Generates executable archive at `target/smartbank-1.0.0.jar`.

### Starting the Application
```powershell
# Option A: Run via Maven Wrapper
.\mvnw.cmd spring-boot:run

# Option B: Run via packaged JAR
java -jar target/smartbank-1.0.0.jar
```
Access the application at `http://localhost:8080`.

---

## Quick Demo Credentials (Pre-Seeded)

The database automatically seeds test accounts and role users on first launch:

| Role | Username | Password | Account / Branch | Dashboard Route |
|---|---|---|---|---|
| **Customer Alpha** | `customer_a` | `Customer@123` | `SB3532006893` (BR-001) | `/customer/dashboard` |
| **Customer Beta** | `customer_b` | `Customer@123` | `SB7505485085` (BR-001) | `/customer/dashboard` |
| **Teller Officer** | `teller_a` | `Teller@123` | Branch BR-001 | `/staff/teller` |
| **Customer Service** | `cso_a` | `Cso@123` | Branch BR-001 | `/staff/cso` |
| **Branch Manager** | `manager_a` | `Manager@123` | Branch BR-001 | `/manager/dashboard` |
| **Compliance Officer**| `compliance_a` | `Compliance@123` | Bank-wide | `/compliance/dashboard` |
| **Risk Officer** | `risk_a` | `Risk@123` | Bank-wide | `/compliance/dashboard` |
| **Internal Auditor** | `auditor_a` | `Auditor@123` | Bank-wide | `/auditor/dashboard` |
| **Bank Administrator**| `admin_a` | `Admin@123` | Bank-wide | `/admin/dashboard` |
| **Super Administrator**| `superadmin` | `SuperAdmin@123` | System-wide | `/admin/dashboard` |

*Tip: The login page includes quick-fill buttons to auto-populate test credentials.*

---

## Statutory & Educational Disclaimer

> [!NOTE]
> SmartBank is an academic simulation and educational software project developed for internship demonstration. It is **not** an RBI-licensed banking company, nor is it connected to live UPI (NPCI), IMPS, NEFT, RTGS, CBDT tax systems, or real financial clearing networks. All monetary values, taxes, and customer records represent simulated test scenarios.

---

## Author
- **Gokulraj A R** — Banking Application Development & Security Architecture
