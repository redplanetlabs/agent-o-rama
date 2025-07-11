package com.rpl.aortest;

import com.rpl.agentorama.FinishedAgg;
import com.rpl.rama.ops.RamaCombinerAgg;

public class EarlySumCombiner implements RamaCombinerAgg<Object> {
  @Override
  public Object combine(Object curr, Object arg) {
    Long ret = (Long) curr + (Long) arg;
    if(ret > 5) return new FinishedAgg(ret);
    else return ret;
  }

  @Override
  public Object zeroVal() {
    return 0L;
  }
}
