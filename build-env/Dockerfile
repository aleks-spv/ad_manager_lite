FROM eclipse-temurin:21-jdk-noble

WORKDIR /work

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME

RUN mkdir -p $ANDROID_HOME/cmdline-tools

RUN curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
    -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract && \
    mv /tmp/cmdline-tools-extract/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extract

ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

RUN yes | sdkmanager --licenses > /dev/null || true

RUN sdkmanager --install "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
#RUN sdkmanager --install "platform-tools" "platforms;android-37" "build-tools;37.0.0"

RUN curl -fsSL https://services.gradle.org/distributions/gradle-9.6.0-bin.zip \
    -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip

ENV PATH=/opt/gradle-9.6.0/bin:$PATH

WORKDIR /work
