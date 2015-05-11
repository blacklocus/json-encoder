/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 BlackLocus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.blacklocus.logback.s3;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.LineOfCallerConverter;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.blacklocus.logback.s3.avro.LogLevel;
import com.blacklocus.logback.s3.avro.RawLog;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// We need our own encoder to correctly initialize the layout.
public class JsonEncoder<E> extends LayoutWrappingEncoder<E> {

    private static final String DEBUG_NAME = "[JsonAppender]";
    private static final String NEW_LINE = System.getProperty("line.separator", "\n");

    private ObjectMapper mapper = new ObjectMapper();
    private PatternLayout renderPatternLayout;

    // Configurable things

    String pattern;
    boolean lineNumbers = false;
    boolean debug = false;

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setLineNumbers(boolean lineNumbers) {
        this.lineNumbers = lineNumbers;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void start() {
        Layout<E> layout = new LayoutBase<E>() {
            @Override
            public String doLayout(E event) {
                return _doLayout(event);
            }
        };
        layout.setContext(context);
        layout.start();
        setLayout(layout);

        if (null != pattern) {
            PatternLayout renderPatternLayout = new PatternLayout();
            renderPatternLayout.setContext(context);
            renderPatternLayout.setPattern(pattern);
            renderPatternLayout.start();
            this.renderPatternLayout = renderPatternLayout;
        }

        super.start();
    }

    String _doLayout(E eventObject) {
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
                return rawLog.toString() + NEW_LINE;
            }

        } catch (Exception e) {
            debug(e);
        }

        return null; // hmmmmmm
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
}
