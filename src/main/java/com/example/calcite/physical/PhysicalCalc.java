package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Calc;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rex.RexProgram;

/** Physical calc node. */
public class PhysicalCalc extends Calc implements PhysicalNode {
  public PhysicalCalc(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints,
      RelNode input, RexProgram program) {
    super(cluster, traitSet, hints, input, program);
  }

  @Override public Calc copy(RelTraitSet traitSet, RelNode input, RexProgram program) {
    return new PhysicalCalc(getCluster(), traitSet, getHints(), input, program);
  }
}
