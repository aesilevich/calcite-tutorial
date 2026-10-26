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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    RelRoot root = database.processQuery("select * from users");

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
    RelRoot root = database.processQuery("select name from departments d\n"
        + "where exists (select 1 from users u where u.dep_id = d.id)");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of correlated subquery in SELECT */
  @Test void testProjectSubQuery() throws Exception {
    RelRoot root = database.processQuery(
        "select d.name, (select count(*) from users u where u.dep_id = d.id)\n"
            + "from departments d");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of correlated subquery in JOIN condition */
  @Test void testJoinSubQuery() throws Exception {
    RelRoot root = database.processQuery("select u.name from users u\n"
        + "join departments d on u.dep_id = d.id\n"
        + "and exists (select 1 from users u2 where u2.dep_id = d.id)");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of nested subqueries in Filter + Project */
  @Test void testNestedProjectSubQuery() throws Exception {
    RelRoot root = database.processQuery("select d.name,\n"
        + "  (select count(*) from users u\n"
        + "   where u.dep_id = d.id\n"
        + "   and exists (select 1 from departments d2 where d2.id = u.dep_id))\n"
        + "from departments d");

    assertThat(SubQueryChecker.containsSubQuery(root.rel), is(false));
    assertThat(CorrelateChecker.containsCorrelate(root.rel), is(false));
  }

  /** Tests removing of nested subqueries in Join + Filter */
  @Test void testNestedJoinSubQuery() throws Exception {
    RelRoot root = database.processQuery("select u.name from users u\n"
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
        () -> database.processQuery("select dep_id from users group by rollup(dep_id)"));

    assertThat(e.getMessage(), is("From line 1, column 35 to line 1, column 48: "
        + "ROLLUP operation is not supported in this engine"));
  }

  /** Tests trimming of unused fields in query with join */
  @Test void testTrimUnusedFields() throws Exception {
    RelRoot root = database.processQuery(
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
    RelRoot root = database.processQuery("select count(*) from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalAggregate(group=[{}], EXPR$0=[COUNT()])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing of constant expression 1 + 2 */
  @Test void testReduceConstantExpression() throws Exception {
    RelRoot root = database.processQuery("select id, 1 + 2 from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(ID=[$0], EXPR$1=[3])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing constant additions in nested queries */
  @Test void testReduceConstantExpressionInNestedQuery() throws Exception {
    RelRoot root = database.processQuery("select a + (1 + 2) from (\n"
        + "  select id + (3 + 4) as a from users where id > 2 * 5\n"
        + ") as t");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(EXPR$0=[+(+($0, 7), 3)])\n"
            + "  LogicalFilter(condition=[>($0, 10)])\n"
            + "    LogicalProject(id=[$0])\n"
            + "      LogicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests reducing constant addition in join condition */
  @Test void testReduceConstantExpressionInJoin() throws Exception {
    RelRoot root = database.processQuery("select u.name from users u\n"
        + "join departments d on u.dep_id = d.id and u.id > 2 + 5");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(NAME=[$1])\n"
            + "  LogicalJoin(condition=[AND(=($2, $3), >($0, 7))], joinType=[inner])\n"
            + "    LogicalTableScan(table=[[tutorial, users]])\n"
            + "    LogicalProject(id=[$0])\n"
            + "      LogicalTableScan(table=[[tutorial, departments]])\n"));
  }

  /** Tests reducing advanced ABS function without overflow for minimal SMALLINT value */
  @Test void testReduceAbs() throws Exception {
    RelRoot root = database.processQuery("select abs(cast(-32768 as smallint)) from users");

    assertThat(RelOptUtil.toString(root.rel),
        is("LogicalProject(EXPR$0=[32768])\n"
            + "  LogicalTableScan(table=[[tutorial, users]])\n"));
  }
}
