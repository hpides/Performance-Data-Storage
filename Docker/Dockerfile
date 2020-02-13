FROM maven:3
WORKDIR /app
COPY . .
RUN mvn package -Dmaven.test.skip=true
FROM openjdk:12-alpine
RUN apk add --no-cache curl
COPY --from=0 /app/target/performance-data-storage-0.1.0.jar .
CMD java ${JVM_OPTS} -jar ./performance-data-storage-0.1.0.jar
