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
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Physical node for table scan. */
public class PhysicalTableScan extends TableScan implements PhysicalNode {
  /** Filtering condition for physical table scan, refers to physical table columns. */
  private final @Nullable RexNode filter;

  /** Indexes of physical table columns returned by scan. */
  private final @Nullable ImmutableIntList projection;

  public PhysicalTableScan(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelHint> hints, RelOptTable table, @Nullable RexNode filter,
      @Nullable ImmutableIntList projection) {
    super(cluster, traitSet, hints, table);
    this.filter = filter;
    this.projection = projection;
  }

  /** Returns filter condition for scan, or null if there is no filter. */
  public @Nullable RexNode getFilter() {
    return filter;
  }

  /** Returns indexes of table columns returned by scan, or null if there is no projection. */
  public @Nullable ImmutableIntList getProjection() {
    return projection;
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new PhysicalTableScan(getCluster(), traitSet, getHints(), table, filter, projection);
  }

  @Override public RelDataType deriveRowType() {
    RelDataType tableRowType = table.getRowType();
    if (projection == null) {
      return tableRowType;
    }

    RelDataTypeFactory.Builder builder = getCluster().getTypeFactory().builder();
    for (int index : projection) {
      builder.add(tableRowType.getFieldList().get(index));
    }
    return builder.build();
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
    return super.explainTerms(pw)
        .itemIf("filter", filter, filter != null)
        .itemIf("projection", projection, projection != null);
  }
}
