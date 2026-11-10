package com.example.calcite;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.logical.RelBuilder;
import com.example.calcite.physical.PhysicalRelBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for optimization of relational trees into enumerable nodes. */
class OptimizeEnumerableTest {
  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();
  }

  /** Tests pushing single filter into table scan */
  @Test void testPushFilterIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)])\n"));
  }

  /** Tests pushing filter into table scan that already has filter */
  @Test void testPushFilterIntoScanWithFilter() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
            .withFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], "
            + "filter=[AND(>($2, 1), >($0, 10))])\n"));
  }

  /** Tests pushing of multiple filters into table scan as single filter */
  @Test void testPushMultipleFiltersIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], "
            + "filter=[AND(>($2, 1), >($0, 10))])\n"));
  }

  /** Tests converting table scan */
  @Test void testTableScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting project + scan into scan with project */
  @Test void testConvertProjectAndScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("dep_id"), physicalBuilder.field("name"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting filter + scan to single scan with filter */
  @Test void testConvertFilterAndScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(physicalBuilder.field("id"),
                physicalBuilder.literal(10)))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting project with expression and scan */
  @Test void testProjectExpressionWithScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("id"))
        .physicalToEnumerable()
        .enumerableProject(physicalBuilder.call(SqlStdOperatorTable.PLUS,
            physicalBuilder.field("id"), physicalBuilder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing project that removes column into scan that already has projection */
  @Test void testPushProjectRemovingColumnIntoScanWithProjection() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
            .withProject(builder.field("dep_id"), builder.field("name"), builder.field("id"))
        .project(builder.field("name"), builder.field("id"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("name"), physicalBuilder.field("id"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /**
   * Tests pushing project that switches order of two fields into scan
   * that already has projection
   */
  @Test void testPushProjectSwitchingFieldsIntoScanWithProjection() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
            .withProject(builder.field("dep_id"), builder.field("name"))
        .project(builder.field("name"), builder.field("dep_id"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("name"), physicalBuilder.field("dep_id"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /**
   * Tests pushing filter + project into scan.
   */
  @Test void testPushFilterAndProjectIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(physicalBuilder.field("id"),
                physicalBuilder.literal(10)))
            .withProjection(physicalBuilder.field("dep_id"), physicalBuilder.field("name"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /**
   * Tests pushing filter into scan through project
   */
  @Test void testPushFilterIntoScanThroughProject() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"), builder.field("id"))
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(physicalBuilder.field("id"),
                physicalBuilder.literal(10)))
            .withProjection(physicalBuilder.field("dep_id"), physicalBuilder.field("name"),
                physicalBuilder.field("id"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing two filters + project into scan */
  @Test void testPushTwoFiltersAndProjectIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("id"))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .filter(builder.greaterThan(builder.field("id"), builder.literal(2)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.and(
                physicalBuilder.greaterThan(physicalBuilder.field("id"),
                    physicalBuilder.literal(2)),
                physicalBuilder.greaterThan(physicalBuilder.field("dep_id"),
                    physicalBuilder.literal(1))))
            .withProjection(physicalBuilder.field("dep_id"), physicalBuilder.field("id"))
        .physicalToEnumerable()
        .build();

    assertThat(RelOptUtil.toString(original),
        is("LogicalFilter(condition=[>($1, 2)])\n"
            + "  LogicalFilter(condition=[>($0, 1)])\n"
            + "    LogicalProject(dep_id=[$2], id=[$0])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"));

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], filter=[AND(>($0, 2), >($2, 1))], "
            + "projection=[[2, 0]])\n"));
  }

  /** Tests pushing filter condition into scan with projection */
  @Test void testPushFilterIntoScanWithProjection() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
            .withProject(builder.field("name"), builder.field("dep_id"))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(physicalBuilder.field("dep_id"),
                physicalBuilder.literal(1)))
            .withProjection(physicalBuilder.field("name"), physicalBuilder.field("dep_id"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /**
   * Tests pushing filter condition into scan through project with expression
   */
  @Test void testPushFilterIntoScanThroughProjectExpression() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.literal(1)),
            builder.field("name"))
        .filter(builder.greaterThan(builder.field(0), builder.literal(10)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(
                physicalBuilder.call(SqlStdOperatorTable.PLUS, physicalBuilder.field("id"),
                    physicalBuilder.literal(1)),
                physicalBuilder.literal(10)))
            .withProjection(physicalBuilder.field("id"), physicalBuilder.field("name"))
        .physicalToEnumerable()
        .enumerableProject(
            physicalBuilder.call(SqlStdOperatorTable.PLUS, physicalBuilder.field("id"),
                physicalBuilder.literal(1)),
            physicalBuilder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing filter into scan through two projects */
  @Test void testPushFilterIntoScanThroughTwoProjects() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"), builder.field("id"))
        .project(builder.field("id"), builder.field("name"))
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.greaterThan(physicalBuilder.field("id"),
                physicalBuilder.literal(10)))
            .withProjection(physicalBuilder.field("id"), physicalBuilder.field("name"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing single project into table scan */
  @Test void testPushProjectIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], projection=[[2, 1]])\n"));
  }

  /** Tests pushing of project into table scan that already has projection */
  @Test void testPushProjectIntoScanWithProjection() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
            .withProject(builder.field("dep_id"), builder.field("id"), builder.field("name"))
        .project(builder.field("name"), builder.field("dep_id"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("name"), physicalBuilder.field("dep_id"))
        .physicalToEnumerable()
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing three projects into scan */
  @Test void testPushThreeProjectsIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.field("name"), builder.field("dep_id"))
        .project(builder.field("dep_id"), builder.field("name"))
        .project(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .build();

    assertThat(RelOptUtil.toString(original),
        is("LogicalProject($f0=[>($0, 1)])\n"
            + "  LogicalProject(dep_id=[$1], name=[$0])\n"
            + "    LogicalProject(name=[$1], dep_id=[$2])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"));

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withProjection(physicalBuilder.field("dep_id"))
        .physicalToEnumerable()
        .enumerableProject(physicalBuilder.greaterThan(physicalBuilder.field("dep_id"),
            physicalBuilder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests that projection is not added to scan if project uses all columns */
  @Test void testSkipPushingProjectWithAllColumnsIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(
            builder.call(SqlStdOperatorTable.PLUS, builder.field("id"), builder.field("dep_id")),
            builder.field("name"))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(
            physicalBuilder.call(SqlStdOperatorTable.PLUS, physicalBuilder.field("id"),
                physicalBuilder.field("dep_id")),
            physicalBuilder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests that project without column references is not pushed into scan */
  @Test void testSkipPushingProjectWithoutColumnsIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.literal(1))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(physicalBuilder.literal(1))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing filter and two projects into scan */
  @Test void testPushFilterAndTwoProjectsIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode original = builder
        .scan("tutorial", "users")
        .project(builder.call(SqlStdOperatorTable.PLUS, builder.field("id"),
                builder.field("dep_id")),
            builder.field("name"))
        .filter(builder.equals(builder.field("name"), builder.literal("x")))
        .project(builder.field(0))
        .build();

    PhysicalRelBuilder physicalBuilder = database.createPhysicalRelBuilder();
    RelNode expected = physicalBuilder
        .physicalScan("tutorial", "users")
            .withFilter(physicalBuilder.equals(physicalBuilder.field("name"),
                physicalBuilder.literal("x")))
            .withProjection(physicalBuilder.field("id"), physicalBuilder.field("dep_id"))
        .physicalToEnumerable()
        .enumerableProject(physicalBuilder.call(SqlStdOperatorTable.PLUS,
            physicalBuilder.field("id"), physicalBuilder.field("dep_id")))
        .build();

    RelRoot root = database.optimizeQueryEnumerable(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }
}
