package com.rpl.agentorama;

public interface AgentObjectOptions {
  public static Impl create() {
    return new Impl();
  }

  public static Impl threadSafe() {
    return create().threadSafe();
  }

  public static Impl disableAutoTracing() {
    return create().disableAutoTracing();
  }

  public static Impl workerObjectLimit(int amt) {
    return create().workerObjectLimit(amt);
  }

  class Impl implements AgentObjectOptions {
    public Boolean threadSafe;
    public Boolean autoTracing;
    public Long workerObjectLimit;

    public Impl threadSafe() {
      this.threadSafe = true;
      return this;
    }

    public Impl disableAutoTracing() {
      this.autoTracing = false;
      return this;
    }

    public Impl workerObjectLimit(int amt) {
      this.workerObjectLimit = (long) amt;
      return this;
    }
  }
}
