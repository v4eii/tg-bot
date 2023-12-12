FROM openjdk:17

COPY target/tg-bot.jar app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]