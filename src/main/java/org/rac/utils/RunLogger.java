package org.rac.utils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class RunLogger {

    public static FileAppender<ILoggingEvent> start(File pngDir) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder enc = new PatternLayoutEncoder();
        enc.setContext(lc);
        enc.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n");
        enc.start();
        FileAppender<ILoggingEvent> fa = new FileAppender<>();
        fa.setContext(lc);
        fa.setFile(new File(pngDir, "run.log").getAbsolutePath());
        fa.setEncoder(enc);
        fa.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(fa);
        return fa;
    }

    public static void stop(FileAppender<ILoggingEvent> fa) {
        if (fa == null) return;
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(fa);
        fa.stop();
    }
}
