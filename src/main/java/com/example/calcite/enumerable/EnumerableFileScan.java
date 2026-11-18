package com.example.calcite.enumerable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
import org.apache.calcite.adapter.enumerable.PhysType;
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
import org.apache.calcite.linq4j.tree.Blocks;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.schema.CsvDepartmentsTable;

import static java.util.Objects.requireNonNull;

/** Enumerable node for scan of file table. */
public class EnumerableFileScan extends TableScan implements EnumerableRel {
  /** Indexes of file table columns returned by scan. */
  private final @Nullable ImmutableIntList projection;

  public EnumerableFileScan(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelHint> hints, RelOptTable table, @Nullable ImmutableIntList projection) {
    super(cluster, traitSet, hints, table);
    assert getConvention() == EnumerableConvention.INSTANCE;
    this.projection = projection;
  }

  /** Creates scan of file table with specified projection. */
  public static EnumerableFileScan create(RelOptCluster cluster, RelOptTable table,
      @Nullable ImmutableIntList projection) {
    return new EnumerableFileScan(cluster, cluster.traitSetOf(EnumerableConvention.INSTANCE),
        List.of(), table, projection);
  }

  /** Returns indexes of table columns returned by scan, or null if there is no projection. */
  public @Nullable ImmutableIntList getProjection() {
    return projection;
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new EnumerableFileScan(getCluster(), traitSet, getHints(), table, projection);
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

  /** Creates new scan that returns only used fields. */
  @Override public RelNode project(ImmutableBitSet fieldsUsed,
      Set<RelDataTypeField> extraFields, RelBuilder relBuilder) {
    if (!extraFields.isEmpty()) {
      return super.project(fieldsUsed, extraFields, relBuilder);
    }

    if (fieldsUsed.equals(ImmutableBitSet.range(getRowType().getFieldCount()))) {
      return this;
    }

    // mapping used fields to table columns
    List<Integer> columns = new ArrayList<>();
    for (int field : fieldsUsed) {
      columns.add(projection == null ? field : projection.get(field));
    }

    return new EnumerableFileScan(getCluster(), traitSet, getHints(), table,
        ImmutableIntList.copyOf(columns));
  }

  /** Calculates cost of scan taking into account number of columns in projection. */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // calculating base cost using default implementation
    RelOptCost cost = super.computeSelfCost(planner, mq);
    if (cost == null || projection == null) {
      return cost;
    }

    // adjusting cost proportionally to number of projection columns
    int columnCount = table.getRowType().getFieldCount();
    return cost.multiplyBy(((double) Math.max(projection.size(), 1)) / columnCount);
  }

  /** Generates code that reads rows from CSV table. */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
    PhysType physType = PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(),
        JavaRowFormat.ARRAY);

    int[] columns = projection == null
        ? ImmutableIntList.identity(table.getRowType().getFieldCount()).toIntArray()
        : projection.toIntArray();

    CsvDepartmentsTable csvTable = requireNonNull(table.unwrap(CsvDepartmentsTable.class));
    Expression tableExpression = implementor.stash(csvTable, CsvDepartmentsTable.class);
    return implementor.result(physType, Blocks.toBlock(
        Expressions.call(tableExpression, "scan", Expressions.constant(columns))));
  }

  @Override public RelWriter explainTerms(RelWriter pw) {
    return super.explainTerms(pw)
        .itemIf("projection", projection, projection != null);
  }
}
