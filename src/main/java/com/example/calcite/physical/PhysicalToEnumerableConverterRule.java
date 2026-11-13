package com.example.calcite.physical;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;

/** Rule that converts physical node to enumerable node. */
public class PhysicalToEnumerableConverterRule extends ConverterRule {
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(RelNode.class, PhysicalConvention.INSTANCE,
          EnumerableConvention.INSTANCE, "PhysicalToEnumerableConverterRule")
      .withRuleFactory(PhysicalToEnumerableConverterRule::new);

  public static final PhysicalToEnumerableConverterRule INSTANCE =
      DEFAULT_CONFIG.toRule(PhysicalToEnumerableConverterRule.class);

  protected PhysicalToEnumerableConverterRule(Config config) {
    super(config);
  }

  @Override public RelNode convert(RelNode rel) {
    return new PhysicalToEnumerableConverter(rel.getCluster(),
        rel.getTraitSet().replace(EnumerableConvention.INSTANCE), rel);
  }
}
