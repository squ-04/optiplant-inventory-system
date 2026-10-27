FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
ARG TARGETARCH
RUN set -eu; \
    case "$TARGETARCH" in \
      arm64) arch=aarch64; jdk_sha=bb5b2ded252f21c6090cf780b0a408ad0d83bbb557e702955643557f13231880; jre_sha=963931723891302ebb2687178532c3d793d71e2577cd058061c7efb396140a86 ;; \
      amd64) arch=x64; jdk_sha=68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb; jre_sha=6fac98f0c8202efc3135a9e9e42aa61963ec42d1c5cbb40caf231e7b14a24ef6 ;; \
      *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    mkdir -p /opt/downloads /opt/temurin-jdk /opt/temurin-jre; \
    base='https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8'; \
    curl --fail --location --retry 3 "$base/OpenJDK25U-jdk_${arch}_linux_hotspot_25.0.5_8.tar.gz" -o /opt/downloads/jdk.tar.gz; \
    echo "$jdk_sha  /opt/downloads/jdk.tar.gz" | sha256sum --check --strict; \
    curl --fail --location --retry 3 "$base/OpenJDK25U-jre_${arch}_linux_hotspot_25.0.5_8.tar.gz" -o /opt/downloads/jre.tar.gz; \
    echo "$jre_sha  /opt/downloads/jre.tar.gz" | sha256sum --check --strict; \
    tar -xzf /opt/downloads/jdk.tar.gz -C /opt/temurin-jdk --strip-components=1; \
    tar -xzf /opt/downloads/jre.tar.gz -C /opt/temurin-jre --strip-components=1; \
    rm -rf /opt/downloads
ENV JAVA_HOME=/opt/temurin-jdk
ENV PATH="/opt/temurin-jdk/bin:${PATH}"
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN java -version && mvn --version && mvn --batch-mode --no-transfer-progress -DskipTests package
# The build target also serves as the isolated Maven/Testcontainers verification image.
ENTRYPOINT []
CMD ["mvn", "--batch-mode", "--no-transfer-progress", "verify"]

FROM ubuntu:24.04@sha256:68ee22004a9109b44973313b02ff7b1de250f1de0f5ecb628d73f579aaff33ac AS runtime
RUN apt-get update && apt-get install --yes --no-install-recommends ca-certificates curl
ENV JAVA_HOME=/opt/temurin-jre
ENV PATH="/opt/temurin-jre/bin:${PATH}"
WORKDIR /app
COPY --from=build /opt/temurin-jre /opt/temurin-jre
COPY --from=build /workspace/target/inventory-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 CMD ["curl", "--fail", "--silent", "http://localhost:8080/actuator/health"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
