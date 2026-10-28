# Etapa de compilación: no requiere JDK/Maven en la máquina anfitriona y fija el entorno de build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# Usuario sin privilegios
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /build/target/toxicity-inference-service-*.jar app.jar

# El modelo NO va en la imagen: se monta en /models (ver README). Sin secretos en la imagen:
# TOXICITY_API_KEY debe pasarse en tiempo de ejecución.
ENV TOXICITY_MODEL_PATH=/models/onnx/model.onnx \
    TOXICITY_TOKENIZER_PATH=/models/tokenizer.json \
    JAVA_OPTS="-XX:MaxRAMPercentage=70"
USER 10001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
