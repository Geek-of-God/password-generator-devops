FROM eclipse-temurin:21-jdk-jammy

# Installer CrackLib et dictionnaires
RUN apt-get update && apt-get install -y --no-install-recommends \
    cracklib-runtime wamerican \
    && rm -rf /var/lib/apt/lists/*

# Générer la base CrackLib
RUN create-cracklib-dict /usr/share/dict/american-english || true

WORKDIR /app

# Copier et compiler le serveur Java
COPY src/main/java/com/devops/passwordcli/server/CrackLibServer.java /app/com/devops/passwordcli/server/CrackLibServer.java
RUN javac -d /app/classes /app/com/devops/passwordcli/server/CrackLibServer.java

EXPOSE 5000

HEALTHCHECK CMD curl -f http://localhost:5000/health || exit 1

CMD ["java", "-cp", "/app/classes", "com.devops.passwordcli.server.CrackLibServer"]
