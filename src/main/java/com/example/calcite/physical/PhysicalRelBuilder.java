package com.example.calcite.physical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableFilter;
import org.apache.calcite.adapter.enumerable.EnumerableProject;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.Contexts;
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
import org.apache.calcite.util.ImmutableIntList;
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
    return new PhysicalRelBuilder(Contexts.of(Config.DEFAULT.withBloat(-1)), cluster,
        relOptSchema);
  }

  /** Creates physical table scan for table with specified name and pushes it to stack. */
  public PhysicalRelBuilder physicalScan(String... tableNames) {
    push(new PhysicalTableScan(getCluster(), physicalTraits(), List.of(), getTable(tableNames),
        null, null));
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
        scan.getTable(), condition, scan.getProjection()));
    return this;
  }

  /** Adds projection property to physical table scan on top of stack. */
  public PhysicalRelBuilder withProjection(RexNode... fields) {
    RelNode top = build();
    if (!(top instanceof PhysicalTableScan scan)) {
      throw new IllegalStateException("Physical table scan expected on top of stack, got " + top);
    }

    if (scan.getProjection() != null) {
      throw new IllegalStateException("Physical table scan already has projection: " + scan);
    }

    List<Integer> projection = new ArrayList<>();
    for (RexNode field : fields) {
      if (!(field instanceof RexInputRef ref)) {
        throw new IllegalArgumentException("Field reference expected, got " + field);
      }
      projection.add(ref.getIndex());
    }

    push(new PhysicalTableScan(scan.getCluster(), scan.getTraitSet(), scan.getHints(),
        scan.getTable(), scan.getFilter(), ImmutableIntList.copyOf(projection)));
    return this;
  }

  /** Creates physical filter with specified condition on top of stack. */
  public PhysicalRelBuilder physicalFilter(RexNode condition) {
    RelNode input = build();
    push(new PhysicalFilter(getCluster(), physicalTraits(), List.of(), input, condition));
    return this;
  }

  /** Creates enumerable filter with specified condition on top of stack. */
  public PhysicalRelBuilder enumerableFilter(RexNode condition) {
    push(EnumerableFilter.create(build(), condition));
    return this;
  }

  /** Creates physical project with specified expressions on top of stack. */
  public PhysicalRelBuilder physicalProject(RexNode... nodes) {
    RelNode input = build();
    push(new PhysicalProject(getCluster(), physicalTraits(), List.of(), input, List.of(nodes),
        projectRowType(input, nodes)));
    return this;
  }

  /** Creates enumerable project with specified expressions on top of stack. */
  public PhysicalRelBuilder enumerableProject(RexNode... nodes) {
    RelNode input = build();
    push(EnumerableProject.create(input, List.of(nodes), projectRowType(input, nodes)));
    return this;
  }

  /** Creates converter from physical to enumerable convention on top of stack. */
  public PhysicalRelBuilder physicalToEnumerable() {
    RelNode input = build();
    push(new PhysicalToEnumerableConverter(getCluster(),
        input.getTraitSet().replace(EnumerableConvention.INSTANCE), input));
    return this;
  }

  /** Returns table with specified name, throws exception if table is not found. */
  private RelOptTable getTable(String... tableNames) {
    RelOptSchema schema = requireNonNull(relOptSchema, "relOptSchema");
    return requireNonNull(schema.getTableForMember(List.of(tableNames)),
        () -> "Table " + String.join(".", tableNames) + " not found");
  }

  /** Returns row type of project with specified expressions on top of specified input. */
  private RelDataType projectRowType(RelNode input, RexNode... nodes) {
    // using input field names for column references, like project does
    List<@Nullable String> fieldNames = new ArrayList<>();
    for (RexNode node : nodes) {
      fieldNames.add(node instanceof RexInputRef ref
          ? input.getRowType().getFieldNames().get(ref.getIndex())
          : null);
    }

    return RexUtil.createStructType(getCluster().getTypeFactory(), List.of(nodes), fieldNames,
        SqlValidatorUtil.F_SUGGESTER);
  }

  /** Returns trait set of physical nodes. */
  private RelTraitSet physicalTraits() {
    return getCluster().traitSetOf(PhysicalConvention.INSTANCE);
  }
}
