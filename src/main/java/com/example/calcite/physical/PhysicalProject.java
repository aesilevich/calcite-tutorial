package com.example.calcite.physical;

import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;

/** Physical node for projection. */
public class PhysicalProject extends Project implements PhysicalNode {
  public PhysicalProject(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints,
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) {
    super(cluster, traitSet, hints, input, projects, rowType, Set.of());
  }

  @Override public Project copy(RelTraitSet traitSet, RelNode input, List<RexNode> projects,
      RelDataType rowType) {
    return new PhysicalProject(getCluster(), traitSet, getHints(), input, projects, rowType);
  }
}
