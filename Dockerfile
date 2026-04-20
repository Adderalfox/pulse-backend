FROM eclipse-temurin:17-jdk

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY . .

RUN curl -L -o sbt.tgz https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz \
    && tar -xzf sbt.tgz \
    && mv sbt /usr/local/sbt

ENV PATH="/usr/local/sbt/bin:${PATH}"

EXPOSE 9000

CMD ["sbt", "run"]