package com.blacklocus.logback.s3;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;

public class LogTest {
    private static final Logger LOG = LoggerFactory.getLogger(LogTest.class);

    public static void main(String[] args) throws InterruptedException {
        MDC.put("StartTime", "" + System.currentTimeMillis());

        LOG.trace("trace");
        LOG.debug("debug");
        LOG.info("info");
        LOG.warn("warn");
        LOG.error("error");

        LOG.trace("trace {}", "hello");
        LOG.debug("debug {}", 5);
        LOG.info("info {}", 1 / 3);
        LOG.warn("warn for reasons: {}", Arrays.asList("reason 1", "reason 2", "reason 3"));
        LOG.error("error", new Exception("This is an error"));

        ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
    }
}
