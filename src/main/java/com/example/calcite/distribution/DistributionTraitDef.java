package com.example.calcite.distribution;

import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.rel.RelNode;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.physical.PhysicalConvention;
import com.example.calcite.physical.PhysicalUnicastExchange;

/** Definition of distribution trait. */
public class DistributionTraitDef extends RelTraitDef<Distribution> {
  public static final DistributionTraitDef INSTANCE = new DistributionTraitDef();

  private DistributionTraitDef() {}

  @Override public Class<Distribution> getTraitClass() {
    return Distribution.class;
  }

  @Override public String getSimpleName() {
    return "distribution";
  }

  @Override public Distribution getDefault() {
    return Distribution.SINGLETON;
  }

  /**
   * Converts physical node to SINGLETON distribution by adding exchange on top of it.
   * Returns null for other distributions and nodes of other conventions.
   */
  @Override public @Nullable RelNode convert(RelOptPlanner planner, RelNode rel,
      Distribution toTrait, boolean allowInfiniteCostConverters) {
    if (toTrait != Distribution.SINGLETON || rel.getConvention() != PhysicalConvention.INSTANCE) {
      return null;
    }

    return new PhysicalUnicastExchange(rel.getCluster(), rel.getTraitSet().replace(toTrait), rel);
  }

  /** Returns whether distribution can be converted to specified distribution. */
  @Override public boolean canConvert(RelOptPlanner planner, Distribution fromTrait,
      Distribution toTrait) {
    return toTrait == Distribution.SINGLETON;
  }
}
