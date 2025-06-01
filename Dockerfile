FROM clojure:temurin-24-tools-deps AS builder

WORKDIR /build

# Install dependencies
COPY deps.edn build.clj ./
RUN clojure -P -T:build uber && \
  clojure -P -M -m com.tylerkindy.jeopardy.main

# Build
COPY src/ src
COPY resources/ resources
RUN clojure -T:build uber


FROM eclipse-temurin:24-jre-noble

RUN apt-get update && \
  apt-get install -y --no-install-recommends age && \
  rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/jeopardy.jar jeopardy.jar
COPY jeopardy.db.gz.age ./

CMD ["java", "-jar", "jeopardy.jar"]
