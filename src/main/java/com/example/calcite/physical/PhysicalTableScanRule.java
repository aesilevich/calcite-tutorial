package com.example.calcite.physical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.logical.LogicalTableScan;

/** Rule that converts logical table scan to physical table scan. */
public class PhysicalTableScanRule extends ConverterRule {
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalTableScan.class, Convention.NONE,
          PhysicalConvention.INSTANCE, "PhysicalTableScanRule")
      .withRuleFactory(PhysicalTableScanRule::new);

  public static final PhysicalTableScanRule INSTANCE =
      DEFAULT_CONFIG.toRule(PhysicalTableScanRule.class);

  protected PhysicalTableScanRule(Config config) {
    super(config);
  }

  @Override public RelNode convert(RelNode rel) {
    LogicalTableScan scan = (LogicalTableScan) rel;
    return new PhysicalTableScan(scan.getCluster(),
        scan.getTraitSet().replace(PhysicalConvention.INSTANCE), scan.getHints(),
        scan.getTable());
  }
}
