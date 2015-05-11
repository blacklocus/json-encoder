package com.blacklocus.logback.s3;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.LineOfCallerConverter;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.blacklocus.logback.s3.avro.LogLevel;
import com.blacklocus.logback.s3.avro.RawLog;
import org.apache.commons.compress.utils.Charsets;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

public class AvroS3Appender<E> extends AppenderBase<E> {

    public static final String DEBUG_NAME = "[RawS3Appender]";
    public static final String THREAD_NAME_BATCHER = "RawS3Appender.batcher";
    public static final String THREAD_NAME_UPLOADER = "RawS3Appender.uploader";

    private static final EventPair TERMINATE = new EventPair(null, null);

    private ObjectMapper mapper = new ObjectMapper();
    private PatternLayout keyPatternLayout;
    private PatternLayout renderPatternLayout;
    private BlockingQueue<EventPair> rawLogQueue;
    private Thread batcherThread;
    private ExecutorService uploadExecutor;
    private AmazonS3 s3;

    // Configurable things follow

    String bucket;
    String keyPattern = "logs/";
    String renderPattern;
    int flushSize = 10000;
    Duration flushInterval = Duration.standardMinutes(5);
    boolean lineNumbers = false;
    boolean gzip = false;
    int uploadThreads = 1;
    boolean debug = false;

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public void setKeyPattern(String keyPattern) {
        this.keyPattern = keyPattern;
    }

    public void setRenderPattern(String renderPattern) {
        this.renderPattern = renderPattern;
    }

    public void setFlushSize(int flushSize) {
        this.flushSize = flushSize;
    }

    public void setFlushInterval(String flushInterval) {
        this.flushInterval = Duration.parse(flushInterval);
    }

    public void setLineNumbers(boolean lineNumbers) {
        this.lineNumbers = lineNumbers;
    }

    public void setGzip(boolean gzip) {
        this.gzip = gzip;
    }

    public void setUploadThreads(int uploadThreads) {
        this.uploadThreads = uploadThreads;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void start() {
        check(null != bucket, "<bucket> is required");
        check(null != keyPattern, "<prefix> may not be null");
        check(!flushInterval.isShorterThan(Duration.standardSeconds(5)), "<flushInterval> must be at least 5 seconds");
        check(uploadThreads >= 1, "<uploadThreads> must be positive");

        PatternLayout keyPatternLayout = new PatternLayout();
        keyPatternLayout.setContext(context);
        keyPatternLayout.setPattern(keyPattern);
        keyPatternLayout.start();
        this.keyPatternLayout = keyPatternLayout;

        if (null != renderPattern) {
            PatternLayout renderPatternLayout = new PatternLayout();
            renderPatternLayout.setContext(context);
            renderPatternLayout.setPattern(renderPattern);
            renderPatternLayout.start();
            this.renderPatternLayout = renderPatternLayout;
        }

        this.rawLogQueue = new SynchronousQueue<>(true);

        this.batcherThread = new Thread(Thread.currentThread().getThreadGroup(), () -> {
            long nextFlush = DateTime.now().withDurationAdded(flushInterval, 1).getMillis();
            List<EventPair> batch = new ArrayList<>(flushSize);

            try {
                EventPair eventPair;
                while (TERMINATE != (eventPair = rawLogQueue.poll(5, TimeUnit.SECONDS))) {

                    if (null != eventPair) {
                        batch.add(eventPair);
                    } else if (batch.isEmpty()) {
                        // No need to shorten a flush because of an empty batch. Move it up.
                        nextFlush = DateTime.now().withDurationAdded(flushInterval, 1).getMillis();
                        continue;
                    }

                    boolean sizeReached = batch.size() >= flushSize;
                    boolean intervalElapsed = System.currentTimeMillis() > nextFlush;
                    if (sizeReached || intervalElapsed && !batch.isEmpty()) {
                        debug("Passing batch to uploader with " + batch.size() + " logs.");
                        uploadExecutor.submit(new BatchUploader(batch));

                        nextFlush = DateTime.now().withDurationAdded(flushInterval, 1).getMillis();
                        batch = new ArrayList<>(flushSize);
                    }
                }
            } catch (InterruptedException e) {
                debug(e);
            }

            if (batch.size() > 0) {
                debug("Flushing final batch to uploader with " + batch.size() + " logs.");
                uploadExecutor.submit(new BatchUploader(batch));
            }

            debug(THREAD_NAME_BATCHER + " terminated.");
            uploadExecutor.shutdown();
            debug("Waiting for " + THREAD_NAME_UPLOADER + " shutdown");
            try {
                uploadExecutor.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                debug(e);
            }
            debug(THREAD_NAME_UPLOADER + " has shut down.");

        }, THREAD_NAME_BATCHER);
        this.batcherThread.setDaemon(false);
        this.batcherThread.start();

        this.uploadExecutor = new ThreadPoolExecutor(1, uploadThreads, 5, TimeUnit.MINUTES, new SynchronousQueue<>(),
                new ThreadFactory() {
                    AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(Thread.currentThread().getThreadGroup(), r,
                                THREAD_NAME_UPLOADER + "-" + counter.incrementAndGet());
                        t.setDaemon(false);
                        return t;
                    }
                },
                (r, executor) -> {
                    try {
                        executor.getQueue().put(r);
                    } catch (InterruptedException e) {
                        throw new RejectedExecutionException("Unexpected InterruptedException", e);
                    }
                });

        this.s3 = new AmazonS3Client();

//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            debug("Shutting down");
//            try {
//                rawLogQueue.put(TERMINATE);
//            } catch (InterruptedException e) {
//                debug(e);
//            }
//        }));

        super.start();
    }

    @Override
    public void stop() {
        debug("stopping");
        // This signals the batcher to stop. The batcher will flush the final batch,
        // and then shut down the uploader pool.
        try {
            rawLogQueue.put(TERMINATE);
        } catch (InterruptedException e) {
            debug(e);
        }
        super.stop();
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
                debug(rawLog);

                rawLogQueue.put(new EventPair(e, rawLog));
            }

        } catch (Exception e) {
            debug(e);
        }

    }

    void check(boolean checkPassed, String message) throws IllegalArgumentException {
        if (!checkPassed) {
            throw new IllegalArgumentException(message);
        }
    }

    List<CharSequence> stringify(Object[] args) throws IOException {
        if (args == null) {
            return Collections.emptyList();
        }

        List<CharSequence> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            String stringed = mapper.writeValueAsString(arg);
            list.add(stringed);
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

    class BatchUploader implements Runnable {
        final List<EventPair> logs;

        BatchUploader(List<EventPair> logs) {
            assert logs.size() > 0;
            this.logs = logs;
        }

        @Override
        public void run() {
            try {
                StringBuilder sb = new StringBuilder();
                for (EventPair eventPair : logs) {
                    sb.append(eventPair.rawLog.toString());
                }
                byte[] content = sb.toString().getBytes(Charsets.UTF_8);

                InputStream contentStream = new ByteArrayInputStream(content);
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentType("application/json; charset=UTF-8");
                if (gzip) {
                    try {
                        // TODO wrong usage
                        contentStream = new GZIPInputStream(contentStream);
                        meta.setContentEncoding("gzip");
                    } catch (IOException e) {
                        debug(e);
                    }
                } else {
                    meta.setContentLength(content.length);
                }

                long firstEvent = logs.get(0).event.getTimeStamp();
                long lastEvent = logs.get(logs.size() - 1).event.getTimeStamp();
                Duration duration = new Duration(firstEvent, lastEvent);
                meta.addUserMetadata("BlackLocus-Duration", duration.toString());

                String key = keyPatternLayout.doLayout(logs.get(0).event);
                s3.putObject(bucket, key, contentStream, meta);
                debug("Wrote batch to s3://" + bucket + "/" + key);
            } catch (Throwable t) {
                debug("agr");
                debug(t);
            }
        }
    }
}
