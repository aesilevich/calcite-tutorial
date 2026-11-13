package com.example.calcite;

import java.util.List;
import java.util.Set;

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.Holder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.logical.RelBuilder;
import com.example.calcite.schema.JsonSchema;
import com.example.calcite.sql.OperatorTable;
import com.example.calcite.sql.RexExecutor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for optimization of logical relational trees. */
class LogicalOptimizeProgramTest {
  private RelDataTypeFactory typeFactory;
  private CalciteCatalogReader catalogReader;

  @BeforeEach void setUp() throws Exception {
    typeFactory = new JavaTypeFactoryImpl();
    CalciteSchema rootSchema = CalciteSchema.createRootSchema(false, false);
    rootSchema.add("tutorial", JsonSchema.createDefault());
    CalciteConnectionConfig config =
        CalciteConnectionConfig.DEFAULT.set(CalciteConnectionProperty.CASE_SENSITIVE, "false");
    catalogReader =
        new CalciteCatalogReader(rootSchema, List.of("tutorial"), typeFactory, config);
  }

  /** Tests removing of correlated subquery in WHERE */
  @Test void testFilterSubQuery() {
    RelBuilder builder = createRelBuilder();
    Holder<RexCorrelVariable> d = Holder.empty();
    RelNode rel = builder
        .scan("tutorial", "departments")
        .variable(d::set)
        .filter(List.of(d.get().id), builder.exists(b -> b
            .scan("tutorial", "users")
            .filter(b.equals(b.field("dep_id"), b.field(d.get(), "id")))
            .build()))
        .project(builder.field("name"))
        .build();

    RelNode optimized = optimize(rel);
    assertThat(SubQueryChecker.containsSubQuery(optimized), is(false));
    assertThat(CorrelateChecker.containsCorrelate(optimized), is(false));
  }

  /** Tests removing of correlated subquery in SELECT */
  @Test void testProjectSubQuery() {
    RelBuilder builder = createRelBuilder();
    Holder<RexCorrelVariable> d = Holder.empty();
    RelNode rel = builder
        .scan("tutorial", "departments")
        .variable(d::set)
        .project(
            List.of(builder.field("name"), builder.scalarQuery(b -> b
                .scan("tutorial", "users")
                .filter(b.equals(b.field("dep_id"), b.field(d.get(), "id")))
                .aggregate(b.groupKey(), b.countStar(null))
                .build())),
            List.of(), false, List.of(d.get().id))
        .build();

    RelNode optimized = optimize(rel);
    assertThat(SubQueryChecker.containsSubQuery(optimized), is(false));
    assertThat(CorrelateChecker.containsCorrelate(optimized), is(false));
  }

  /** Tests removing of correlated subquery in JOIN condition */
  @Test void testJoinSubQuery() {
    RelBuilder builder = createRelBuilder();
    builder
        .scan("tutorial", "users")
        .project(builder.field("name"), builder.field("dep_id"))
        .scan("tutorial", "departments")
        .project(builder.field("id"));
    CorrelationId correlationId = builder.getCluster().createCorrel();
    RelDataType joinRowType = typeFactory.builder()
        .addAll(builder.peek(1).getRowType().getFieldList())
        .addAll(builder.peek(0).getRowType().getFieldList())
        .build();
    RexNode joinRow = builder.getRexBuilder().makeCorrel(joinRowType, correlationId);
    RelNode rel = builder
        .join(JoinRelType.INNER,
            builder.and(
                builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")),
                builder.exists(b -> b
                    .scan("tutorial", "users")
                    .filter(b.equals(b.field("dep_id"), b.field(joinRow, "id")))
                    .build())),
            Set.of(correlationId))
        .project(builder.field("name"))
        .build();

    RelNode optimized = optimize(rel);
    assertThat(SubQueryChecker.containsSubQuery(optimized), is(false));
    assertThat(CorrelateChecker.containsCorrelate(optimized), is(false));
  }

  /** Tests removing of nested subqueries in Filter + Project */
  @Test void testNestedProjectSubQuery() {
    RelBuilder builder = createRelBuilder();
    Holder<RexCorrelVariable> d = Holder.empty();
    Holder<RexCorrelVariable> u = Holder.empty();
    RelNode rel = builder
        .scan("tutorial", "departments")
        .variable(d::set)
        .project(
            List.of(builder.field("name"), builder.scalarQuery(b -> b
                .scan("tutorial", "users")
                .variable(u::set)
                .filter(List.of(u.get().id), b.and(
                    b.equals(b.field("dep_id"), b.field(d.get(), "id")),
                    b.exists(b2 -> b2
                        .scan("tutorial", "departments")
                        .filter(b2.equals(b2.field("id"), b2.field(u.get(), "dep_id")))
                        .build())))
                .aggregate(b.groupKey(), b.countStar(null))
                .build())),
            List.of(), false, List.of(d.get().id))
        .build();

    RelNode optimized = optimize(rel);
    assertThat(SubQueryChecker.containsSubQuery(optimized), is(false));
    assertThat(CorrelateChecker.containsCorrelate(optimized), is(false));
  }

  /** Tests removing of nested subqueries in Join + Filter */
  @Test void testNestedJoinSubQuery() {
    RelBuilder builder = createRelBuilder();
    builder
        .scan("tutorial", "users")
        .project(builder.field("name"), builder.field("dep_id"))
        .scan("tutorial", "departments")
        .project(builder.field("id"));
    CorrelationId correlationId = builder.getCluster().createCorrel();
    RelDataType joinRowType = typeFactory.builder()
        .addAll(builder.peek(1).getRowType().getFieldList())
        .addAll(builder.peek(0).getRowType().getFieldList())
        .build();
    RexNode joinRow = builder.getRexBuilder().makeCorrel(joinRowType, correlationId);
    Holder<RexCorrelVariable> u2 = Holder.empty();
    RelNode rel = builder
        .join(JoinRelType.INNER,
            builder.and(
                builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")),
                builder.exists(b -> b
                    .scan("tutorial", "users")
                    .variable(u2::set)
                    .filter(List.of(u2.get().id), b.and(
                        b.equals(b.field("dep_id"), b.field(joinRow, "id")),
                        b.in(b.field("id"), b2 -> b2
                            .scan("tutorial", "users")
                            .filter(b2.equals(b2.field("name"), b2.field(u2.get(), "name")))
                            .project(b2.field("id"))
                            .build())))
                    .build())),
            Set.of(correlationId))
        .project(builder.field("name"))
        .build();

    RelNode optimized = optimize(rel);
    assertThat(SubQueryChecker.containsSubQuery(optimized), is(false));
    assertThat(CorrelateChecker.containsCorrelate(optimized), is(false));
  }

  /** Tests reducing of constant expression 1 + 2 */
  @Test void testReduceConstantExpression() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .project(builder.field("id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.literal(1), builder.literal(2)))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .project(expectedBuilder.field("id"), expectedBuilder.literal(3))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests reducing constant additions in nested queries */
  @Test void testReduceConstantExpressionInNestedQuery() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"),
            builder.call(SqlStdOperatorTable.MULTIPLY, builder.literal(2), builder.literal(5))))
        .project(builder.alias(
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"),
                builder.call(SqlStdOperatorTable.PLUS, builder.literal(3), builder.literal(4))),
            "a"))
        .project(builder.call(SqlStdOperatorTable.PLUS, builder.field("a"),
            builder.call(SqlStdOperatorTable.PLUS, builder.literal(1), builder.literal(2))))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .filter(expectedBuilder.greaterThan(expectedBuilder.field("id"),
            expectedBuilder.literal(10)))
        .project(expectedBuilder.field("id"))
        .project(expectedBuilder.call(SqlStdOperatorTable.PLUS,
            expectedBuilder.call(SqlStdOperatorTable.PLUS, expectedBuilder.field("id"),
                expectedBuilder.literal(7)),
            expectedBuilder.literal(3)))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests reducing constant addition in join condition */
  @Test void testReduceConstantExpressionInJoin() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .scan("tutorial", "departments")
        .join(JoinRelType.INNER, builder.and(
            builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")),
            builder.greaterThan(builder.field(2, 0, "id"),
                builder.call(SqlStdOperatorTable.PLUS, builder.literal(2), builder.literal(5)))))
        .project(builder.field(1))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .filter(expectedBuilder.greaterThan(expectedBuilder.field("id"),
            expectedBuilder.literal(7)))
        .scan("tutorial", "departments")
        .project(expectedBuilder.field("id"))
        .join(JoinRelType.INNER, expectedBuilder.equals(
            expectedBuilder.field(2, 0, "dep_id"), expectedBuilder.field(2, 1, "id")))
        .project(expectedBuilder.field(1))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests reducing advanced ABS function without overflow for minimal SMALLINT value */
  @Test void testReduceAbs() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .project(builder.call(OperatorTable.ABS,
            builder.cast(builder.literal(-32768), SqlTypeName.SMALLINT)))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .project(expectedBuilder.literal(32768))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing down of filter below aggregate */
  @Test void testFilterPushDown() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .aggregate(builder.groupKey("dep_id"), builder.avg(builder.field("id")).as("avg_id"))
        .filter(builder.greaterThan(builder.field("avg_id"), builder.literal(10)))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(5)))
        .project(builder.field("dep_id"))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .filter(expectedBuilder.greaterThan(expectedBuilder.field("dep_id"),
            expectedBuilder.literal(5)))
        .project(expectedBuilder.field("id"), expectedBuilder.field("dep_id"))
        .aggregate(expectedBuilder.groupKey("dep_id"),
            expectedBuilder.avg(expectedBuilder.field("id")).as("avg_id"))
        .filter(expectedBuilder.greaterThan(expectedBuilder.field("avg_id"),
            expectedBuilder.literal(10)))
        .project(expectedBuilder.field("dep_id"))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests trimming of unused fields in query with join */
  @Test void testTrimUnusedFields() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .scan("tutorial", "departments")
        .join(JoinRelType.INNER,
            builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")))
        .project(builder.field(1))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .project(expectedBuilder.field("name"), expectedBuilder.field("dep_id"))
        .scan("tutorial", "departments")
        .project(expectedBuilder.field("id"))
        .join(JoinRelType.INNER, expectedBuilder.equals(
            expectedBuilder.field(2, 0, "dep_id"), expectedBuilder.field(2, 1, "id")))
        .project(expectedBuilder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Tests trimming of unused fields in query with two joins */
  @Test void testTrimUnusedFieldsWithTwoJoins() {
    RelBuilder builder = createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .scan("tutorial", "departments")
        .join(JoinRelType.INNER,
            builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")))
        .scan("tutorial", "departments")
        .join(JoinRelType.INNER,
            builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")))
        .project(builder.field(1))
        .build();

    RelBuilder expectedBuilder = createRelBuilder();
    RelNode expected = expectedBuilder
        .scan("tutorial", "users")
        .project(expectedBuilder.field("name"), expectedBuilder.field("dep_id"))
        .scan("tutorial", "departments")
        .project(expectedBuilder.field("id"))
        .join(JoinRelType.INNER, expectedBuilder.equals(
            expectedBuilder.field(2, 0, "dep_id"), expectedBuilder.field(2, 1, "id")))
        .scan("tutorial", "departments")
        .project(expectedBuilder.field("id"))
        .join(JoinRelType.INNER, expectedBuilder.equals(
            expectedBuilder.field(2, 0, "dep_id"), expectedBuilder.field(2, 1, "id")))
        .project(expectedBuilder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(optimize(rel)), is(RelOptUtil.toString(expected)));
  }

  /** Creates relational builder with new cluster */
  private RelBuilder createRelBuilder() {
    return RelBuilder.create(createCluster(), catalogReader);
  }

  /** Creates cluster with new Volcano planner */
  private RelOptCluster createCluster() {
    VolcanoPlanner planner = new VolcanoPlanner();
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    planner.setExecutor(new RexExecutor());
    return RelOptCluster.create(planner, new RexBuilder(typeFactory));
  }

  /** Runs logical optimization program for tree */
  private static RelNode optimize(RelNode rel) {
    return LogicalOptimizeProgram.INSTANCE.run(rel.getCluster().getPlanner(), rel,
        rel.getTraitSet(), List.of(), List.of());
  }
}
