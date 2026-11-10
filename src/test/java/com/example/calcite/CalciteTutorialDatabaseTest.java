package com.example.calcite;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for CalciteTutorialDatabase. */
class CalciteTutorialDatabaseTest {
  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();
  }

  /** Tests processing simple query and converting to physical nodes */
  @Test void testProcessSimpleQuery() throws Exception {
    RelRoot root = database.processQueryPhysical("select name from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[1]])\n"));
  }

  /** Tests processing query with expression in select list and converting it to physical calc */
  @Test void testProcessQueryWithCalc() throws Exception {
    RelRoot root = database.processQueryPhysical("select id + 1 from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalCalc(expr#0=[{inputs}], expr#1=[1], expr#2=[+($t0, $t1)], EXPR$0=[$t2])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[0]])\n"));
  }

  /** Tests processing simple query and converting to enumerable nodes */
  @Test void testProcessSimpleQueryEnumerable() throws Exception {
    RelRoot root = database.processQueryEnumerable("select name from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[1]])\n"));
  }

  /**
   * Tests processing query with expression in select list and converting it to
   * enumerable calc
   */
  @Test void testProcessQueryWithCalcEnumerable() throws Exception {
    RelRoot root = database.processQueryEnumerable("select id + 1 from users where id > 10");

    assertThat(RelOptUtil.toString(root.rel),
        is("EnumerableCalc(expr#0=[{inputs}], expr#1=[1], expr#2=[+($t0, $t1)], EXPR$0=[$t2])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
            + "projection=[[0]])\n"));
  }
}
