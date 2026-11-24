package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableBitSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.logical.RelBuilder;
import com.example.calcite.metadata.TutorialRelMetadataQuery;
import com.example.calcite.physical.PhysicalRelBuilder;
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
    RelBuilder builder = database.createRelBuilder();
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

    RelBuilder builder = db.createRelBuilder();
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

  /** Tests column uniqueness for logical scan with projection */
  @Test void testColumnUniquenessForLogicalScanWithProjection() throws Exception {
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(JsonSchema.parse("""
        {"tables": [{"name": "users", "keys": [["id"]], "columns": [
          {"name": "id", "type": "integer", "nullable": false},
          {"name": "name", "type": "varchar", "nullable": false},
          {"name": "dep_id", "type": "integer", "nullable": false}
        ]}]}
        """));

    RelBuilder builder = db.createRelBuilder();
    RelNode depIdScan = builder
        .scan("tutorial", "users")
        .withProject(builder.field("dep_id"))
        .build();
    RelNode depIdAndIdScan = builder
        .scan("tutorial", "users")
        .withProject(builder.field("dep_id"), builder.field("id"))
        .build();

    RelMetadataQuery mq = depIdScan.getCluster().getMetadataQuery();
    assertThat(mq.areColumnsUnique(depIdScan, ImmutableBitSet.of(0)), is(false));
    assertThat(mq.areColumnsUnique(depIdAndIdScan, ImmutableBitSet.of(0)), is(false));
    assertThat(mq.areColumnsUnique(depIdAndIdScan, ImmutableBitSet.of(1)), is(true));
  }

  /** Tests column uniqueness for physical scan with projection */
  @Test void testColumnUniquenessForPhysicalScanWithProjection() throws Exception {
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(JsonSchema.parse("""
        {"tables": [{"name": "users", "keys": [["id"]], "columns": [
          {"name": "id", "type": "integer", "nullable": false},
          {"name": "name", "type": "varchar", "nullable": false},
          {"name": "dep_id", "type": "integer", "nullable": false}
        ]}]}
        """));

    PhysicalRelBuilder builder = db.createPhysicalRelBuilder();
    RelNode depIdScan = builder
        .physicalScan("tutorial", "users")
        .withProjection(builder.field("dep_id"))
        .build();
    RelNode depIdAndIdScan = builder
        .physicalScan("tutorial", "users")
        .withProjection(builder.field("dep_id"), builder.field("id"))
        .build();

    RelMetadataQuery mq = depIdScan.getCluster().getMetadataQuery();
    assertThat(mq.areColumnsUnique(depIdScan, ImmutableBitSet.of(0)), is(false));
    assertThat(mq.areColumnsUnique(depIdAndIdScan, ImmutableBitSet.of(0)), is(false));
    assertThat(mq.areColumnsUnique(depIdAndIdScan, ImmutableBitSet.of(1)), is(true));
  }

  /** Tests that unique keys of table from custom JDBC schema are propagated to metadata */
  @Test void testJdbcUniqueKeysPropagatedToMetadata() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:unique", "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table \"t\" (\"a\" integer not null, \"b\" integer not null, "
          + "\"c\" integer not null, primary key (\"a\"), unique (\"b\", \"c\"))");

      CalciteTutorialDatabase db = new CalciteTutorialDatabase(
          com.example.calcite.schema.JdbcSchema.readCatalog(connection).getSchema("PUBLIC"));

      RelBuilder builder = db.createRelBuilder();
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

  /** Builds join of two tables from schema for testing row count estimation */
  private static Join buildJoin(JoinRelType joinType, String left, String right,
      Function<RelBuilder, RexNode> condition) throws Exception {
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(JsonSchema.parse("""
        {"tables": [
          {"name": "big", "rowCount": 1000, "keys": [["id"]], "columns": [
            {"name": "id", "type": "integer", "nullable": false},
            {"name": "small_id", "type": "integer", "nullable": false},
            {"name": "x", "type": "integer", "nullable": false},
            {"name": "y", "type": "integer", "nullable": false}
          ]},
          {"name": "small", "rowCount": 100, "keys": [["id"], ["x", "y"]], "columns": [
            {"name": "id", "type": "integer", "nullable": false},
            {"name": "x", "type": "integer", "nullable": false},
            {"name": "y", "type": "integer", "nullable": false}
          ]},
          {"name": "other", "rowCount": 100, "columns": [
            {"name": "id", "type": "integer", "nullable": false}
          ]}
        ]}
        """));

    RelBuilder builder = db.createRelBuilder();
    return (Join) builder
        .scan("tutorial", left)
        .scan("tutorial", right)
        .join(joinType, condition.apply(builder))
        .build();
  }

  /** Returns estimated row count of node */
  private static @Nullable Double getRowCount(RelNode rel) {
    return rel.getCluster().getMetadataQuery().getRowCount(rel);
  }

  /**
   * Returns row count of join estimated by default Calcite implementation
   */
  private static @Nullable Double getDefaultRowCount(Join join) {
    return RelMdUtil.validateResult(RelMdUtil.getJoinRowCount(
        join.getCluster().getMetadataQuery(), join, join.getCondition()));
  }

  /** Tests row count estimation for inner join with unique keys of right input */
  @Test void testInnerJoinRowCountWithUniqueRightKeys() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "small", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(1000.0));
  }

  /** Tests row count estimation for inner join with unique keys of left input */
  @Test void testInnerJoinRowCountWithUniqueLeftKeys() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "small", "big", b ->
        b.equals(b.field(2, 0, "id"), b.field(2, 1, "small_id")));

    assertThat(getRowCount(join), is(1000.0));
  }

  /** Tests row count estimation for inner join with unique keys of both inputs */
  @Test void testInnerJoinRowCountWithUniqueKeysOfBothInputs() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "small", b ->
        b.equals(b.field(2, 0, "id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(100.0));
  }

  /** Tests that row count estimation for inner join without unique keys is default */
  @Test void testInnerJoinRowCountWithoutUniqueKeys() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "other", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }

  /** Tests row count estimation for inner join with unique composite key of right input */
  @Test void testInnerJoinRowCountWithUniqueCompositeKey() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "small", b ->
        b.and(b.equals(b.field(2, 0, "x"), b.field(2, 1, "x")),
            b.equals(b.field(2, 0, "y"), b.field(2, 1, "y"))));

    assertThat(getRowCount(join), is(1000.0));
  }

  /** Tests that row count estimation for inner join on part of composite key is default */
  @Test void testInnerJoinRowCountWithPartOfCompositeKey() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "small", b ->
        b.equals(b.field(2, 0, "x"), b.field(2, 1, "x")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }

  /**
   * Tests row count estimation for inner join with unique keys of right input and
   * additional non-equi condition with default selectivity 0.5
   */
  @Test void testInnerJoinRowCountWithUniqueKeysAndNonEquiCondition() throws Exception {
    Join join = buildJoin(JoinRelType.INNER, "big", "small", b ->
        b.and(b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")),
            b.greaterThan(b.field(2, 0, "x"), b.field(2, 1, "x"))));

    assertThat(getRowCount(join), is(500.0));
  }

  /** Tests row count estimation for left join with unique keys of right input */
  @Test void testLeftJoinRowCountWithUniqueRightKeys() throws Exception {
    Join join = buildJoin(JoinRelType.LEFT, "big", "small", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(1000.0));
  }

  /** Tests that row count estimation for left join with unique keys of left input is default */
  @Test void testLeftJoinRowCountWithUniqueLeftKeys() throws Exception {
    Join join = buildJoin(JoinRelType.LEFT, "small", "big", b ->
        b.equals(b.field(2, 0, "id"), b.field(2, 1, "small_id")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }

  /** Tests row count estimation for right join with unique keys of left input */
  @Test void testRightJoinRowCountWithUniqueLeftKeys() throws Exception {
    Join join = buildJoin(JoinRelType.RIGHT, "small", "big", b ->
        b.equals(b.field(2, 0, "id"), b.field(2, 1, "small_id")));

    assertThat(getRowCount(join), is(1000.0));
  }

  /** Tests that row count estimation for full join with unique keys is default */
  @Test void testFullJoinRowCountWithUniqueKeys() throws Exception {
    Join join = buildJoin(JoinRelType.FULL, "big", "small", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }

  /** Tests that row count estimation for semi join with unique keys is default */
  @Test void testSemiJoinRowCountWithUniqueKeys() throws Exception {
    Join join = buildJoin(JoinRelType.SEMI, "big", "small", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }

  /** Tests that row count estimation for anti join with unique keys is default */
  @Test void testAntiJoinRowCountWithUniqueKeys() throws Exception {
    Join join = buildJoin(JoinRelType.ANTI, "big", "small", b ->
        b.equals(b.field(2, 0, "small_id"), b.field(2, 1, "id")));

    assertThat(getRowCount(join), is(getDefaultRowCount(join)));
  }
}
