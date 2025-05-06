// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;

public interface MultiAgg {
  public static MultiAgg.Impl create() {
    return (MultiAgg.Impl) AORHelpers.CREATE_MULTI_AGG.invoke();
  }

  public static <S> MultiAgg.Impl init(RamaFunction0<S> impl) {
    return create().init(impl);
  }
  
  public static <S> MultiAgg.Impl on(String name, RamaFunction1<S,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0> MultiAgg.Impl on(String name, RamaFunction2<S,T0,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1> MultiAgg.Impl on(String name, RamaFunction3<S,T0,T1,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1,T2> MultiAgg.Impl on(String name, RamaFunction4<S,T0,T1,T2,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1,T2,T3> MultiAgg.Impl on(String name, RamaFunction5<S,T0,T1,T2,T3,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1,T2,T3,T4> MultiAgg.Impl on(String name, RamaFunction6<S,T0,T1,T2,T3,T4,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1,T2,T3,T4,T5> MultiAgg.Impl on(String name, RamaFunction7<S,T0,T1,T2,T3,T4,T5,Object> impl) {
    return create().on(name, impl);
  }
  
  public static <S,T0,T1,T2,T3,T4,T5,T6> MultiAgg.Impl on(String name, RamaFunction8<S,T0,T1,T2,T3,T4,T5,T6,Object> impl) {
    return create().on(name, impl);
  }
  

  interface Impl {
    <S> Impl init(RamaFunction0<S> impl);
    <S> MultiAgg.Impl on(String name, RamaFunction1<S,Object> impl);
    <S,T0> MultiAgg.Impl on(String name, RamaFunction2<S,T0,Object> impl);
    <S,T0,T1> MultiAgg.Impl on(String name, RamaFunction3<S,T0,T1,Object> impl);
    <S,T0,T1,T2> MultiAgg.Impl on(String name, RamaFunction4<S,T0,T1,T2,Object> impl);
    <S,T0,T1,T2,T3> MultiAgg.Impl on(String name, RamaFunction5<S,T0,T1,T2,T3,Object> impl);
    <S,T0,T1,T2,T3,T4> MultiAgg.Impl on(String name, RamaFunction6<S,T0,T1,T2,T3,T4,Object> impl);
    <S,T0,T1,T2,T3,T4,T5> MultiAgg.Impl on(String name, RamaFunction7<S,T0,T1,T2,T3,T4,T5,Object> impl);
    <S,T0,T1,T2,T3,T4,T5,T6> MultiAgg.Impl on(String name, RamaFunction8<S,T0,T1,T2,T3,T4,T5,T6,Object> impl);
  }
}
