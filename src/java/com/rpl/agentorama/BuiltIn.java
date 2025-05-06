package com.rpl.agentorama;

import com.rpl.agentorama.impl.BuiltInAgg;
import com.rpl.rama.Agg;
import com.rpl.rama.impl.AggImpl;

public class BuiltIn {
  public static BuiltInAgg AND_AGG = new BuiltInAgg(((AggImpl) Agg.and("*v")).getAgg());
  public static BuiltInAgg FIRST_AGG = new BuiltInAgg(((AggImpl) Agg.first("*v")).getAgg());
  public static BuiltInAgg LAST_AGG = new BuiltInAgg(((AggImpl) Agg.last("*v")).getAgg());
  public static BuiltInAgg LIST_AGG = new BuiltInAgg(((AggImpl) Agg.list("*v")).getAgg());
  public static BuiltInAgg MAP_AGG = new BuiltInAgg(((AggImpl) Agg.map("*k", "*v")).getAgg());
  public static BuiltInAgg MAX_AGG = new BuiltInAgg(((AggImpl) Agg.max("*v")).getAgg());
  public static BuiltInAgg MERGE_MAP_AGG = new BuiltInAgg(((AggImpl) Agg.mergeMap("*m")).getAgg());
  public static BuiltInAgg MIN_AGG = new BuiltInAgg(((AggImpl) Agg.min("*v")).getAgg());
  public static BuiltInAgg MULTI_SET_AGG = new BuiltInAgg(((AggImpl) Agg.multiSet("*v")).getAgg());
  public static BuiltInAgg OR_AGG = new BuiltInAgg(((AggImpl) Agg.or("*v")).getAgg());
  public static BuiltInAgg SET_AGG = new BuiltInAgg(((AggImpl) Agg.set("*v")).getAgg());
  public static BuiltInAgg SUM_AGG = new BuiltInAgg(((AggImpl) Agg.sum("*v")).getAgg());
}