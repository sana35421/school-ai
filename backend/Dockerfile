# Use the existing server image as an offline Java 17 runtime base. This avoids
# requiring Docker Hub access during deployments in the restricted ECS network.
FROM school-ai-backend:latest
WORKDIR /app
COPY target/school-ai-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "/app/app.jar"]
