package com.example.calcite.physical;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.CalciteTutorialDatabase;
import com.example.calcite.distribution.Distribution;
import com.example.calcite.schema.JsonSchema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for PhysicalRelBuilder. */
class PhysicalRelBuilderTest {
  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase();
  }

  /** Tests building physical table scan with builder */
  @Test void testBuildPhysicalTableScan() {
    RelNode rel = database.createPhysicalRelBuilder()
        .physicalScan("tutorial", "users")
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical project with builder */
  @Test void testBuildPhysicalProject() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalProject(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical filter with builder */
  @Test void testBuildPhysicalFilter() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building physical exchange with builder */
  @Test void testBuildPhysicalExchange() {
    RelNode rel = database.createPhysicalRelBuilder()
        .physicalScan("tutorial", "users")
        .physicalExchange(Distribution.RANDOM)
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalUnicastExchange(distribution=[RANDOM])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests printing distribution of physical nodes for table with RANDOM distribution */
  @Test void testPrintPhysicalNodesWithRandomDistribution() throws Exception {
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(JsonSchema.parse("""
        {"tables": [{"name": "t", "distribution": "random", "columns": [
          {"name": "a", "type": "integer"},
          {"name": "b", "type": "integer"}
        ]}]}
        """));

    PhysicalRelBuilder builder = db.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "t")
        .physicalFilter(builder.greaterThan(builder.field("a"), builder.literal(10)))
        .physicalProject(builder.field("b"))
        .physicalExchange(Distribution.SINGLETON)
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalUnicastExchange(distribution=[SINGLETON])\n"
            + "  PhysicalProject(b=[$1], distribution=[RANDOM])\n"
            + "    PhysicalFilter(condition=[>($0, 10)], distribution=[RANDOM])\n"
            + "      PhysicalTableScan(table=[[tutorial, t]], distribution=[RANDOM])\n"));
  }

  /** Tests building logical project with physical table scan with builder */
  @Test void testBuildLogicalProjectWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .project(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("LogicalProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building logical filter with physical table scan with builder */
  @Test void testBuildLogicalFilterWithPhysicalScan() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .filter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("LogicalFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }
}
