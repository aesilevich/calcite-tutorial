package com.example.calcite;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.physical.PhysicalRelBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for conversion of filters and projects to calc. */
class ConvertToCalcTest {
  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();
  }

  /** Tests converting physical filter to physical calc */
  @Test void testConvertPhysicalFilterToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "proj#0..2=[{exprs}], $condition=[$t4])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests converting physical project to physical calc */
  @Test void testConvertPhysicalProjectToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "dep_id=[$t2], $f1=[$t4])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two physical filters into single physical calc */
  @Test void testMergePhysicalFiltersToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)));
    RelNode rel = builder
        .physicalFilter(builder.lessThan(builder.field("dep_id"), builder.literal(5)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "expr#5=[5], expr#6=[<($t2, $t5)], expr#7=[AND($t4, $t6)], "
            + "proj#0..2=[{exprs}], $condition=[$t7])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two physical projects into single physical calc */
  @Test void testMergePhysicalProjectsToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)));
    RelNode rel = builder
        .physicalProject(
            builder.call(SqlStdOperatorTable.PLUS, builder.field(1), builder.field("dep_id")))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[+($t4, $t2)], $f0=[$t5])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests that common sub-expressions of physical project are computed once in physical calc */
  @Test void testConvertPhysicalProjectWithCommonExpressionsToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
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

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[+($t0, $t2)], expr#4=[2], "
            + "expr#5=[*($t3, $t4)], expr#6=[1], expr#7=[+($t5, $t6)], "
            + "name=[$t1], $f1=[$t5], $f2=[$t7])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging physical project and physical filter into single physical calc */
  @Test void testMergePhysicalProjectAndFilterToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .physicalProject(builder.field("name"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[10], expr#6=[>($t0, $t5)], name=[$t1], $f1=[$t4], $condition=[$t6])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests converting enumerable filter to enumerable calc */
  @Test void testConvertEnumerableFilterToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "proj#0..2=[{exprs}], $condition=[$t4])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests converting enumerable project to enumerable calc */
  @Test void testConvertEnumerableProjectToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "dep_id=[$t2], $f1=[$t4])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two enumerable filters into single enumerable calc */
  @Test void testMergeEnumerableFiltersToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .enumerableFilter(builder.lessThan(builder.field("dep_id"), builder.literal(5)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[10], expr#4=[>($t0, $t3)], "
            + "expr#5=[5], expr#6=[<($t2, $t5)], expr#7=[AND($t4, $t6)], "
            + "proj#0..2=[{exprs}], $condition=[$t7])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging two enumerable projects into single enumerable calc */
  @Test void testMergeEnumerableProjectsToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(builder.field("dep_id"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .enumerableProject(
            builder.call(SqlStdOperatorTable.PLUS, builder.field(1), builder.field("dep_id")))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[+($t4, $t2)], $f0=[$t5])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /**
   * Tests that common sub-expressions of enumerable project are computed once in
   * enumerable calc
   */
  @Test void testConvertEnumerableProjectWithCommonExpressionsToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(builder.field("name"),
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

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[+($t0, $t2)], expr#4=[2], "
            + "expr#5=[*($t3, $t4)], expr#6=[1], expr#7=[+($t5, $t6)], "
            + "name=[$t1], $f1=[$t5], $f2=[$t7])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests merging enumerable project and enumerable filter into single enumerable calc */
  @Test void testMergeEnumerableProjectAndFilterToCalc() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .enumerableProject(builder.field("name"),
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    RelRoot root = database.convertToCalc(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t0, $t3)], "
            + "expr#5=[10], expr#6=[>($t0, $t5)], name=[$t1], $f1=[$t4], $condition=[$t6])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
  }
}
