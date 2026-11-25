# Hibernate-Store-E-Commerce-Engine 🛍️

A robust backend management system developed in Java that utilizes Hibernate ORM for data persistence. This project implements an enterprise-level architecture, focusing on transactional integrity, role-based security, and efficient database session management.

##  Key Features

*   **RBAC (Role-Based Access Control)**: Authentication system with differentiated dashboards for Admin and Customer profiles.
*   **Atomic Transactions (ACID)**: Bulletproof purchasing logic: if anything fails during the checkout process (e.g., out of stock or insufficient funds), the database automatically reverts any changes (Rollback).
*   **External API Integration**: Automatic stock synchronization by consuming the DummyJSON API using `HttpClient` and mapping responses with `Gson`.
*   **DAO Architecture**: Strict separation of concerns between the data access layer (DAO), business logic layer (Controller), and the user interface (Main).
*   **Secure Session Management**: Implementation of `try-with-resources` to guarantee connection closures and prevent memory leaks.
*   **Automated Seeding**: Smart initialization of default roles and a "Super Admin" account upon system boot if the database is empty.

##  Technology Stack

*   **Language**: Java (JDK 21+)
*   **ORM**: Hibernate 7
*   **Database**: MySQL
*   **Project Manager**: Maven
*   **Optimization**: Lombok (Automated boilerplate code generation)
*   **Parsers**: Gson, org.json

## ⚙️ Installation and Setup

**Prerequisites:** JDK 21+, Maven and Docker (or your own MySQL server).

**1. Clone the repository:**
```bash
git clone https://github.com/RicardoMatosR/Hibernate-E-Commerce-Engine.git
cd Hibernate-E-Commerce-Engine
```

**2. Start the database (Docker):**
```bash
docker compose up -d
```
This starts MySQL 8.4 with the `tienda_ces` database. The tables are created automatically on the first run.

**3. Run the app:**
```bash
mvn compile exec:java
```

Log in with the demo admin account **admin@ces.com / admin123**, import the products from DummyJSON, then register a customer and buy.

### Using your own MySQL instead of Docker

The connection is **never stored in the repository**. It is read from environment variables, with defaults that match `docker-compose.yml`:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/tienda_ces` |
| `DB_USER` | `tienda` |
| `DB_PASSWORD` | `tienda` |

```bash
# Linux / macOS
export DB_USER=myuser DB_PASSWORD=mypassword
# Windows PowerShell
$env:DB_USER="myuser"; $env:DB_PASSWORD="mypassword"
```
In IntelliJ: *Run > Edit Configurations > Environment variables*.

If port 3306 is already in use, change it in `docker-compose.yml` (e.g. `"3307:3306"`) and set `DB_URL=jdbc:mysql://localhost:3307/tienda_ces`.

## 🔒 Security and Concurrency Notes

* **No secrets in the code**: database credentials come from environment variables.
* **Hashed passwords**: stored as salted PBKDF2 hashes (built into the JDK), never in plain text and never printed.
* **No race condition on the last unit**: stock and wallet are checked and updated by the database in a single atomic `UPDATE ... WHERE stock >= 1`. If two customers buy the last unit at the same time, only one succeeds. If the payment fails, the rollback returns the stock.
* **Safe API calls**: one reusable `HttpClient`, timeouts and HTTP status checks before parsing the JSON.

## 📁 Project Structure

```text
Hibernate-E-Commerce-Engine/
│
├── src/main/java/
│   ├── controller/      # Business logic and orchestration
│   ├── dao/             # Data access (Transactional CRUD operations)
│   ├── model/           # Hibernate Entities (JPA)
│   ├── database/        # HibernateUtil configuration (reads DB_* environment variables)
│   ├── security/        # PasswordHasher (salted PBKDF2)
│   └── Main.java        # Console interface and system startup
│
├── src/main/resources/  # hibernate.cfg.xml (no credentials)
├── docker-compose.yml   # Local MySQL for development
└── pom.xml              # Maven dependencies
```
