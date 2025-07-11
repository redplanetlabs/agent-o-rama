package com.rpl.agentorama.impl;

import clojure.lang.IFn;

import com.rpl.rama.RamaSerializable;
import com.rpl.rama.impl.Util;
import java.io.*;
import java.util.*;

public class NippyMap implements Map, RamaSerializable {
    private Map delegate;

    public NippyMap(Map val) {
      delegate = val;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      byte[] ser = AORHelpers.freeze(delegate);
      out.writeInt(ser.length);
      out.write(ser);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      int size = in.readInt();
      byte[] ser = new byte[size];
      in.readFully(ser);
      this.delegate = (Map) AORHelpers.thaw(ser);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return delegate.containsValue(value);
    }

    @Override
    public Object get(Object key) {
      return delegate.get(key);
    }

    @Override
    public Object put(Object key, Object value) {
      throw new RuntimeException("Unimplemented");
    }

    @Override
    public Object remove(Object key) {
      throw new RuntimeException("Unimplemented");
    }

    @Override
    public void putAll(Map m) {
      throw new RuntimeException("Unimplemented");
    }

    @Override
    public void clear() {
      throw new RuntimeException("Unimplemented");
    }

    @Override
    public Set keySet() {
      return delegate.keySet();
    }

    @Override
    public Collection values() {
      return delegate.values();
    }

    @Override
    public Set entrySet() {
      return delegate.entrySet();
    }
}
