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

might produce something like (from [LogTest.java]())

```
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "TRACE", "logDateTime": "2015-05-12T14:41:17.171Z", "lineNumber": 26, "rendered": "14:41:17.171 [main] TRACE com.blacklocus.logback.s3.LogTest - trace\n", "format": "trace", "args": [], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "DEBUG", "logDateTime": "2015-05-12T14:41:17.302Z", "lineNumber": 27, "rendered": "14:41:17.302 [main] DEBUG com.blacklocus.logback.s3.LogTest - debug\n", "format": "debug", "args": [], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "INFO", "logDateTime": "2015-05-12T14:41:17.302Z", "lineNumber": 28, "rendered": "14:41:17.302 [main] INFO  com.blacklocus.logback.s3.LogTest - info\n", "format": "info", "args": [], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "WARN", "logDateTime": "2015-05-12T14:41:17.303Z", "lineNumber": 29, "rendered": "14:41:17.303 [main] WARN  com.blacklocus.logback.s3.LogTest - warn\n", "format": "warn", "args": [], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "ERROR", "logDateTime": "2015-05-12T14:41:17.303Z", "lineNumber": 30, "rendered": "14:41:17.303 [main] ERROR com.blacklocus.logback.s3.LogTest - error\n", "format": "error", "args": [], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "TRACE", "logDateTime": "2015-05-12T14:41:17.303Z", "lineNumber": 32, "rendered": "14:41:17.303 [main] TRACE com.blacklocus.logback.s3.LogTest - trace hello\n", "format": "trace {}", "args": ["hello"], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "DEBUG", "logDateTime": "2015-05-12T14:41:17.305Z", "lineNumber": 33, "rendered": "14:41:17.305 [main] DEBUG com.blacklocus.logback.s3.LogTest - debug 5\n", "format": "debug {}", "args": ["5"], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "INFO", "logDateTime": "2015-05-12T14:41:17.339Z", "lineNumber": 34, "rendered": "14:41:17.339 [main] INFO  com.blacklocus.logback.s3.LogTest - info 0\n", "format": "info {}", "args": ["0"], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "WARN", "logDateTime": "2015-05-12T14:41:17.340Z", "lineNumber": 35, "rendered": "14:41:17.340 [main] WARN  com.blacklocus.logback.s3.LogTest - warn for reasons: [reason 1, reason 2, reason 3]\n", "format": "warn for reasons: {}", "args": ["[\"reason 1\",\"reason 2\",\"reason 3\"]"], "context": {"StartTime": "1431441677166"}}
{"loggerName": "com.blacklocus.logback.s3.LogTest", "logLevel": "ERROR", "logDateTime": "2015-05-12T14:41:17.357Z", "lineNumber": 36, "rendered": "14:41:17.357 [main] ERROR com.blacklocus.logback.s3.LogTest - error\njava.lang.Exception: This is an error\n\tat com.blacklocus.logback.s3.LogTest.main(LogTest.java:36) [json-encoder/:na]\n", "format": "error", "args": [], "context": {"StartTime": "1431441677166"}}
```


*I need arbitrary attributes to be included!*

That is what [MDC](http://logback.qos.ch/manual/mdc.html) is for.

```java
public class MyMain {

    public static void main(String[] args) {
        
        // Put it in the MDC.
        MDC.put("MachineId", getMachineId());
        
        // ... and then there was the rest
        
    }

}
```

These will get included in the **context** member of the output JSON object.
