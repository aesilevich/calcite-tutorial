package com.example.calcite.physical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.logical.LogicalProject;

/** Rule that converts logical project to physical project. */
public class PhysicalProjectRule extends ConverterRule {
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalProject.class, Convention.NONE,
          PhysicalConvention.INSTANCE, "PhysicalProjectRule")
      .withRuleFactory(PhysicalProjectRule::new);

  public static final PhysicalProjectRule INSTANCE =
      DEFAULT_CONFIG.toRule(PhysicalProjectRule.class);

  protected PhysicalProjectRule(Config config) {
    super(config);
  }

  @Override public RelNode convert(RelNode rel) {
    LogicalProject project = (LogicalProject) rel;
    RelNode input = project.getInput();
    return new PhysicalProject(project.getCluster(),
        project.getTraitSet().replace(PhysicalConvention.INSTANCE), project.getHints(),
        convert(input, input.getTraitSet().replace(PhysicalConvention.INSTANCE)),
        project.getProjects(), project.getRowType());
  }
}
