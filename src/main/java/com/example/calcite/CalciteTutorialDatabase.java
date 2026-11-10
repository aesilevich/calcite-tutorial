package com.example.calcite;

import com.example.calcite.parser.impl.SqlParserImpl;
import com.example.calcite.physical.PhysicalCalc;
import com.example.calcite.logical.LogicalFilterToScanRule;
import com.example.calcite.logical.LogicalProjectToScanRule;
import com.example.calcite.logical.LogicalTableScanWithFilterAndProjectRule;
import com.example.calcite.physical.PhysicalConvention;
import com.example.calcite.physical.PhysicalFilter;
import com.example.calcite.physical.PhysicalFilterRule;
import com.example.calcite.physical.PhysicalFilterToCalcRule;
import com.example.calcite.physical.PhysicalProject;
import com.example.calcite.physical.PhysicalProjectRule;
import com.example.calcite.physical.PhysicalProjectToCalcRule;
import com.example.calcite.physical.PhysicalRelBuilder;
import com.example.calcite.physical.PhysicalTableScanRule;
import com.example.calcite.physical.PhysicalToEnumerableConverterRule;
import com.example.calcite.schema.JsonSchema;
import com.example.calcite.sql.UnsupportedOperationValidator;
import com.example.calcite.sql.OperatorTable;
import com.example.calcite.sql.RexExecutor;
import com.example.calcite.sql.StandardOperatorTableWithoutAbs;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.List;

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.adapter.enumerable.EnumerableCalc;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableFilter;
import org.apache.calcite.adapter.enumerable.EnumerableProject;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.rules.FilterCalcMergeRule;
import org.apache.calcite.rel.rules.ProjectCalcMergeRule;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.sql2rel.RelDecorrelator;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;
import org.apache.calcite.tools.RelBuilder;

/**
 * Main database class for tutorial. Incapsulates initialization of parser, validator
 * and optimizer for processing queries.
 */
public class CalciteTutorialDatabase {
  /* Schema name */
  public final String schemaName = "tutorial";

  /* Type factory */
  private RelDataTypeFactory typeFactory;

  /* Root Calcite schema */
  private CalciteSchema rootSchema;

  /* Database schema */
  private AbstractSchema schema;

  /* Catalog reader */
  private CalciteCatalogReader catalogReader;

  /* Operator table */
  private SqlOperatorTable operatorTable;

  /* Initializes all parts of database for processing queries */
  public CalciteTutorialDatabase() throws JsonProcessingException {
    this(JsonSchema.createDefault());
  }

  /* Initializes all parts of database for processing queries with specified schema */
  public CalciteTutorialDatabase(AbstractSchema schema) {
    // creating type factory
    typeFactory = new JavaTypeFactoryImpl();

    // creating root schema
    rootSchema = CalciteSchema.createRootSchema(false, false);

    // adding database schema
    this.schema = schema;
    rootSchema.add(schemaName, schema);

    // creating catalog reader
    CalciteConnectionConfig config =
        CalciteConnectionConfig.DEFAULT.set(CalciteConnectionProperty.CASE_SENSITIVE, "false");
    catalogReader = new CalciteCatalogReader(rootSchema,
        Collections.singletonList(schemaName), typeFactory, config);

    // using standard operator table with ABS replaced by advanced ABS function
    operatorTable = SqlOperatorTables.chain(OperatorTable.instance(),
        new StandardOperatorTableWithoutAbs().init());
  }

  /* Returns database schema */
  public AbstractSchema getSchema() {
    return schema;
  }

  /* Creates relational builder with new cluster */
  public com.example.calcite.logical.RelBuilder createRelBuilder() {
    return com.example.calcite.logical.RelBuilder.create(createCluster(), catalogReader);
  }

  /* Creates physical relational builder with new cluster */
  public PhysicalRelBuilder createPhysicalRelBuilder() {
    return PhysicalRelBuilder.create(createCluster(), catalogReader);
  }

  /* Parses and validates query, and converts it to relational tree without optimization. */
  public RelRoot convertQuery(String query) throws SqlParseException {
    SqlParser.Config parserConfig = SqlParser.config()
        .withParserFactory(SqlParserImpl.FACTORY)
        .withCaseSensitive(false);
  
    SqlValidator validator = createValidator();
    SqlToRelConverter converter = createConverter(validator);
        
    // parsing query
    SqlNode node = SqlParser.create(query, parserConfig).parseQuery();

    // validating query
    SqlNode validatedNode = validator.validate(node);

    // checking for unsupported operations
    validatedNode.accept(new UnsupportedOperationValidator());

    // converting query to relational tree
    RelRoot root = converter.convertQuery(validatedNode, false, true);

    // trimming unused fields
    return root.withRel(converter.trimUnusedFields(true, root.rel));
  }

  /* Optimizes logical relational tree. */
  public RelRoot optimizeQueryLogical(RelRoot root) {
    RelNode rel = root.rel;

    // creating HEP planner
    HepProgram program = HepProgram.builder()
        .addRuleCollection(List.of(
            CoreRules.FILTER_SUB_QUERY_TO_CORRELATE,
            CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE,
            CoreRules.JOIN_SUB_QUERY_TO_CORRELATE,

            CoreRules.FILTER_REDUCE_EXPRESSIONS,
            CoreRules.PROJECT_REDUCE_EXPRESSIONS,
            CoreRules.JOIN_REDUCE_EXPRESSIONS,
            CoreRules.CALC_REDUCE_EXPRESSIONS,
            CoreRules.WINDOW_REDUCE_EXPRESSIONS))
        .addRuleCollection(List.of(
            CoreRules.FILTER_PROJECT_TRANSPOSE,
            CoreRules.FILTER_INTO_JOIN,
            CoreRules.JOIN_CONDITION_PUSH,
            CoreRules.FILTER_AGGREGATE_TRANSPOSE,
            CoreRules.FILTER_SET_OP_TRANSPOSE,
            CoreRules.FILTER_MERGE,
            CoreRules.JOIN_PUSH_EXPRESSIONS))
        .build();
    HepPlanner hepPlanner = new HepPlanner(program);
    hepPlanner.setRoot(root.rel);

    // optimizing relation tree with HEP planner
    rel = hepPlanner.findBestExp();

    // decorrelating query
    RelBuilder relBuilder = RelFactories.LOGICAL_BUILDER.create(rel.getCluster(), null);
    rel = RelDecorrelator.decorrelateQuery(rel, relBuilder);

    return root.withRel(rel);
  }

  /* Parses and validates query, converts it to logical relational tree and optimizes it. */
  public RelRoot processQueryLogical(String query) throws SqlParseException {
    return optimizeQueryLogical(convertQuery(query));
  }

  /*
   * Converts logical relational tree to physical relation tree with Volcano planner
   * and optimizes it.
   */
  public RelRoot optimizeQueryPhysical(RelRoot root) {
    return optimizeQueryVolcano(root, PhysicalConvention.INSTANCE);
  }

  /*
   * Converts logical relational tree to enumerable relation tree with Volcano planner
   * and optimizes it.
   */
  public RelRoot optimizeQueryEnumerable(RelRoot root) {
    return optimizeQueryVolcano(root, EnumerableConvention.INSTANCE);
  }

  /*
   * Converts logical relational tree to physical representation with Volcano planner
   * using specified convention for root node.
   */
  public RelRoot optimizeQueryVolcano(RelRoot root, Convention convention) {
    RelNode rel = root.rel;

    // converting logical nodes to physical nodes with Volcano planner
    RelOptPlanner volcanoPlanner = rel.getCluster().getPlanner();
    EnumerableRules.ENUMERABLE_RULES.forEach(volcanoPlanner::addRule);
    volcanoPlanner.addRule(LogicalTableScanWithFilterAndProjectRule.INSTANCE);
    volcanoPlanner.addRule(LogicalFilterToScanRule.INSTANCE);
    volcanoPlanner.addRule(LogicalProjectToScanRule.INSTANCE);
    volcanoPlanner.addRule(PhysicalTableScanRule.INSTANCE);

    // we want generate physical project and filter nodes only
    // if requested convention is physical
    if (convention == PhysicalConvention.INSTANCE) {
      volcanoPlanner.addRule(PhysicalProjectRule.INSTANCE);
      volcanoPlanner.addRule(PhysicalFilterRule.INSTANCE);
    }
    volcanoPlanner.addRule(PhysicalToEnumerableConverterRule.INSTANCE);
    volcanoPlanner.addRule(CoreRules.FILTER_PROJECT_TRANSPOSE);
    volcanoPlanner.addRule(CoreRules.PROJECT_MERGE);
    volcanoPlanner.addRule(CoreRules.FILTER_AGGREGATE_TRANSPOSE);
    volcanoPlanner.addRule(CoreRules.FILTER_SET_OP_TRANSPOSE);

    RelTraitSet traits = rel.getTraitSet().replace(convention);
    if (!rel.getTraitSet().equals(traits)) {
      rel = volcanoPlanner.changeTraits(rel, traits);
    }

    volcanoPlanner.setRoot(rel);
    rel = volcanoPlanner.findBestExp();

    return root.withRel(rel);
  }

  /* Converts physical projects and filters to physical calcs with HEP planner. */
  public RelRoot convertToCalc(RelRoot root) {
    HepProgram program = HepProgram.builder()
        .addRuleCollection(List.of(
            PhysicalProjectToCalcRule.INSTANCE,
            PhysicalFilterToCalcRule.INSTANCE,
            CoreRules.CALC_MERGE,
            FilterCalcMergeRule.Config.DEFAULT
                .withOperandFor(PhysicalFilter.class, PhysicalCalc.class)
                .toRule(),
            ProjectCalcMergeRule.Config.DEFAULT
                .withOperandFor(PhysicalProject.class, PhysicalCalc.class)
                .toRule(),
            EnumerableRules.ENUMERABLE_PROJECT_TO_CALC_RULE,
            EnumerableRules.ENUMERABLE_FILTER_TO_CALC_RULE,
            FilterCalcMergeRule.Config.DEFAULT
                .withOperandFor(EnumerableFilter.class, EnumerableCalc.class)
                .toRule(),
            ProjectCalcMergeRule.Config.DEFAULT
                .withOperandFor(EnumerableProject.class, EnumerableCalc.class)
                .toRule()))
        .build();
    HepPlanner hepPlanner = new HepPlanner(program);
    hepPlanner.setRoot(root.rel);
    return root.withRel(hepPlanner.findBestExp());
  }

  /*
   * Parses and validates query, converts it to relational tree and then performs
   * all optimization on logical and physical level making final tree with
   * root node with physical convention.
   */
  public RelRoot processQueryPhysical(String query) throws SqlParseException {
    return processQuery(query, PhysicalConvention.INSTANCE);
  }

  /*
   * Parses and validates query, converts it to relational tree and then performs
   * all optimization on logical and physical level making final tree with
   * root node with enumerable convention.
   */
  public RelRoot processQueryEnumerable(String query) throws SqlParseException {
    return processQuery(query, EnumerableConvention.INSTANCE);
  }

  /*
   * Parses and validates query, optimizes it and converts to final physical representation.
   */
  public RelRoot processQuery(String query, Convention convention) throws SqlParseException {
    return convertToCalc(optimizeQueryVolcano(processQueryLogical(query), convention));
  }

  /* Creates query planner */
  private VolcanoPlanner createPlanner() {
    VolcanoPlanner planner = new VolcanoPlanner();
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    planner.setExecutor(new RexExecutor());
    return planner;
  }

  /* Creates cluster  */
  private RelOptCluster createCluster() {
    return RelOptCluster.create(createPlanner(), new RexBuilder(typeFactory));
  }

  /* Creates query validator */
  private SqlValidator createValidator() {
    return SqlValidatorUtil.newValidator(operatorTable, catalogReader, typeFactory,
        SqlValidator.Config.DEFAULT);
  }

  /* Creates query converter */
  private SqlToRelConverter createConverter(SqlValidator validator) {
    SqlToRelConverter.Config converterConfig = SqlToRelConverter.config()
        .withTrimUnusedFields(true)
        .withExpand(false);

    return new SqlToRelConverter(
        null,
        validator,
        catalogReader,
        createCluster(),
        StandardConvertletTable.INSTANCE,
        converterConfig
    );
  }
}
