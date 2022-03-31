FROM openjdk:11-jre

ADD target/echo-client-2.3-SNAPSHOT.jar /app/

WORKDIR /app

ENTRYPOINT ["java", "-jar", "echo-client-2.3-SNAPSHOT.jar"]
