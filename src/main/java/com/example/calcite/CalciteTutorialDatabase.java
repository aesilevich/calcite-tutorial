package com.example.calcite;

import com.example.calcite.parser.impl.SqlParserImpl;
import com.example.calcite.schema.JsonSchema;
import com.example.calcite.sql.UnsupportedOperationValidator;
import com.example.calcite.sql.OperatorTable;
import com.example.calcite.sql.StandardOperatorTableWithoutAbs;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.List;

import org.apache.calcite.DataContexts;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexExecutorImpl;
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

  /* Parses and validates query, converts it to relational tree and optimizes it. */
  public RelRoot processQuery(String query) throws SqlParseException {
    RelRoot root = convertQuery(query);
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

  /* Creates query planner */
  private VolcanoPlanner createPlanner() {
    VolcanoPlanner planner = new VolcanoPlanner();
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    planner.setExecutor(new RexExecutorImpl(DataContexts.EMPTY));
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
