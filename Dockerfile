# Build stage - full JDK to compile the multi-module reactor.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY loot-core/pom.xml loot-core/pom.xml
COPY loot-gateways/pom.xml loot-gateways/pom.xml
COPY loot-persistence/pom.xml loot-persistence/pom.xml
COPY loot-api/pom.xml loot-api/pom.xml
RUN ./mvnw -q -B -pl loot-api -am dependency:go-offline

COPY loot-core/src loot-core/src
COPY loot-gateways/src loot-gateways/src
COPY loot-persistence/src loot-persistence/src
COPY loot-api/src loot-api/src
RUN ./mvnw -q -B -pl loot-api -am package -DskipTests

# Runtime stage - JRE only, non-root user, just the built jar.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system loot && useradd --system --gid loot --shell /usr/sbin/nologin loot
COPY --from=build /build/loot-api/target/loot-api-*.jar app.jar
USER loot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
