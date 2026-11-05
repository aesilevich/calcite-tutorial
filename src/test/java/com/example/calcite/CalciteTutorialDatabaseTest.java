package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.util.ImmutableBitSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.metadata.TutorialRelMetadataQuery;
import com.example.calcite.schema.JsonSchema;

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

  /** Tests used tables metadata */
  @Test void testUsedTables() {
    com.example.calcite.logical.RelBuilder builder = database.createRelBuilder();
    RelNode usersScan = builder.scan("tutorial", "users").peek();
    RelNode filter = builder
        .filter(builder.equals(builder.field("dep_id"), builder.literal(1)))
        .peek();
    RelNode departmentsScan = builder.scan("tutorial", "departments").peek();
    RelNode join = builder
        .join(JoinRelType.INNER,
            builder.equals(builder.field(2, 0, "dep_id"), builder.field(2, 1, "id")))
        .peek();
    RelNode project = builder
        .project(builder.field(1), builder.field(4))
        .build();

    // checking used tables for each node
    TutorialRelMetadataQuery mq =
        (TutorialRelMetadataQuery) project.getCluster().getMetadataQuery();
    List<String> users = List.of("tutorial", "users");
    List<String> departments = List.of("tutorial", "departments");
    assertThat(mq.getUsedTables(usersScan), is(Set.of(users)));
    assertThat(mq.getUsedTables(filter), is(Set.of(users)));
    assertThat(mq.getUsedTables(departmentsScan), is(Set.of(departments)));
    assertThat(mq.getUsedTables(join), is(Set.of(users, departments)));
    assertThat(mq.getUsedTables(project), is(Set.of(users, departments)));
  }

  /** Tests used tables metadata for filter with sub-query */
  @Test void testUsedTablesWithSubQuery() {
    com.example.calcite.logical.RelBuilder builder = database.createRelBuilder();
    RelNode filter = builder
        .scan("tutorial", "departments")
        .filter(builder.exists(b -> b
            .scan("tutorial", "users")
            .build()))
        .build();

    TutorialRelMetadataQuery mq =
        (TutorialRelMetadataQuery) filter.getCluster().getMetadataQuery();
    List<String> users = List.of("tutorial", "users");
    List<String> departments = List.of("tutorial", "departments");
    assertThat(mq.getUsedTables(filter), is(Set.of(departments, users)));
  }

  /** Tests that unique keys of table from JSON schema are propagated to metadata */
  @Test void testUniqueKeysPropagatedToMetadata() throws Exception {
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(JsonSchema.parse("""
        {"tables": [{"name": "t", "keys": [["a"], ["b", "c"]], "columns": [
          {"name": "a", "type": "integer", "nullable": false},
          {"name": "b", "type": "integer", "nullable": false},
          {"name": "c", "type": "integer", "nullable": false}
        ]}]}
        """));

    com.example.calcite.logical.RelBuilder builder = db.createRelBuilder();
    RelNode scan = builder.scan("tutorial", "t").peek();
    RelNode project = builder
        .project(builder.field("c"), builder.field("a"))
        .build();

    RelMetadataQuery mq = scan.getCluster().getMetadataQuery();
    assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(0)), is(true));
    assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(0, 1)), is(true));
    assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(1)), is(false));
    assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(1, 2)), is(true));

    assertThat(mq.areColumnsUnique(project, ImmutableBitSet.of(1)), is(true));
    assertThat(mq.areColumnsUnique(project, ImmutableBitSet.of(0)), is(false));
  }

  /** Tests that unique keys of table from custom JDBC schema are propagated to metadata */
  @Test void testJdbcUniqueKeysPropagatedToMetadata() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:unique", "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table \"t\" (\"a\" integer not null, \"b\" integer not null, "
          + "\"c\" integer not null, primary key (\"a\"), unique (\"b\", \"c\"))");

      CalciteTutorialDatabase db = new CalciteTutorialDatabase(
          com.example.calcite.schema.JdbcSchema.readCatalog(connection).getSchema("PUBLIC"));

      com.example.calcite.logical.RelBuilder builder = db.createRelBuilder();
      RelNode scan = builder.scan("tutorial", "t").peek();
      RelNode project = builder
          .project(builder.field("c"), builder.field("a"))
          .build();

      RelMetadataQuery mq = scan.getCluster().getMetadataQuery();
      assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(0)), is(true));
      assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(0, 1)), is(true));
      assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(1)), is(false));
      assertThat(mq.areColumnsUnique(scan, ImmutableBitSet.of(1, 2)), is(true));

      assertThat(mq.areColumnsUnique(project, ImmutableBitSet.of(1)), is(true));
      assertThat(mq.areColumnsUnique(project, ImmutableBitSet.of(0)), is(false));
    }
  }
}
