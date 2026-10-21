package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.RelHint;

/** Physical node for table scan. */
public class PhysicalTableScan extends TableScan implements PhysicalNode {
  public PhysicalTableScan(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelHint> hints, RelOptTable table) {
    super(cluster, traitSet, hints, table);
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new PhysicalTableScan(getCluster(), traitSet, getHints(), table);
  }
}
