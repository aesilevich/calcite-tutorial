package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.util.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.distribution.Distribution;
import com.example.calcite.distribution.DistributionTraitDef;

import static java.util.Objects.requireNonNull;

/** Common interface for physical nodes. */
public interface PhysicalNode extends org.apache.calcite.rel.PhysicalNode {
  /** Returns null because node does not pass required traits to inputs by default. */
  @Override default @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(
      RelTraitSet required) {
    return null;
  }

  /** Returns null because node does not derive traits from inputs by default. */
  @Override default @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits(
      RelTraitSet childTraits, int childId) {
    return null;
  }

  /** Returns traits of node and its single input with specified distribution. */
  default Pair<RelTraitSet, List<RelTraitSet>> traitsWithDistribution(
      @Nullable Distribution distribution) {
    return Pair.of(getTraitSet().replace(distribution),
        List.of(getInput(0).getTraitSet().replace(distribution)));
  }

  /** Returns distribution of node output. */
  default Distribution getDistribution() {
    return requireNonNull(getTraitSet().getTrait(DistributionTraitDef.INSTANCE), "distribution");
  }

  /** Adds distribution to explain terms if it is not SINGLETON. */
  default RelWriter explainDistribution(RelWriter pw) {
    Distribution distribution = getDistribution();
    return pw.itemIf("distribution", distribution, distribution != Distribution.SINGLETON);
  }
}
