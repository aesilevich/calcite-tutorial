package com.example.calcite.enumerable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import org.apache.calcite.schema.impl.AbstractSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.calcite.CalciteTutorialPrepare;
import com.example.calcite.schema.CsvDepartmentsTable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/** Tests for scan of CSV file tables. */
class EnumerableFileScanTest {
  private static final String URL = "jdbc:hsqldb:mem:enumerable_file_scan_test";

  @BeforeAll static void setUpDatabase() throws Exception {
    try (Connection connection = DriverManager.getConnection(URL, "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table users (id int primary key, name varchar(20), dep_id int)");
      statement.execute("insert into users values (1, 'alice', 1), (2, 'bob', 1), "
          + "(11, 'carol', 2), (12, 'dave', 2)");
    }
  }

  /** Tests executing query that reads all columns from CSV table */
  @Test void testReadCsvTable() throws Exception {
    String query = "select * from \"files\".\"departments\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("EnumerableCalc(expr#0..2=[{inputs}], proj#0..2=[{exprs}])\n"
                + "  EnumerableFileScan(table=[[files, departments]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          rows.add(List.of(resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3)));
        }
        assertThat(rows, is(List.of(
            List.of(1, "Sales", "Sells products to customers"),
            List.of(2, "Engineering", "Builds, tests and maintains products"),
            List.of(3, "Support", "Helps customers with problems"))));
      }
    }
  }

  /** Tests optimizing and executing query that reads two columns from CSV table */
  @Test void testReadTwoColumnsFromCsvTable() throws Exception {
    String query = "select \"id\", \"description\" from \"files\".\"departments\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("EnumerableFileScan(table=[[files, departments]], projection=[[0, 2]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          rows.add(List.of(resultSet.getInt(1), resultSet.getString(2)));
        }
        assertThat(rows, is(List.of(
            List.of(1, "Sells products to customers"),
            List.of(2, "Builds, tests and maintains products"),
            List.of(3, "Helps customers with problems"))));
      }
    }
  }

  /** Tests optimizing and executing query with filter on CSV table */
  @Test void testFilterCsvTable() throws Exception {
    String query = "select \"name\" from \"files\".\"departments\" where \"id\" > 1";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[>($t0, $t2)], "
                + "name=[$t1], $condition=[$t3])\n"
                + "  EnumerableFileScan(table=[[files, departments]], projection=[[0, 1]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<String> names = new ArrayList<>();
        while (resultSet.next()) {
          names.add(resultSet.getString(1));
        }
        assertThat(names, is(List.of("Engineering", "Support")));
      }
    }
  }

  /** Tests sorting single column read from CSV table */
  @Test void testSortSingleColumnFromCsvTable() throws Exception {
    String query = "select \"name\" from \"files\".\"departments\" order by \"name\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query)) {
      List<String> names = new ArrayList<>();
      while (resultSet.next()) {
        names.add(resultSet.getString(1));
      }
      assertThat(names, is(List.of("Engineering", "Sales", "Support")));
    }
  }

  /** Tests sorting single integer column read from CSV table in descending order */
  @Test void testSortSingleIntegerColumnDescendingFromCsvTable() throws Exception {
    String query = "select \"id\" from \"files\".\"departments\" order by \"id\" desc";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query)) {
      List<Integer> ids = new ArrayList<>();
      while (resultSet.next()) {
        ids.add(resultSet.getInt(1));
      }
      assertThat(ids, is(List.of(3, 2, 1)));
    }
  }

  /** Tests limit on single column read from CSV table */
  @Test void testLimitSingleColumnFromCsvTable() throws Exception {
    String query = "select \"name\" from \"files\".\"departments\" limit 2";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query)) {
      List<String> names = new ArrayList<>();
      while (resultSet.next()) {
        names.add(resultSet.getString(1));
      }
      assertThat(names, is(List.of("Sales", "Engineering")));
    }
  }

  /** Tests union of single columns read from CSV table */
  @Test void testUnionSingleColumnsFromCsvTable() throws Exception {
    String query = "select \"name\" from \"files\".\"departments\"\n"
        + "union all\n"
        + "select \"name\" from \"files\".\"departments\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query)) {
      List<String> names = new ArrayList<>();
      while (resultSet.next()) {
        names.add(resultSet.getString(1));
      }
      assertThat(names, containsInAnyOrder("Sales", "Engineering", "Support",
          "Sales", "Engineering", "Support"));
    }
  }

  /** Tests optimizing and executing join of HSQLDB table with CSV table */
  @Test void testJoinWithJdbcTable() throws Exception {
    String query = "select u.name, d.\"name\" from db.users u\n"
        + "join \"files\".\"departments\" d on u.dep_id = d.\"id\"";
    try (Connection connection = createConnection();
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery("explain plan for " + query)) {
        resultSet.next();
        assertThat(resultSet.getString(1),
            is("EnumerableCalc(expr#0..3=[{inputs}], NAME=[$t0], name=[$t3])\n"
                + "  EnumerableMergeJoin(condition=[=($1, $2)], joinType=[inner])\n"
                + "    JdbcToEnumerableConverter\n"
                + "      JdbcSort(sort0=[$1], dir0=[ASC])\n"
                + "        JdbcProject(NAME=[$1], DEP_ID=[$2])\n"
                + "          JdbcTableScan(table=[[DB, USERS]])\n"
                + "    EnumerableSort(sort0=[$0], dir0=[ASC])\n"
                + "      EnumerableFileScan(table=[[files, departments]], "
                + "projection=[[0, 1]])\n"));
      }

      try (ResultSet resultSet = statement.executeQuery(query)) {
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          rows.add(List.of(resultSet.getString(1), resultSet.getString(2)));
        }
        assertThat(rows, containsInAnyOrder(
            List.of("alice", "Sales"),
            List.of("bob", "Sales"),
            List.of("carol", "Engineering"),
            List.of("dave", "Engineering")));
      }
    }
  }

  /** Creates Calcite connection with tutorial optimization program, HSQLDB and CSV schemas */
  private static Connection createConnection() throws Exception {
    Connection connection = new Driver().withPrepareFactory(CalciteTutorialPrepare::new)
        .connect("jdbc:calcite:", new Properties());
    SchemaPlus rootSchema = connection.unwrap(CalciteConnection.class).getRootSchema();
    rootSchema.add("DB", JdbcSchema.create(rootSchema, "DB",
        JdbcSchema.dataSource(URL, "org.hsqldb.jdbc.JDBCDriver", "SA", ""), null, "PUBLIC"));
    SchemaPlus files = rootSchema.add("files", new AbstractSchema());
    files.add("departments", new CsvDepartmentsTable(() -> new InputStreamReader(
        EnumerableFileScanTest.class.getResourceAsStream("/departments.csv"),
        StandardCharsets.UTF_8)));
    return connection;
  }
}
