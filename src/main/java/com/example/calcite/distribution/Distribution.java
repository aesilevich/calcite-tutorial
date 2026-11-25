package com.example.calcite.distribution;

import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTrait;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Trait that describes location of data in distributed system. */
public final class Distribution implements RelTrait {
  /** Type of distribution. */
  public enum Type {
    /** Whole data set is available on each node. */
    REPLICATED,

    /** Data is split into disjoint parts, each node contains only subset of these parts. */
    RANDOM,

    /** Data is split into disjoint shards based on set of columns. */
    HASH_SHARDED,

    /** Whole data set is located on only one node. */
    SINGLETON,

    /** Distribution is not determined yet. */
    UNKNOWN
  }

  public static final Distribution REPLICATED = canonize(Type.REPLICATED);
  public static final Distribution RANDOM = canonize(Type.RANDOM);
  public static final Distribution HASH_SHARDED = canonize(Type.HASH_SHARDED);
  public static final Distribution SINGLETON = canonize(Type.SINGLETON);
  public static final Distribution UNKNOWN = canonize(Type.UNKNOWN);

  /** Type of distribution. */
  private final Type type;

  private Distribution(Type type) {
    this.type = type;
  }

  /** Returns canonical instance of distribution with specified type. */
  private static Distribution canonize(Type type) {
    return DistributionTraitDef.INSTANCE.canonize(new Distribution(type));
  }

  /** Returns type of distribution. */
  public Type getType() {
    return type;
  }

  /** Returns definition of distribution trait. */
  @Override public DistributionTraitDef getTraitDef() {
    return DistributionTraitDef.INSTANCE;
  }

  /**
   * Returns whether this distribution satisfies specified distribution. Any distribution
   * satisfies UNKNOWN, hash sharded distribution satisfies RANDOM, other distributions
   * satisfy only themselves.
   */
  @Override public boolean satisfies(RelTrait trait) {
    if (!(trait instanceof Distribution required)) {
      return false;
    }

    switch (required.type) {
    case UNKNOWN:
      return true;

    case RANDOM:
      return type == Type.RANDOM || type == Type.HASH_SHARDED;

    default:
      return type == required.type;
    }
  }

  @Override public void register(RelOptPlanner planner) {}

  @Override public boolean equals(@Nullable Object o) {
    return this == o || o instanceof Distribution that && type == that.type;
  }

  @Override public int hashCode() {
    return type.hashCode();
  }

  @Override public String toString() {
    return type.toString();
  }
}
