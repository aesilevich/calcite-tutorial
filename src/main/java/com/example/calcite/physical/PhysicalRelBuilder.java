package com.example.calcite.physical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.util.Objects.requireNonNull;

/** Relational builder that can also create physical nodes. */
public class PhysicalRelBuilder extends RelBuilder {
  protected PhysicalRelBuilder(@Nullable Context context, RelOptCluster cluster,
      @Nullable RelOptSchema relOptSchema) {
    super(context, cluster, relOptSchema);
  }

  /** Creates factory for physical relational builders with specified context. */
  public static RelBuilderFactory proto(Context context) {
    return (cluster, schema) -> new PhysicalRelBuilder(context, cluster, schema);
  }

  /** Creates physical relational builder with specified cluster and schema. */
  public static PhysicalRelBuilder create(RelOptCluster cluster,
      @Nullable RelOptSchema relOptSchema) {
    return new PhysicalRelBuilder(null, cluster, relOptSchema);
  }

  /** Creates physical table scan for table with specified name and pushes it to stack. */
  public PhysicalRelBuilder physicalScan(String... tableNames) {
    RelOptSchema schema = requireNonNull(relOptSchema, "relOptSchema");
    RelOptTable table = requireNonNull(schema.getTableForMember(List.of(tableNames)),
        () -> "Table " + String.join(".", tableNames) + " not found");
    push(new PhysicalTableScan(getCluster(), physicalTraits(), List.of(), table, null));
    return this;
  }

  /** Adds filter condition to physical table scan on top of stack. */
  public PhysicalRelBuilder withFilter(RexNode condition) {
    RelNode top = build();
    if (!(top instanceof PhysicalTableScan scan)) {
      throw new IllegalStateException("Physical table scan expected on top of stack, got " + top);
    }

    if (scan.getFilter() != null) {
      throw new IllegalStateException("Physical table scan already has filter: " + scan);
    }

    push(new PhysicalTableScan(scan.getCluster(), scan.getTraitSet(), scan.getHints(),
        scan.getTable(), condition));
    return this;
  }

  /** Creates physical filter with specified condition on top of stack. */
  public PhysicalRelBuilder physicalFilter(RexNode condition) {
    RelNode input = build();
    push(new PhysicalFilter(getCluster(), physicalTraits(), List.of(), input, condition));
    return this;
  }

  /** Creates physical project with specified expressions on top of stack. */
  public PhysicalRelBuilder physicalProject(RexNode... nodes) {
    RelNode input = build();

    // using input field names for column references, like project does
    List<@Nullable String> fieldNames = new ArrayList<>();
    for (RexNode node : nodes) {
      fieldNames.add(node instanceof RexInputRef ref
          ? input.getRowType().getFieldNames().get(ref.getIndex())
          : null);
    }

    RelDataType rowType = RexUtil.createStructType(getCluster().getTypeFactory(),
        List.of(nodes), fieldNames, SqlValidatorUtil.F_SUGGESTER);
    push(new PhysicalProject(getCluster(), physicalTraits(), List.of(), input, List.of(nodes),
        rowType));
    return this;
  }

  /** Returns trait set of physical nodes. */
  private RelTraitSet physicalTraits() {
    return getCluster().traitSetOf(PhysicalConvention.INSTANCE);
  }
}
