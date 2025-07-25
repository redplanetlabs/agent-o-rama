package com.rpl.agentorama.impl;

import java.io.*;
import java.util.IdentityHashMap;
import java.util.concurrent.*;
import com.rpl.rama.ops.*;

public class LazyObjectPool implements Closeable {
  private final RamaFunction0 _builder;
  private final ConcurrentLinkedQueue<Slot> _available = new ConcurrentLinkedQueue<>();
  private final Semaphore _permits;
  private final IdentityHashMap _taken = new IdentityHashMap();

  public LazyObjectPool(int limit, RamaFunction0 builder) {
    _permits = new Semaphore(limit);
    _builder = builder;
  }

  // the timeout prevents deadlock such as:
  //  - one agent node acquires A and then B
  //  - at same time another agent node acquires B and then A
  //  - after first acquisition, both A and B are at their limits
  //  - this timeout will cause one of the callers to fail and then release
  //    any objects they currently have, allowing other one to proceed
  //  - to avoid this instability, user either has to acquire in consistent order
  //    or raise object pool limit
  public Object acquire(long timeoutMillis) {
    try {
      if(!_permits.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Could not acquire object. Consider raising object pool limit size.");
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    Slot s = _available.poll();
    Object ret = (s != null) ? s.o : _builder.invoke();
    synchronized(_taken) {
      _taken.put(ret, null);
    }
    return ret;
  }

  public void release(Object o) {
    synchronized(_taken) {
      // this should be impossible
      if(!_taken.containsKey(o)) {
        throw new RuntimeException("Released object not acquired, type: " + o.getClass());
      }
    }
    _available.add(new Slot(o));
    synchronized(_taken) {
      _taken.remove(o);
    }
    _permits.release();
  }

  @Override
  public void close() throws IOException {
    for(Object o: _taken.keySet()) {
      if(o instanceof Closeable) ((Closeable) o).close();
    }
    for(Object o: _available) {
      if(o instanceof Closeable) ((Closeable) o).close();
    }
  }

  private static class Slot {
    public final Object o;

    public Slot(Object o) {
      this.o = o;
    }
  }
}
