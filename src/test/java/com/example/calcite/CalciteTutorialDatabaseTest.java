package com.example.calcite;

import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.JoinRelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.metadata.TutorialRelMetadataQuery;

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
}
