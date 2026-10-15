package com.example.calcite;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
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

  /** Tests correct error reporting for unsupported ROLLUP operation */
  @Test void testRollup() {
    UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
        () -> database.processQuery("select dep_id from users group by rollup(dep_id)"));

    assertThat(e.getMessage(), is("ROLLUP is not supported in this engine"));
  }
}
