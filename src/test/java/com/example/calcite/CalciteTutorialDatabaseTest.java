package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelRoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/** Tests for CalciteTutorialDatabase. */
class CalciteTutorialDatabaseTest {
  /** URL of in-memory HSQLDB database */
  private static final String JDBC_URL = "jdbc:hsqldb:mem:tutorial";

  private CalciteTutorialDatabase database;

  /** Database that uses Calcite JDBC schema for HSQLDB database */
  private CalciteTutorialDatabase jdbcDatabase;

  /** Creates tables in HSQLDB database */
  @BeforeAll static void setUpJdbc() throws Exception {
    try (Connection connection = DriverManager.getConnection(JDBC_URL, "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table \"salaries\" (\"user_id\" integer not null, "
          + "\"amount\" integer not null, \"paid_date\" date not null)");
      statement.execute("create table \"projects\" (\"id\" integer not null, "
          + "\"dep_id\" integer not null, \"name\" varchar(100) not null, \"budget\" integer)");
      statement.execute("create table \"assignments\" (\"user_id\" integer not null, "
          + "\"project_id\" integer not null, \"role\" varchar(50) not null)");
      statement.execute("insert into \"salaries\" values "
          + "(1, 3000, date '2026-01-31'), (11, 2500, date '2026-01-31'), "
          + "(12, 2000, date '2026-01-31'), (1, 3000, date '2026-02-28')");
      statement.execute("insert into \"projects\" values "
          + "(1, 1, 'search', 100000), (2, 1, 'ads', 50000), (3, 2, 'crm', 20000)");
      statement.execute("insert into \"assignments\" values "
          + "(1, 1, 'lead'), (1, 3, 'dev'), (11, 2, 'dev'), (12, 3, 'lead')");
    }
  }

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();

    DataSource dataSource = JdbcSchema.dataSource(JDBC_URL, null, "SA", "");
    jdbcDatabase = new CalciteTutorialDatabase(
        JdbcSchema.create(CalciteSchema.createRootSchema(false, false).plus(),
            "tutorial", dataSource, null, "PUBLIC"));
  }

  /** Tests processing and executing query for JDBC database */
  @Test void testProcessAndExecuteJdbcQuery() throws Exception {
    RelRoot root =
        jdbcDatabase.processQueryEnumerable("select name from projects where budget > 30000");

    assertThat(RelOptUtil.toString(root.rel),
        is("JdbcToEnumerableConverter\n"
            + "  JdbcProject(NAME=[$2])\n"
            + "    JdbcFilter(condition=[>($3, 30000)])\n"
            + "      JdbcTableScan(table=[[tutorial, projects]])\n"));

    assertThat(jdbcDatabase.executeQuery(root).toList(), is(List.of("search", "ads")));
  }

  /** Tests processing and executing query with join and calc for JDBC database */
  @Test void testProcessAndExecuteJdbcQueryWithJoinAndCalc() throws Exception {
    RelRoot root = jdbcDatabase.processQueryEnumerable("select p.name, a.role, p.budget / 1000\n"
        + "from projects p join assignments a on a.project_id = p.id\n"
        + "where p.budget > 30000");

    assertThat(RelOptUtil.toString(root.rel),
        is("JdbcToEnumerableConverter\n"
            + "  JdbcProject(NAME=[$1], ROLE=[$4], EXPR$2=[/($2, 1000)])\n"
            + "    JdbcJoin(condition=[=($3, $0)], joinType=[inner])\n"
            + "      JdbcProject(id=[$0], name=[$2], budget=[$3])\n"
            + "        JdbcFilter(condition=[>($3, 30000)])\n"
            + "          JdbcTableScan(table=[[tutorial, projects]])\n"
            + "      JdbcProject(project_id=[$1], role=[$2])\n"
            + "        JdbcTableScan(table=[[tutorial, assignments]])\n"));

    List<List<Object>> rows = new ArrayList<>();
    for (Object row : jdbcDatabase.executeQuery(root)) {
      rows.add(Arrays.asList((Object[]) row));
    }
    assertThat(rows, containsInAnyOrder(List.of("search", "lead", 100), List.of("ads", "dev", 50)));
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
