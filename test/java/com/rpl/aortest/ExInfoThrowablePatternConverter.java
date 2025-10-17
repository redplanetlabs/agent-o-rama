package com.rpl.aortest;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ConverterKeys;

@Plugin(
    name = "ExInfoThrowable",
    category = PatternConverter.CATEGORY,
    printObject = true
)
@ConverterKeys({"exinfo"})
public final class ExInfoThrowablePatternConverter extends LogEventPatternConverter {

    private ExInfoThrowablePatternConverter() {
        super("exinfo", "throwable");
    }

    @PluginFactory
    public static ExInfoThrowablePatternConverter newInstance(String[] options) {
        return new ExInfoThrowablePatternConverter();
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        Throwable t = event.getThrown();
        if (t == null) return;

        toAppendTo.append(System.lineSeparator()).append(t);

        try {
            Class<?> exInfoClass = Class.forName("clojure.lang.ExceptionInfo");
            if (exInfoClass.isInstance(t)) {
                Object data = exInfoClass.getMethod("getData").invoke(t);
                toAppendTo.append(System.lineSeparator())
                          .append("ex-data: ")
                          .append(String.valueOf(data));
            }
        } catch (Throwable ignored) {}

        for (StackTraceElement ste : t.getStackTrace()) {
            toAppendTo.append(System.lineSeparator())
                      .append("\tat ").append(ste);
        }
    }
}
