# ParcelTrack Lite

A secure parcel tracking system built with Java Spring Boot and MySQL for **STTCK3033 Secure Software Development** (Group 6).

## Team and modules

| Module | Owner | Scope | Package |
|---|---|---|---|
| M1 | Muhammad Ammar Zikri bin Amran | Login and user management | `com.parceltrack.user` |
| M2 | Wan Norsiddiqin bin Wan Sharima Azman | Parcel registration and public tracking | `com.parceltrack.parcel` |
| M3 | Muhammad Hamzi Hariri bin Bahar | Delivery update and Admin dashboard | `com.parceltrack.delivery` |

Shared code lives in `config`, `audit` and `common`.

## Tech stack

Java 17+, Spring Boot 4, Spring MVC, Spring Security, Spring Data JPA, Bean Validation, Thymeleaf, Bootstrap 5, MySQL 8, Flyway, Maven (via the included wrapper).

## Setup

You need **JDK 17 or newer** and **MySQL 8**. You do not need to install Maven: use `mvnw` / `mvnw.cmd`.

1. Create the database and a user (MySQL Workbench or the `mysql` shell):

   ```sql
   CREATE DATABASE parceltrack CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'parceltrack'@'localhost' IDENTIFIED BY 'choose-a-password';
   GRANT ALL PRIVILEGES ON parceltrack.* TO 'parceltrack'@'localhost';
   ```

2. Copy `application-local.properties.example` to `application-local.properties` in the project root and put your password in it. That file is git-ignored. **Never commit passwords.**

3. Run the app:

   ```bash
   ./mvnw spring-boot:run        # macOS / Linux / Git Bash
   mvnw.cmd spring-boot:run      # Windows cmd / PowerShell
   ```

4. Open <http://localhost:8080>.

Flyway creates the tables automatically from `src/main/resources/db/migration` on startup.

## Team rules

- Pull the latest `main` before you start work. Work on a branch (`m1/login`, `m2/tracking`, `m3/upload`, …) and open a pull request into `main`.
- Commit at least once a day, under your own name, with a clear message.
- Never change the database schema alone. Propose it to the group, then add a **new** Flyway migration (`V2__...sql`). Never edit a migration that has been pushed.
- Never commit passwords, keys or real personal data. Use synthetic test data only.
- Always print user data with `th:text`, never `th:utext`.
- Check authorization (role, parcel ownership, current Rider assignment) in the **service**, not only in the page.
- Save screenshots of every security test: input, expected result, actual result.

## Project structure

```
src/main/java/com/parceltrack/
├── config/     SecurityConfig (M1)
├── user/       M1
├── parcel/     M2
├── delivery/   M3
├── audit/      shared audit logging
└── common/     GlobalExceptionHandler, HomeController
src/main/resources/
├── templates/  fragments/layout.html, auth/, seller/, rider/, admin/, tracking/, error/
├── static/     css/, js/, vendor/bootstrap/
├── db/migration/   Flyway SQL migrations (M2)
└── application.properties
```
