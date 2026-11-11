package com.example.calcite.physical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;

import com.example.calcite.logical.LogicalTableScanWithFilterAndProject;
import com.example.calcite.schema.SchemaTable;

/**
 * Rule that converts logical table scan with filter and project to physical table scan
 * for tables of custom schema.
 */
public class PhysicalTableScanRule extends ConverterRule {
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalTableScanWithFilterAndProject.class,
          scan -> scan.getTable().unwrap(SchemaTable.class) != null, Convention.NONE,
          PhysicalConvention.INSTANCE, "PhysicalTableScanRule")
      .withRuleFactory(PhysicalTableScanRule::new);

  public static final PhysicalTableScanRule INSTANCE =
      DEFAULT_CONFIG.toRule(PhysicalTableScanRule.class);

  protected PhysicalTableScanRule(Config config) {
    super(config);
  }

  @Override public RelNode convert(RelNode rel) {
    LogicalTableScanWithFilterAndProject scan = (LogicalTableScanWithFilterAndProject) rel;
    return new PhysicalTableScan(scan.getCluster(),
        scan.getTraitSet().replace(PhysicalConvention.INSTANCE), scan.getHints(),
        scan.getTable(), scan.getFilter(), scan.getProjection());
  }
}
