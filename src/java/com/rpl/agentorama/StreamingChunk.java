package com.rpl.agentorama;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.RamaSerializable;

public class StreamingChunk<T> implements RamaSerializable {
  private long _invokeId;
  private int _index;
  private T _chunk;

  public StreamingChunk(long invokeId, int index, T chunk) {
    _invokeId = invokeId;
    _index = index;
    _chunk = chunk;
  }

  public long getInvokeId() {
    return _invokeId;
  }

  public long getIndex() {
    return _index;
  }

  public T getChunk() {
    return _chunk;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeLong(_invokeId);
    out.writeInt(_index);
    byte[] ser = AORHelpers.freeze(_chunk);
    out.writeInt(ser.length);
    out.write(ser);

  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    _invokeId = in.readLong();
    _index = in.readInt();
    int size = in.readInt();
    byte[] ser = new byte[size];
    in.readFully(ser);
    this._chunk = (T) AORHelpers.thaw(ser);
  }
}
