#!/usr/bin/env bash

mkdir -p src/main/java/com/blacklocus/logback/s3/avro

avro-tools compile schema \
    src/main/avro/LogLevel.avsc \
    src/main/avro/RawLog.avsc \
    src/main/java