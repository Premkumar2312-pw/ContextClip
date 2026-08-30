# =============================================================================
# ContextClip Backend — Multi-stage Docker Build
# =============================================================================
# Stage 1: Build the application JAR using Maven + JDK 25
# Stage 2: Run the JAR using a minimal JRE 25 runtime image
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Builder
# ---------------------------------------------------------------------------
# eclipse-temurin is the de-facto standard OpenJDK distribution for Docker.
# We use JDK 25 to match the project's java.version and maven compiler settings.
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /build

# Copy dependency descriptors first — Docker layer-caches the dependency
# download step as long as pom.xml has not changed.
COPY pom.xml .

# Download all dependencies in a separate layer (offline later).
# -B = batch (non-interactive), -q = quiet
RUN mvn dependency:go-offline -B -q 2>/dev/null || \
    (apt-get update -qq && apt-get install -y -qq maven && mvn dependency:go-offline -B -q)

# Copy the full source tree and build the fat JAR, skipping tests
# (tests are run separately in CI / via mvn clean test).
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
# JRE-only image — no compiler, no Maven, no source.
FROM eclipse-temurin:25-jre-noble AS runtime

# Metadata
LABEL org.opencontainers.image.title="contextclip-backend"
LABEL org.opencontainers.image.description="ContextClip Spring Boot Backend Service"

# Create a non-root system user for security best practice
RUN groupadd --system contextclip \
 && useradd  --system --gid contextclip --no-create-home contextclip

WORKDIR /app

# Copy only the fat JAR produced by the builder stage
COPY --from=builder /build/target/contextclip-backend-*.jar app.jar

# Set ownership to the non-root user
RUN chown contextclip:contextclip app.jar

USER contextclip

EXPOSE 8080

# Runtime environment variable documentation
# These MUST be supplied at container startup — no secret defaults here.
#
# Required (no default — container will fail to start without them):
#   JWT_SECRET                   — HMAC-SHA256 signing key, ≥32 chars
#   POSTGRES_PASSWORD            — PostgreSQL password
#
# Required for agent support (no default):
#   AGENT_USERNAME               — desktop agent account username
#   AGENT_PASSWORD               — desktop agent account password
#
# Optional (sensible defaults):
#   POSTGRES_DB                  — defaults to 'contextclip' in application.properties
#   POSTGRES_USER                — defaults to 'contextclip_user'
#   DB_HOST                      — defaults to 'localhost' (override to 'postgres' in Compose)
#   DB_PORT                      — defaults to 5432
#   JWT_EXPIRATION_MINUTES       — defaults to 60
#   AI_SERVICE_URL               — defaults to http://localhost:8000
#   AI_SERVICE_TIMEOUT_SECONDS   — defaults to 60

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
