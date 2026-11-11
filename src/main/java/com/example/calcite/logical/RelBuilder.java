package com.example.calcite.logical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.ImmutableIntList;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Relational builder that can also create logical table scans with filter and project. */
public class RelBuilder extends org.apache.calcite.tools.RelBuilder {
  protected RelBuilder(@Nullable Context context, RelOptCluster cluster,
      @Nullable RelOptSchema relOptSchema) {
    super(context, cluster, relOptSchema);
  }

  /** Creates factory for relational builders with specified context. */
  public static RelBuilderFactory proto(Context context) {
    return (cluster, schema) -> new RelBuilder(context, cluster, schema);
  }

  /** Creates relational builder with specified cluster and schema. */
  public static RelBuilder create(RelOptCluster cluster, @Nullable RelOptSchema relOptSchema) {
    return new RelBuilder(Contexts.of(Config.DEFAULT.withBloat(-1)), cluster, relOptSchema);
  }

  @Override public RelBuilder scan(String... tableNames) {
    super.scan(tableNames);
    return this;
  }

  @Override public RelBuilder filter(RexNode... predicates) {
    super.filter(predicates);
    return this;
  }

  @Override public RelBuilder project(RexNode... nodes) {
    super.project(nodes);
    return this;
  }

  /** Adds filter condition to logical table scan on top of stack. */
  public RelBuilder withFilter(RexNode condition) {
    LogicalTableScanWithFilterAndProject scan = buildScan();
    if (scan.getFilter() != null) {
      throw new IllegalStateException("Logical table scan already has filter: " + scan);
    }

    push(new LogicalTableScanWithFilterAndProject(scan.getCluster(), scan.getTraitSet(),
        scan.getHints(), scan.getTable(), condition, scan.getProjection()));
    return this;
  }

  /** Adds projection to logical table scan on top of stack. */
  public RelBuilder withProject(RexNode... fields) {
    LogicalTableScanWithFilterAndProject scan = buildScan();
    if (scan.getProjection() != null) {
      throw new IllegalStateException("Logical table scan already has projection: " + scan);
    }

    List<Integer> projection = new ArrayList<>();
    for (RexNode field : fields) {
      if (!(field instanceof RexInputRef ref)) {
        throw new IllegalArgumentException("Field reference expected, got " + field);
      }
      projection.add(ref.getIndex());
    }

    push(new LogicalTableScanWithFilterAndProject(scan.getCluster(), scan.getTraitSet(),
        scan.getHints(), scan.getTable(), scan.getFilter(), ImmutableIntList.copyOf(projection)));
    return this;
  }

  /**
   * Pops logical table scan from top of stack and returns it as logical table scan with filter
   * and project.
   */
  private LogicalTableScanWithFilterAndProject buildScan() {
    RelNode top = build();
    if (top instanceof LogicalTableScanWithFilterAndProject scan) {
      return scan;
    }

    if (top instanceof LogicalTableScan scan) {
      return new LogicalTableScanWithFilterAndProject(scan.getCluster(), scan.getTraitSet(),
          scan.getHints(), scan.getTable(), null, null);
    }

    throw new IllegalStateException("Logical table scan expected on top of stack, got " + top);
  }
}
