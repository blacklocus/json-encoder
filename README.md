# json-encoder
Logs messages to JSON.


```gradle
repositories {
  mavenCentral()
}

dependencies {
  compile 'com.blacklocus.logback:json-encoder:0.0.1'
}
```


Sample usage within a FileAppender
```xml
<appender name="json" class="ch.qos.logback.core.FileAppender">
  <file>${APP_ROOT:-/tmp}/${APP_NAME:-blacklocus}.json</file>
  <append>true</append>
  <encoder class="com.blacklocus.logback.s3.JsonEncoder">
    <pattern>%d [%thread] %-5level %logger{36} - %msg%n</pattern>
    <lineNumbers>true</lineNumbers>
  </encoder>
</appender>
```
