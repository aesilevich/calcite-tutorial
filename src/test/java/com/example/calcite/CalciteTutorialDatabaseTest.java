package com.example.calcite;

import java.util.List;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.schema.Schema;
import com.example.calcite.schema.SchemaTable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

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
}
