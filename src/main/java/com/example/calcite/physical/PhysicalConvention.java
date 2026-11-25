package com.example.calcite.physical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;

import com.example.calcite.distribution.DistributionTraitDef;

/** Convention for physical nodes. */
public enum PhysicalConvention implements Convention {
  INSTANCE;

  @Override public String toString() {
    return getName();
  }

  @Override public Class<PhysicalNode> getInterface() {
    return PhysicalNode.class;
  }

  @Override public String getName() {
    return "PHYSICAL";
  }

  @Override public RelTraitDef<Convention> getTraitDef() {
    return ConventionTraitDef.INSTANCE;
  }

  @Override public boolean satisfies(RelTrait trait) {
    return this == trait;
  }

  @Override public void register(RelOptPlanner planner) {}

  /** Returns true to make Volcano create abstract converters between physical subsets. */
  @Override public boolean useAbstractConvertersForConversion(RelTraitSet fromTraits,
      RelTraitSet toTraits) {
    return true;
  }

  /** Creates exchange that converts input to required distribution. */
  @Override public RelNode enforce(RelNode input, RelTraitSet required) {
    return new PhysicalUnicastExchange(input.getCluster(),
        input.getTraitSet().replace(required.getTrait(DistributionTraitDef.INSTANCE)), input);
  }
}
