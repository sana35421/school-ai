FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/school-ai-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "/app/app.jar"]
