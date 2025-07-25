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
    public boolean threadSafe = false;
    public boolean autoTracing = true;
    public int workerObjectLimit = 1000;

    public Impl threadSafe() {
      this.threadSafe = true;
      return this;
    }

    public Impl disableAutoTracing() {
      this.autoTracing = false;
      return this;
    }

    public Impl workerObjectLimit(int amt) {
      this.workerObjectLimit = amt;
      return this;
    }
  }
}
