package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.SingleRel;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.distribution.Distribution;
import com.example.calcite.distribution.DistributionTraitDef;

import static java.util.Objects.requireNonNull;

/**
 * Physical node for unicast data exchanges in distributed systems.
 */
public class PhysicalUnicastExchange extends SingleRel implements PhysicalNode {
  public PhysicalUnicastExchange(RelOptCluster cluster, RelTraitSet traitSet, RelNode input) {
    super(cluster, traitSet, input);
  }

  /** Returns target distribution. */
  public Distribution getDistribution() {
    return requireNonNull(getTraitSet().getTrait(DistributionTraitDef.INSTANCE), "distribution");
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new PhysicalUnicastExchange(getCluster(), traitSet, sole(inputs));
  }

  /** Returns estimated const for exchange operation based on number of transferred bytes. */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    double rowCount = mq.getRowCount(this);
    double bytes = rowCount * getRowType().getFieldCount() * 4;
    return planner.getCostFactory().makeCost(bytes, rowCount, bytes);
  }

  @Override public RelWriter explainTerms(RelWriter pw) {
    return super.explainTerms(pw).item("distribution", getDistribution());
  }
}
