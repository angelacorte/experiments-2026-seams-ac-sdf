FROM eclipse-temurin:25
RUN mkdir -p /experiment
WORKDIR /experiment
ENV GRADLE_TASK=runAllBatch
ENV MAX_SEED=31
COPY effects effects
COPY gradle gradle
COPY src src
COPY *.kts ./
COPY *.properties ./
COPY gradlew* ./
RUN sed -i '/alias(libs.plugins.gitSemVer)/d' build.gradle.kts
RUN sed -i '/alias(libs.plugins.multiJvmTesting)/d' build.gradle.kts
RUN sed -i '/multiJvm {/,/}/d' build.gradle.kts
RUN CI=true ./gradlew runAllGraphic
RUN rm -rf data
RUN ./gradlew --stop
CMD MAX_SEED=$MAX_SEED ./gradlew $GRADLE_TASK
