package com.example.calcite.physical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.logical.LogicalFilter;

/** Rule that converts logical filter to physical filter. */
public class PhysicalFilterRule extends ConverterRule {
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalFilter.class, Convention.NONE,
          PhysicalConvention.INSTANCE, "PhysicalFilterRule")
      .withRuleFactory(PhysicalFilterRule::new);

  public static final PhysicalFilterRule INSTANCE =
      DEFAULT_CONFIG.toRule(PhysicalFilterRule.class);

  protected PhysicalFilterRule(Config config) {
    super(config);
  }

  @Override public RelNode convert(RelNode rel) {
    LogicalFilter filter = (LogicalFilter) rel;
    RelNode input = filter.getInput();
    return new PhysicalFilter(filter.getCluster(),
        filter.getTraitSet().replace(PhysicalConvention.INSTANCE), filter.getHints(),
        convert(input, input.getTraitSet().replace(PhysicalConvention.INSTANCE)),
        filter.getCondition());
  }
}
