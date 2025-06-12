package com.rpl.aortest;

import com.rpl.agentorama.FinishedAgg;
import com.rpl.rama.ops.RamaAccumulatorAgg1;

public class EarlySumAccum implements RamaAccumulatorAgg1<Object, Long> {
  @Override
  public Object initVal() {
    return 0L;
  }

  @Override
  public Object accumulate(Object curr, Long arg) {
    Long ret = (Long) curr + arg;
    if(ret>10L) return new FinishedAgg(ret);
    else return ret;
  }

}