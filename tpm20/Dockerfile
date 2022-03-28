# multi-stage builds
# execute this in parent directory

#
# Build TSS.MSR library
#
#FROM maven:3.8.4-jdk-11-slim AS build-lib
FROM maven:3.8.4-eclipse-temurin-11 AS build-lib
COPY TSS.MSR/TSS.Java/src /home/app/src
COPY TSS.MSR/TSS.Java/pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean install -Dmaven.test.skip

#
# Build tpm20 service
#
#FROM maven:3.8.4-jdk-11-slim AS build-service
FROM maven:3.8.4-eclipse-temurin-11 AS build-service
COPY --from=build-lib /root/.m2 /root/.m2
COPY tpm20/src /home/app/src
COPY tpm20/pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -Dmaven.test.skip

#
# Run tpm20 service
#
#FROM openjdk:11-jre-slim
FROM eclipse-temurin:11.0.14.1_1-jre-focal
COPY --from=build-service /home/app/target/tpm20-0.0.1-SNAPSHOT.jar /usr/local/lib/tpm20.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/tpm20.jar"]