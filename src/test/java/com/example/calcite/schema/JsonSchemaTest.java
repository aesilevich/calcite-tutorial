package com.example.calcite.schema;

import java.util.List;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.lookup.LikePattern;
import org.apache.calcite.util.ImmutableBitSet;
import org.junit.jupiter.api.Test;

import com.example.calcite.distribution.Distribution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for JsonSchema. */
class JsonSchemaTest {
  private final RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl();

  /** Tests parsing schema from JSON string. */
  @Test void testParseSchema() throws Exception {
    Schema schema = JsonSchema.parse("""
        {
          "tables": [
            {
              "name": "orders",
              "columns": [
                {"name": "id", "type": "bigint", "nullable": false},
                {"name": "comment", "type": "varchar"}
              ]
            }
          ]
        }
        """);

    assertThat(schema.getSchemaTable("orders").getRowType(typeFactory).getFullTypeString(),
        is("RecordType(BIGINT NOT NULL id, VARCHAR comment) NOT NULL"));
  }

  /** Tests parsing error with unknown type. */
  @Test void testUnknownType() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [{"name": "c", "type": "foo"}]}]}
            """));

    assertThat(e.getMessage(), is("Unknown type 'foo' for column 't.c'"));
  }

  /** Tests parsing error with missing table name. */
  @Test void testMissingTableName() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"columns": [{"name": "c", "type": "integer"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'name' in table"));
  }

  /** Tests parsing error with missing column name. */
  @Test void testMissingColumnName() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [{"type": "integer"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'name' in column of table 't'"));
  }

  /** Tests parsing error with missing column type. */
  @Test void testMissingColumnType() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [{"name": "c"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'type' in column 't.c'"));
  }

  /** Tests parsing error with non-string column type. */
  @Test void testColumnTypeNotString() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [{"name": "c", "type": 5}]}]}
            """));

    assertThat(e.getMessage(), is("Field 'type' in column 't.c' is not a string"));
  }

  /** Tests parsing error with schema that is not an object. */
  @Test void testSchemaNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("[]"));

    assertThat(e.getMessage(), is("Schema is not an object"));
  }

  /** Tests parsing error with missing tables. */
  @Test void testMissingTables() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("{}"));

    assertThat(e.getMessage(), is("Can't find field 'tables' in schema"));
  }

  /** Tests parsing error with tables that are not an array. */
  @Test void testTablesNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": {}}
            """));

    assertThat(e.getMessage(), is("Field 'tables' in schema is not an array"));
  }

  /** Tests parsing error with missing columns. */
  @Test void testMissingColumns() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t"}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'columns' in table 't'"));
  }

  /** Tests parsing error with columns that are not an array. */
  @Test void testColumnsNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": "c"}]}
            """));

    assertThat(e.getMessage(), is("Field 'columns' in table 't' is not an array"));
  }

  /** Tests parsing error with table that is not an object. */
  @Test void testTableNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [5]}
            """));

    assertThat(e.getMessage(), is("Table is not an object"));
  }

  /** Tests parsing error with column that is not an object. */
  @Test void testColumnNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": ["c"]}]}
            """));

    assertThat(e.getMessage(), is("Column of table 't' is not an object"));
  }

  /** Tests parsing error with nullable flag that is not a boolean. */
  @Test void testNullableNotBoolean() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "integer", "nullable": "no"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'nullable' in column 't.c' is not a boolean"));
  }

  /** Tests parsing row count for table. */
  @Test void testRowCount() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "rowCount": 100, "columns": [
          {"name": "c", "type": "integer"}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getStatistic().getRowCount(), is(100.0));
  }

  /** Tests that row count is unknown if it's not specified. */
  @Test void testRowCountUnknown() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "columns": [
          {"name": "c", "type": "integer"}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getStatistic().getRowCount(), nullValue());
  }

  /** Tests parsing error with row count that is not a number. */
  @Test void testRowCountNotNumber() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "rowCount": "many", "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'rowCount' in table 't' is not a non-negative number"));
  }

  /** Tests parsing error with negative row count. */
  @Test void testRowCountNegative() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "rowCount": -1, "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'rowCount' in table 't' is not a non-negative number"));
  }

  /** Tests parsing precision, length and scale for columns. */
  @Test void testPrecisionAndScale() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "columns": [
          {"name": "d", "type": "decimal", "precision": 10, "scale": 2},
          {"name": "s", "type": "varchar", "length": 100},
          {"name": "p", "type": "varchar", "precision": 50}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getRowType(typeFactory).getFullTypeString(),
        is("RecordType(DECIMAL(10, 2) d, VARCHAR(100) s, VARCHAR(50) p) NOT NULL"));
  }

  /** Tests parsing error with precision that is not an integer. */
  @Test void testPrecisionNotInteger() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "varchar", "precision": "10"}
            ]}]}
            """));

    assertThat(e.getMessage(),
        is("Field 'precision' in column 't.c' is not a non-negative integer"));
  }

  /** Tests parsing error with both precision and length specified. */
  @Test void testPrecisionAndLength() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "varchar", "precision": 10, "length": 10}
            ]}]}
            """));

    assertThat(e.getMessage(),
        is("Both 'precision' and 'length' are specified for column 't.c'"));
  }

  /** Tests parsing error with precision for type that does not support it. */
  @Test void testPrecisionNotSupported() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "integer", "precision": 5}
            ]}]}
            """));

    assertThat(e.getMessage(),
        is("Type INTEGER of column 't.c' does not support precision"));
  }

  /** Tests parsing error with scale for type that does not support it. */
  @Test void testScaleNotSupported() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "varchar", "length": 10, "scale": 2}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Type VARCHAR of column 't.c' does not support scale"));
  }

  /** Tests parsing error with scale specified without precision. */
  @Test void testScaleWithoutPrecision() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "decimal", "scale": 2}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Scale is specified without precision for column 't.c'"));
  }

  /** Tests parsing single-column and multi-column unique keys of table in JSON schema. */
  @Test void testKeys() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "keys": [["a"], ["c", "b"]], "columns": [
          {"name": "a", "type": "integer", "nullable": false},
          {"name": "b", "type": "integer", "nullable": false},
          {"name": "c", "type": "integer", "nullable": false}
        ]}]}
        """);

    Statistic statistic = schema.getSchemaTable("t").getStatistic();
    assertThat(statistic.getKeys(), is(List.of(ImmutableBitSet.of(0), ImmutableBitSet.of(1, 2))));
    assertThat(statistic.isKey(ImmutableBitSet.of(0)), is(true));
    assertThat(statistic.isKey(ImmutableBitSet.of(0, 1)), is(true));
    assertThat(statistic.isKey(ImmutableBitSet.of(1)), is(false));
    assertThat(statistic.isKey(ImmutableBitSet.of(1, 2)), is(true));
  }

  /** Tests that table has no keys if they are not specified in JSON schema. */
  @Test void testNoKeys() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "columns": [
          {"name": "c", "type": "integer"}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getStatistic().getKeys(), is(List.of()));
  }

  /** Tests JSON schema parsing error with keys that are not an array. */
  @Test void testKeysNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": "c", "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'keys' in table 't' is not an array"));
  }

  /** Tests JSON schema parsing error with key that is not an array. */
  @Test void testKeyNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": ["c"], "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Key of table 't' is not a non-empty array"));
  }

  /** Tests JSON schema parsing error with empty key. */
  @Test void testKeyEmpty() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": [[]], "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Key of table 't' is not a non-empty array"));
  }

  /** Tests JSON schema parsing error with key column that is not a string. */
  @Test void testKeyColumnNotString() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": [[0]], "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Key column of table 't' is not a string"));
  }

  /** Tests JSON schema parsing error with unknown key column. */
  @Test void testKeyColumnUnknown() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": [["x"]], "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Unknown key column 'x' in table 't'"));
  }

  /** Tests JSON schema parsing error with duplicate key column. */
  @Test void testKeyColumnDuplicate() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": [["c", "c"]], "columns": [
              {"name": "c", "type": "integer", "nullable": false}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Duplicate key column 'c' in table 't'"));
  }

  /** Tests JSON schema parsing error with nullable key column. */
  @Test void testKeyColumnNullable() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "keys": [["a", "b"]], "columns": [
              {"name": "a", "type": "integer", "nullable": false},
              {"name": "b", "type": "integer", "nullable": true}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Key column 'b' of table 't' is nullable"));
  }

  /** Tests parsing distribution for table in JSON schema. */
  @Test void testDistribution() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [
          {"name": "r", "distribution": "replicated",
           "columns": [{"name": "c", "type": "integer"}]},
          {"name": "a", "distribution": "random",
           "columns": [{"name": "c", "type": "integer"}]},
          {"name": "h", "distribution": "hash_sharded",
           "columns": [{"name": "c", "type": "integer"}]},
          {"name": "s", "distribution": "SINGLETON",
           "columns": [{"name": "c", "type": "integer"}]}
        ]}
        """);

    assertThat(schema.getSchemaTable("r").getDistribution(), is(Distribution.REPLICATED));
    assertThat(schema.getSchemaTable("a").getDistribution(), is(Distribution.RANDOM));
    assertThat(schema.getSchemaTable("h").getDistribution(), is(Distribution.HASH_SHARDED));
    assertThat(schema.getSchemaTable("s").getDistribution(), is(Distribution.SINGLETON));
  }

  /** Tests that distribution of table is SINGLETON if it's not specified in JSON schema. */
  @Test void testDistributionDefault() throws Exception {
    Schema schema = JsonSchema.parse("""
        {"tables": [{"name": "t", "columns": [{"name": "c", "type": "integer"}]}]}
        """);

    assertThat(schema.getSchemaTable("t").getDistribution(), is(Distribution.SINGLETON));
  }

  /** Tests JSON schema parsing error with distribution that is not a string. */
  @Test void testDistributionNotString() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "distribution": 1, "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'distribution' in table 't' is not a string"));
  }

  /** Tests JSON schema parsing error with unknown distribution. */
  @Test void testDistributionUnknown() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> JsonSchema.parse("""
            {"tables": [{"name": "t", "distribution": "unknown", "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Unknown distribution 'unknown' for table 't'"));
  }

  /** Tests tables in default schema. */
  @Test void testDefaultSchema() throws Exception {
    Schema schema = JsonSchema.createDefault();

    assertThat(schema.tables().getNames(LikePattern.any()),
        containsInAnyOrder("users", "departments"));
    assertThat(schema.getSchemaTable("users").getRowType(typeFactory).getFullTypeString(),
        is("RecordType(INTEGER NOT NULL id, VARCHAR NOT NULL name, INTEGER dep_id) NOT NULL"));
  }
}
