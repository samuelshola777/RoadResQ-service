# road-resQ-service

Clean Spring Boot starter generated from Spring Initializr and adjusted for Road ResQ.

- Java: 21
- Spring Boot: 4.1.2
- Maven wrapper: 3.9.11
- Package: com.roadresq.roadresq
- Main class: com.roadresq.roadresq.RoadResQService
- Port: 8072
- Packaging: Jar
- Dependencies: Spring Web MVC, Spring Security, Spring Data MongoDB, Validation, Lombok.

## Open in IntelliJ

Extract into a new location and open pom.xml as a Maven project.
Set Project SDK, Maven importer JDK and Maven runner JRE to Java 21.
Select the Maven wrapper (3.9.11) or a compatible bundled Maven 3 installation.
Reload Maven after import.

## Configure MongoDB

Copy env.properties.example to env.properties and set MONGODB_URI to the same
MongoDB database used by shared auth. No real credentials are included.

## Run from PowerShell

```powershell
Copy-Item env.properties.example env.properties
# Edit env.properties and set your actual MongoDB URI.
.\mvnw.cmd spring-boot:run
```

This archive supplies a clean project skeleton and dependencies. Your business
controllers, vendor services, shared JWT/platform verification and API-key
filters still need to be copied/integrated using the correct package root.
The starter has Spring Security's standard development authentication defaults;
it does not implement your shared platform login by itself.

Do not overwrite your existing project without backing it up first.

## Verification

The Maven 3.9.11 wrapper resolved dependencies and compiled the source successfully
using a Java 17 verification override in this environment. The delivered POM
still targets Java 21. A Java 21 application run and live MongoDB connection
were not tested here.