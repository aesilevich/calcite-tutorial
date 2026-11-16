package com.example.calcite;

import java.util.List;

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
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.physical.PhysicalRelBuilder;
import com.example.calcite.schema.JsonSchema;
import com.example.calcite.sql.RexExecutor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for conversion of filters and projects to calc. */
class ConvertToCalcProgramTest {
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

  /** Tests converting physical filter to physical calc */
  @Test void testConvertPhysicalFilterToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "proj#0..2=[{exprs}], $condition=[$t4])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests converting physical project to physical calc */
  @Test void testConvertPhysicalProjectToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "dep_id=[$t2], $f1=[$t4])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two physical filters into single physical calc */
  @Test void testMergePhysicalFiltersToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)));
    RelNode rel = builder
        .physicalFilter(builder.lessThan(builder.field("dep_id"), builder.literal(5)))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "expr#5=[5], expr#6=[<($t2, $t5)], expr#7=[AND($t4, $t6)], "
            + "proj#0..2=[{exprs}], $condition=[$t7])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two physical projects into single physical calc */
  @Test void testMergePhysicalProjectsToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)));
    RelNode rel = builder
        .physicalProject(
            builder.call(SqlStdOperatorTable.PLUS, builder.field(1), builder.field("dep_id")))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[+($t4, $t2)], $f0=[$t5])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests that common sub-expressions of physical project are computed once in physical calc */
  @Test void testConvertPhysicalProjectWithCommonExpressionsToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("name"),
            builder.call(SqlStdOperatorTable.MULTIPLY,
                builder.call(SqlStdOperatorTable.PLUS,
                    builder.field("id"), builder.field("dep_id")),
                builder.literal(2)),
            builder.call(SqlStdOperatorTable.PLUS,
                builder.call(SqlStdOperatorTable.MULTIPLY,
                    builder.call(SqlStdOperatorTable.PLUS,
                        builder.field("id"), builder.field("dep_id")),
                    builder.literal(2)),
                builder.literal(1)))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[+($t0, $t2)], expr#4=[2], "
            + "expr#5=[*($t3, $t4)], expr#6=[1], expr#7=[+($t5, $t6)], "
            + "name=[$t1], $f1=[$t5], $f2=[$t7])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging physical project and physical filter into single physical calc */
  @Test void testMergePhysicalProjectAndFilterToCalc() {
    PhysicalRelBuilder builder = createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .physicalProject(builder.field("name"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelNode converted = convert(rel);
    assertThat(RelOptUtil.toString(converted),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[10], expr#6=[>($t0, $t5)], name=[$t1], $f1=[$t4], $condition=[$t6])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Creates physical relational builder with new cluster */
  private PhysicalRelBuilder createPhysicalRelBuilder() {
    return PhysicalRelBuilder.create(createCluster(), catalogReader);
  }

  /** Creates cluster with new Volcano planner */
  private RelOptCluster createCluster() {
    VolcanoPlanner planner = new VolcanoPlanner();
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    planner.setExecutor(new RexExecutor());
    return RelOptCluster.create(planner, new RexBuilder(typeFactory));
  }

  /** Runs calc conversion program for tree */
  private static RelNode convert(RelNode rel) {
    return ConvertToCalcProgram.INSTANCE.run(rel.getCluster().getPlanner(), rel,
        rel.getTraitSet(), List.of(), List.of());
  }
}
