package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.schema.SchemaPlus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.calcite.schema.JsonSchema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for executing queries through Calcite JDBC with custom optimization phases. */
class CalciteJdbcTest {
  private static final String URL = "jdbc:hsqldb:mem:calcite_jdbc_test";

  @BeforeAll static void setUpDatabase() throws Exception {
    try (Connection connection = DriverManager.getConnection(URL, "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table users (id int primary key, name varchar(20), dep_id int)");
      statement.execute("insert into users values (1, 'alice', 1), (2, 'bob', 1), "
          + "(11, 'carol', 2), (12, 'dave', 2)");
    }
  }

  /** Tests optimizing and executing query with filter and project on single table */
  @Test void testSelectWithFilter() throws Exception {
    String query = "select name from db.users where id > 10";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("JdbcToEnumerableConverter\n"
                + "  JdbcProject(NAME=[$1])\n"
                + "    JdbcFilter(condition=[>($0, 10)])\n"
                + "      JdbcTableScan(table=[[DB, USERS]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<String> names = new ArrayList<>();
        while (resultSet.next()) {
          names.add(resultSet.getString(1));
        }
        assertThat(names, is(List.of("carol", "dave")));
      }
    }
  }

  /** Tests optimizing and executing query with filter pushed below aggregate */
  @Test void testPushFilterBelowAggregate() throws Exception {
    String query = "select * from (\n"
        + "  select dep_id, count(*) as c from db.users group by dep_id\n"
        + ") where dep_id > 1";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("JdbcToEnumerableConverter\n"
                + "  JdbcAggregate(group=[{0}], C=[COUNT()])\n"
                + "    JdbcProject(DEP_ID=[$2])\n"
                + "      JdbcFilter(condition=[>($2, 1)])\n"
                + "        JdbcTableScan(table=[[DB, USERS]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          rows.add(List.of(resultSet.getInt(1), resultSet.getLong(2)));
        }
        assertThat(rows, is(List.of(List.of(2, 2L))));
      }
    }
  }

  /** Tests conversion of query on custom schema table to physical nodes */
  @Test void testConvertToPhysicalNodes() throws Exception {
    String query = "select \"name\" from \"tutorial\".\"users\" where \"id\" > 10";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
      resultSet.next();
      assertThat(resultSet.getString(1),
          is("PhysicalToEnumerableConverter\n"
              + "  PhysicalTableScan(table=[[tutorial, users]], filter=[>($0, 10)], "
              + "projection=[[1]])\n"));
    }
  }

  /** Tests conversion of project on top of join of HSQLDB and custom schema tables to calc */
  @Test void testConvertToCalc() throws Exception {
    String query = "select u.name, d.\"name\" from db.users u\n"
        + "join \"tutorial\".\"departments\" d on u.dep_id = d.\"id\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
      resultSet.next();
      assertThat(resultSet.getString(1),
          is("EnumerableCalc(expr#0..3=[{inputs}], NAME=[$t0], name=[$t3])\n"
              + "  EnumerableHashJoin(condition=[=($1, $2)], joinType=[inner])\n"
              + "    JdbcToEnumerableConverter\n"
              + "      JdbcProject(NAME=[$1], DEP_ID=[$2])\n"
              + "        JdbcTableScan(table=[[DB, USERS]])\n"
              + "    PhysicalToEnumerableConverter\n"
              + "      PhysicalTableScan(table=[[tutorial, departments]])\n"));
    }
  }

  /** Creates Calcite connection with tutorial optimization program, HSQLDB and custom schemas */
  private static Connection createConnection() throws Exception {
    Connection connection = new Driver().withPrepareFactory(CalciteTutorialPrepare::new)
        .connect("jdbc:calcite:", new Properties());
    SchemaPlus rootSchema = connection.unwrap(CalciteConnection.class).getRootSchema();
    rootSchema.add("DB", JdbcSchema.create(rootSchema, "DB",
        JdbcSchema.dataSource(URL, "org.hsqldb.jdbc.JDBCDriver", "SA", ""), null, "PUBLIC"));
    rootSchema.add("tutorial", JsonSchema.createDefault());
    return connection;
  }
}
