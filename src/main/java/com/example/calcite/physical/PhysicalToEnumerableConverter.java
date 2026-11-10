package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterImpl;

/** Physical -> Enumerable converter node. */
public class PhysicalToEnumerableConverter extends ConverterImpl implements EnumerableRel {
  public PhysicalToEnumerableConverter(RelOptCluster cluster, RelTraitSet traits,
      RelNode input) {
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new PhysicalToEnumerableConverter(getCluster(), traitSet, sole(inputs));
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
    throw new UnsupportedOperationException("Execution of physical nodes is not supported");
  }
}
