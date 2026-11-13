package com.example.calcite;

import com.example.calcite.parser.impl.SqlParserImpl;
import com.example.calcite.physical.PhysicalConvention;
import com.example.calcite.physical.PhysicalRelBuilder;
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
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
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
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;

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
    return converter.convertQuery(validatedNode, false, true);
  }

  /* Optimizes logical relational tree. */
  public RelRoot optimizeQueryLogical(RelRoot root) {
    RelNode rel = root.rel;
    return root.withRel(LogicalOptimizeProgram.INSTANCE.run(rel.getCluster().getPlanner(), rel,
        rel.getTraitSet(), List.of(), List.of()));
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
    RelNode rel = root.rel;
    RelTraitSet physicalTraits = rel.getTraitSet().replace(PhysicalConvention.INSTANCE);
    return root.withRel(PhysicalOptimizeProgram.INSTANCE.run(rel.getCluster().getPlanner(), rel,
        physicalTraits, List.of(), List.of()));
  }

  /* Converts physical projects and filters to physical calcs with HEP planner. */
  public RelRoot convertToCalc(RelRoot root) {
    RelNode rel = root.rel;
    return root.withRel(ConvertToCalcProgram.INSTANCE.run(rel.getCluster().getPlanner(), rel,
        rel.getTraitSet(), List.of(), List.of()));
  }

  /*
   * Parses and validates query, converts it to relational tree and then performs
   * all optimization on logical and physical level.
   */
  public RelRoot processQuery(String query) throws SqlParseException {
    return convertToCalc(optimizeQueryPhysical(processQueryLogical(query)));
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
