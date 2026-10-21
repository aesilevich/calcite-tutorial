package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rex.RexNode;

/** Physical node for filter */
public class PhysicalFilter extends Filter implements PhysicalNode {
  public PhysicalFilter(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints,
      RelNode input, RexNode condition) {
    super(cluster, traitSet, hints, input, condition);
  }

  @Override public Filter copy(RelTraitSet traitSet, RelNode input, RexNode condition) {
    return new PhysicalFilter(getCluster(), traitSet, getHints(), input, condition);
  }
}
