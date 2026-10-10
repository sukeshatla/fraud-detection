# One image recipe for every Spring Boot service:
#   docker build -f infra/docker/service.Dockerfile --build-arg MODULE=scoring-service .
# Expects the jar to be built first (./mvnw -DskipTests package). Building outside Docker keeps the
# Maven cache warm and the image build to a few seconds.

FROM eclipse-temurin:21-jre AS layers
ARG MODULE
WORKDIR /build
COPY ${MODULE}/target/${MODULE}-*.jar app.jar
# Spring Boot layered jar: dependencies change rarely, application code often. Separate layers mean
# a code change re-pushes ~100 KB instead of ~100 MB.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER app
# Container-aware heap; virtual threads need no thread-pool tuning.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
