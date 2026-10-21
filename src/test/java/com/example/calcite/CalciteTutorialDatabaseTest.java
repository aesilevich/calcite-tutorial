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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        is("PhysicalProject(NAME=[$1])\n"
            + "  PhysicalProject(id=[$0], name=[$1])\n"
            + "    PhysicalFilter(condition=[>($0, 10)])\n"
            + "      PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical table scan with builder */
  @Test void testBuildPhysicalTableScan() {
    RelNode rel = database.createRelBuilder()
        .physicalScan("tutorial", "users")
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical project with builder */
  @Test void testBuildPhysicalProject() {
    PhysicalRelBuilder builder = database.createRelBuilder();
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
    PhysicalRelBuilder builder = database.createRelBuilder();
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
    PhysicalRelBuilder builder = database.createRelBuilder();
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
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("LogicalFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests converting logical table scan to physical table scan */
  @Test void testPhysicalTableScan() {
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .build();
    RelNode expected = builder
        .physicalScan("tutorial", "users")
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting logical project to physical project */
  @Test void testPhysicalProject() {
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();
    RelNode expected = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"), builder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting logical filter to physical filter */
  @Test void testPhysicalFilter() {
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode logical = builder
        .scan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();
    RelNode expected = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(logical, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting logical project with physical table scan to physical nodes */
  @Test void testPhysicalProjectWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode mixed = builder
        .physicalScan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();
    RelNode expected = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"), builder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(mixed, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }

  /** Tests converting logical project and filter with physical table scan to physical nodes */
  @Test void testPhysicalProjectAndFilterWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createRelBuilder();
    RelNode mixed = builder
        .physicalScan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .project(builder.field("dep_id"), builder.field("name"))
        .build();
    RelNode expected = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .physicalProject(builder.field("dep_id"), builder.field("name"))
        .build();

    RelRoot root = database.optimizeQueryPhysical(RelRoot.of(mixed, SqlKind.SELECT));
    assertThat(RelOptUtil.toString(root.rel), is(RelOptUtil.toString(expected)));
  }
}
