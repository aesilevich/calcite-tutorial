package com.example.calcite;

import java.util.List;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.logical.RelBuilder;
import com.example.calcite.physical.PhysicalRelBuilder;
import com.example.calcite.schema.Schema;
import com.example.calcite.schema.SchemaTable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for CalciteTutorialDatabase. */
class CalciteTutorialDatabaseTest {
  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();
  }

  /** Tests processing simple SELECT query for users table */
  @Test void testSelectUsers() throws Exception {
    RelRoot root = database.processQueryLogical("select * from users");

    // checking that root node is LogicalProject
    assertThat(root.rel, instanceOf(LogicalProject.class));
    LogicalProject project = (LogicalProject) root.rel;

    // checking that input of project node is table scan
    assertThat(project.getInput(), instanceOf(LogicalTableScan.class));
    LogicalTableScan scan = (LogicalTableScan) project.getInput();

    // checking that target table is users
    RelOptTable table = scan.getTable();
    assertThat(table.unwrap(SchemaTable.class),
        sameInstance(((Schema) database.getSchema()).getSchemaTable("users")));

    // checking relation tree dump
    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(id=[$0], name=[$1], dep_id=[$2])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests removing of correlated subquery in WHERE */
  @Test void testFilterSubQuery() throws Exception {
    RelRoot root = database.processQueryLogical("select name from departments d\n"
        + "where exists (select 1 from users u where u.dep_id = d.id)");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of correlated subquery in SELECT */
  @Test void testProjectSubQuery() throws Exception {
    RelRoot root = database.processQueryLogical(
        "select d.name, (select count(*) from users u where u.dep_id = d.id)\n"
            + "from departments d");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of correlated subquery in JOIN condition */
  @Test void testJoinSubQuery() throws Exception {
    RelRoot root = database.processQueryLogical("select u.name from users u\n"
        + "join departments d on u.dep_id = d.id\n"
        + "and exists (select 1 from users u2 where u2.dep_id = d.id)");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of nested subqueries in Filter + Project */
  @Test void testNestedProjectSubQuery() throws Exception {
    RelRoot root = database.processQueryLogical("select d.name,\n"
        + "  (select count(*) from users u\n"
        + "   where u.dep_id = d.id\n"
        + "   and exists (select 1 from departments d2 where d2.id = u.dep_id))\n"
        + "from departments d");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of nested subqueries in Join + Filter */
  @Test void testNestedJoinSubQuery() throws Exception {
    RelRoot root = database.processQueryLogical("select u.name from users u\n"
        + "join departments d on u.dep_id = d.id\n"
        + "and exists (select 1 from users u2\n"
        + "  where u2.dep_id = d.id\n"
        + "  and u2.id in (select id from users u3 where u3.name = u2.name))");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests correct error reporting for unsupported ROLLUP operation */
  @Test void testRollup() {
    CalciteContextException e = assertThrows(CalciteContextException.class,
        () -> database.processQueryLogical("select dep_id from users group by rollup(dep_id)"));

    assertThat(e.getMessage(), is("From line 1, column 35 to line 1, column 48: "
        + "ROLLUP operation is not supported in this engine"));
  }

  /** Tests trimming of unused fields in query with join */
  @Test void testTrimUnusedFields() throws Exception {
    RelRoot root = database.processQueryLogical(
        "select u.name from users u join departments d on u.dep_id = d.id");

    // checking that root node is logical project
    assertThat(root.rel, instanceOf(LogicalProject.class));
    LogicalProject project = (LogicalProject) root.rel;

    // checking that input of project node is logical join
    assertThat(project.getInput(), instanceOf(LogicalJoin.class));
    LogicalJoin join = (LogicalJoin) project.getInput();

    // checking that first argument of join is logical project
    assertThat(join.getLeft(), instanceOf(LogicalProject.class));
    LogicalProject left = (LogicalProject) join.getLeft();

    // checking that left contains only name and dep_id fields
    assertThat(left.getRowType().getFieldNames(), is(List.of("name", "dep_id")));

    // checking that second argument of join is logical project
    assertThat(join.getRight(), instanceOf(LogicalProject.class));
    LogicalProject right = (LogicalProject) join.getRight();

    // checking that right project contains only id field
    assertThat(right.getRowType().getFieldNames(), is(List.of("id")));
  }

  /**
   * Tests processing COUNT(*) query for users table.
   * COUNT(*) may be broken if we don't use SqlStdOperatorTable.
   */
  @Test void testCountStar() throws Exception {
    RelRoot root = database.processQueryLogical("select count(*) from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalAggregate(group=[{}], EXPR$0=[COUNT()])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing of constant expression 1 + 2 */
  @Test void testReduceConstantExpression() throws Exception {
    RelRoot root = database.processQueryLogical("select id, 1 + 2 from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(ID=[$0], EXPR$1=[3])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing constant additions in nested queries */
  @Test void testReduceConstantExpressionInNestedQuery() throws Exception {
    RelRoot root = database.processQueryLogical("select a + (1 + 2) from (\n"
        + "  select id + (3 + 4) as a from users where id > 2 * 5\n"
        + ") as t");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(EXPR$0=[+(+($0, 7), 3)])\n"
            + "  LogicalProject(id=[$0])\n"
            + "    LogicalFilter(condition=[>($0, 10)])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing constant addition in join condition */
  @Test void testReduceConstantExpressionInJoin() throws Exception {
    RelRoot root = database.processQueryLogical("select u.name from users u\n"
        + "join departments d on u.dep_id = d.id and u.id > 2 + 5");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(NAME=[$1])\n"
            + "  LogicalJoin(condition=[=($2, $3)], joinType=[inner])\n"
            + "    LogicalFilter(condition=[>($0, 7)])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"
            + "    LogicalProject(id=[$0])\n"
            + "      LogicalTableScan(table=[[tutorial, departments]])\n"));
  }

  /** Tests reducing advanced ABS function without overflow for minimal SMALLINT value */
  @Test void testReduceAbs() throws Exception {
    RelRoot root =
        database.processQueryLogical("select abs(cast(-32768 as smallint)) from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(EXPR$0=[32768])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests pushing down of filter below aggregate */
  @Test void testFilterPushDown() throws Exception {
    RelRoot root = database.processQueryLogical("select t.dep_id from (\n"
        + "  select dep_id, avg(id) as avg_id from users group by dep_id having avg(id) > 10\n"
        + ") as t where t.dep_id > 5");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(DEP_ID=[$0])\n"
            + "  LogicalFilter(condition=[>($1, 10)])\n"
            + "    LogicalAggregate(group=[{0}], AVG_ID=[AVG($1)])\n"
            + "      LogicalProject(dep_id=[$2], id=[$0])\n"
            + "        LogicalFilter(condition=[>($2, 5)])\n"
            + "          LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests processing simple query and converting to physical nodes */
  @Test void testProcessSimpleQuery() throws Exception {
    RelRoot root = database.processQuery("select name from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[1]])\n"));
  }

  /** Tests processing query with expression in select list and converting it to physical calc */
  @Test void testProcessQueryWithCalc() throws Exception {
    RelRoot root = database.processQuery("select id + 1 from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0=[{inputs}], expr#1=[1], expr#2=[+($t0, $t1)], EXPR$0=[$t2])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[0]])\n"));
  }

  /** Tests pushing single filter into table scan */
  @Test void testPushFilterIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)])\n"));
  }

  /** Tests pushing filter into table scan that already has filter */
  @Test void testPushFilterIntoScanWithFilter() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
            .withFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .filter(builder.greaterThan(builder.field("dep_id"), builder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], "
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

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], "
            + "filter=[AND(>($2, 1), >($0, 10))])\n"));
  }

  /** Tests building physical table scan with builder */
  @Test void testBuildPhysicalTableScan() {
    RelNode rel = database.createPhysicalRelBuilder()
        .physicalScan("tutorial", "users")
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical project with builder */
  @Test void testBuildPhysicalProject() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical filter with builder */
  @Test void testBuildPhysicalFilter() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building logical project with physical table scan with builder */
  @Test void testBuildLogicalProjectWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("LogicalProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building logical filter with physical table scan with builder */
  @Test void testBuildLogicalFilterWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("LogicalFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
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
        .physicalProject(physicalBuilder.call(SqlStdOperatorTable.PLUS,
            physicalBuilder.field("id"), physicalBuilder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    assertThat(RelOptUtil.toString(original),
        is("LogicalFilter(condition=[>($1, 2)])\n"
            + "  LogicalFilter(condition=[>($0, 1)])\n"
            + "    LogicalProject(dep_id=[$2], id=[$0])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"));

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], filter=[AND(>($0, 2), >($2, 1))], "
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .physicalProject(
            physicalBuilder.call(SqlStdOperatorTable.PLUS, physicalBuilder.field("id"),
                physicalBuilder.literal(1)),
            physicalBuilder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests pushing single project into table scan */
  @Test void testPushProjectIntoScan() {
    RelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(rel, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], projection=[[2, 1]])\n"));
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
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .physicalProject(physicalBuilder.greaterThan(physicalBuilder.field("dep_id"),
            physicalBuilder.literal(1)))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .physicalProject(
            physicalBuilder.call(SqlStdOperatorTable.PLUS, physicalBuilder.field("id"),
                physicalBuilder.field("dep_id")),
            physicalBuilder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .physicalProject(physicalBuilder.literal(1))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
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
        .physicalProject(physicalBuilder.call(SqlStdOperatorTable.PLUS,
            physicalBuilder.field("id"), physicalBuilder.field("dep_id")))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(original, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
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
}
