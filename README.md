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


Sample usage with a FileAppender.
```xml

<configuration scan="true" scanPeriod="30 seconds">

  <appender name="json" class="ch.qos.logback.core.FileAppender">
    <file>${BL_JSON_LOG:-/tmp/blacklocus.json}</file>
    <append>true</append>
    
    <!-- This is us right here -->
    <encoder class="com.blacklocus.logback.s3.JsonEncoder">
      <pattern>%d [%thread] %-5level %logger{36} - %msg%n</pattern>
      <lineNumbers>true</lineNumbers>
    </encoder>
    
  </appender>
  
  <logger name="com.blacklocus" level="INFO"/>
  <!-- other loggers -->
  
  <root level="warn">
    <appender-ref ref="json"/>
  </root>

</configuration>
```
