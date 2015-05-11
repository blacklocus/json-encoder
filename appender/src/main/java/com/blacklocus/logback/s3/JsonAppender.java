package com.blacklocus.logback.s3;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.LineOfCallerConverter;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.blacklocus.logback.s3.avro.LogLevel;
import com.blacklocus.logback.s3.avro.RawLog;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonAppender<E> extends AppenderBase<E> {

    private static final String DEBUG_NAME = "[JsonAppender]";

    private ObjectMapper mapper = new ObjectMapper();
    private PatternLayout renderPatternLayout;

    // Configurable things follow

    String pattern;
    boolean lineNumbers = false;
    boolean debug = false;
    PrintStream target = System.out;

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setLineNumbers(boolean lineNumbers) {
        this.lineNumbers = lineNumbers;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setTarget(String target) {
        if (target.equalsIgnoreCase("System.out")) {
            this.target = System.out;
        } else if (target.equalsIgnoreCase("System.err")) {
            this.target = System.err;
        } else {
            throw new IllegalArgumentException(target);
        }
    }

    @Override
    public void start() {
        if (null != pattern) {
            PatternLayout renderPatternLayout = new PatternLayout();
            renderPatternLayout.setContext(context);
            renderPatternLayout.setPattern(pattern);
            renderPatternLayout.start();
            this.renderPatternLayout = renderPatternLayout;
        }

        super.start();
    }

    @Override
    protected void append(E eventObject) {
        try {

            if (eventObject instanceof ILoggingEvent) {
                ILoggingEvent e = (ILoggingEvent) eventObject;
                RawLog.Builder builder = RawLog.newBuilder()
                        .setLoggerName(e.getLoggerName())
                        .setLogLevel(LogLevel.valueOf(e.getLevel().toString()))
                        .setLogDateTime(new DateTime(e.getTimeStamp()).withZone(DateTimeZone.UTC).toString())
                        .setFormat(e.getMessage())
                        .setArgs(stringify(e.getArgumentArray()));

                Map<String, String> mdc = e.getMDCPropertyMap();
                Map<CharSequence, CharSequence> mdcCopy = new HashMap<>(mdc.size());
                for (Map.Entry<String, String> entry : mdc.entrySet()) {
                    mdcCopy.put(entry.getKey(), entry.getValue());
                }
                builder.setContext(mdcCopy);

                if (null != renderPatternLayout) {
                    String rendered = renderPatternLayout.doLayout(e);
                    builder.setRendered(rendered);
                }

                if (lineNumbers) {
                    String lineNumber = new LineOfCallerConverter().convert(e);
                    if (!CallerData.NA.equals(lineNumber)) {
                        builder.setLineNumber(Integer.valueOf(lineNumber));
                    }
                }

                RawLog rawLog = builder.build();
                target.println(rawLog);
            }

        } catch (Exception e) {
            debug(e);
        }

    }

    List<CharSequence> stringify(Object[] args) throws IOException {
        if (args == null) {
            return Collections.emptyList();
        }

        List<CharSequence> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (null == arg) {
                list.add(null);
            } else if (arg instanceof String) {
                list.add(Objects.toString(arg));
            } else {
                list.add(mapper.writeValueAsString(arg));
            }
        }
        return list;
    }

    void debug(Object message) {
        if (debug) {
            if (message instanceof Throwable) {
                ((Throwable) message).printStackTrace(System.err);
            } else {
                System.err.println(DEBUG_NAME + " " + message);
            }
        }
    }

    static class EventPair {
        ILoggingEvent event;
        RawLog rawLog;

        public EventPair(ILoggingEvent event, RawLog rawLog) {
            this.event = event;
            this.rawLog = rawLog;
        }
    }

}
