# Multi-stage build: the same image runs locally and on the server.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Tolerate a Windows checkout: strip CRLF line endings and restore the executable bit.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/hawks-platform-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 hawks
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER hawks
EXPOSE 8080
# Small-VM friendly: cap the heap relative to the container's memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
