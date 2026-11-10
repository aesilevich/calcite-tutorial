package com.example.calcite.physical;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.CalciteTutorialDatabase;

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

  /** Tests building enumerable project with builder */
  @Test void testBuildEnumerableProject() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .enumerableProject(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("EnumerableProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building enumerable filter with builder */
  @Test void testBuildEnumerableFilter() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .enumerableFilter(builder.greaterThan(builder.field("id"), builder.literal(10)))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("EnumerableFilter(condition=[>($0, 10)])\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
  }

  /** Tests building converter from physical to enumerable convention with builder */
  @Test void testBuildPhysicalToEnumerable() {
    RelNode rel = database.createPhysicalRelBuilder()
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("PhysicalToEnumerableConverter\n"
            + "  PhysicalTableScan(table=[[tutorial, users]])\n"));
    assertThat(rel.getConvention(), is(EnumerableConvention.INSTANCE));
    assertThat(rel.getInput(0).getConvention(), is(PhysicalConvention.INSTANCE));
  }

  /** Tests building enumerable project on top of converter with builder */
  @Test void testBuildEnumerableProjectWithPhysicalToEnumerable() {
    PhysicalRelBuilder builder = database.createPhysicalRelBuilder();
    RelNode rel = builder
        .physicalScan("tutorial", "users")
        .physicalToEnumerable()
        .enumerableProject(builder.field("dep_id"), builder.field("name"))
        .build();

    assertThat(RelOptUtil.toString(rel),
        is("EnumerableProject(dep_id=[$2], name=[$1])\n"
            + "  PhysicalToEnumerableConverter\n"
            + "    PhysicalTableScan(table=[[tutorial, users]])\n"));
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
