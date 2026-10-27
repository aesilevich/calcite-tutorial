package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Physical node for table scan. */
public class PhysicalTableScan extends TableScan implements PhysicalNode {
  /** Filtering condition for physical table scan. */
  private final @Nullable RexNode filter;

  public PhysicalTableScan(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelHint> hints, RelOptTable table, @Nullable RexNode filter) {
    super(cluster, traitSet, hints, table);
    this.filter = filter;
  }

  /** Returns filter condition for scan, or null if there is no filter. */
  public @Nullable RexNode getFilter() {
    return filter;
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new PhysicalTableScan(getCluster(), traitSet, getHints(), table, filter);
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) {
    double rowCount = super.estimateRowCount(mq);
    if (filter == null) {
      return rowCount;
    }

    Double selectivity = mq.getSelectivity(this, filter);
    return selectivity == null ? rowCount : rowCount * selectivity;
  }

  @Override public RelWriter explainTerms(RelWriter pw) {
    return super.explainTerms(pw).itemIf("filter", filter, filter != null);
  }
}
