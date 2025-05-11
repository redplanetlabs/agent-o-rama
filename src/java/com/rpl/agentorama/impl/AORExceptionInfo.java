package com.rpl.agentorama.impl;

import clojure.lang.IPersistentMap;

public class AORExceptionInfo extends clojure.lang.ExceptionInfo {
    public AORExceptionInfo(String s, IPersistentMap data) {
        this(s, data, null);
    }

    public AORExceptionInfo(String s, IPersistentMap data, Throwable throwable) {
        super(s + " " + data, data, throwable);
    }
}
