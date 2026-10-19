package com.example.calcite.schema;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.lookup.LikePattern;
import org.junit.jupiter.api.Test;

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
    JsonSchema schema = new JsonSchema("""
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
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [{"name": "c", "type": "foo"}]}]}
            """));

    assertThat(e.getMessage(), is("Unknown type 'foo' for column 't.c'"));
  }

  /** Tests parsing error with missing table name. */
  @Test void testMissingTableName() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"columns": [{"name": "c", "type": "integer"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'name' in table"));
  }

  /** Tests parsing error with missing column name. */
  @Test void testMissingColumnName() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [{"type": "integer"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'name' in column of table 't'"));
  }

  /** Tests parsing error with missing column type. */
  @Test void testMissingColumnType() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [{"name": "c"}]}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'type' in column 't.c'"));
  }

  /** Tests parsing error with non-string column type. */
  @Test void testColumnTypeNotString() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [{"name": "c", "type": 5}]}]}
            """));

    assertThat(e.getMessage(), is("Field 'type' in column 't.c' is not a string"));
  }

  /** Tests parsing error with schema that is not an object. */
  @Test void testSchemaNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("[]"));

    assertThat(e.getMessage(), is("Schema is not an object"));
  }

  /** Tests parsing error with missing tables. */
  @Test void testMissingTables() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("{}"));

    assertThat(e.getMessage(), is("Can't find field 'tables' in schema"));
  }

  /** Tests parsing error with tables that are not an array. */
  @Test void testTablesNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": {}}
            """));

    assertThat(e.getMessage(), is("Field 'tables' in schema is not an array"));
  }

  /** Tests parsing error with missing columns. */
  @Test void testMissingColumns() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t"}]}
            """));

    assertThat(e.getMessage(), is("Can't find field 'columns' in table 't'"));
  }

  /** Tests parsing error with columns that are not an array. */
  @Test void testColumnsNotArray() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": "c"}]}
            """));

    assertThat(e.getMessage(), is("Field 'columns' in table 't' is not an array"));
  }

  /** Tests parsing error with table that is not an object. */
  @Test void testTableNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [5]}
            """));

    assertThat(e.getMessage(), is("Table is not an object"));
  }

  /** Tests parsing error with column that is not an object. */
  @Test void testColumnNotObject() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": ["c"]}]}
            """));

    assertThat(e.getMessage(), is("Column of table 't' is not an object"));
  }

  /** Tests parsing error with nullable flag that is not a boolean. */
  @Test void testNullableNotBoolean() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "integer", "nullable": "no"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'nullable' in column 't.c' is not a boolean"));
  }

  /** Tests parsing row count for table. */
  @Test void testRowCount() throws Exception {
    JsonSchema schema = new JsonSchema("""
        {"tables": [{"name": "t", "rowCount": 100, "columns": [
          {"name": "c", "type": "integer"}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getStatistic().getRowCount(), is(100.0));
  }

  /** Tests that row count is unknown if it's not specified. */
  @Test void testRowCountUnknown() throws Exception {
    JsonSchema schema = new JsonSchema("""
        {"tables": [{"name": "t", "columns": [
          {"name": "c", "type": "integer"}
        ]}]}
        """);

    assertThat(schema.getSchemaTable("t").getStatistic().getRowCount(), nullValue());
  }

  /** Tests parsing error with row count that is not a number. */
  @Test void testRowCountNotNumber() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "rowCount": "many", "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'rowCount' in table 't' is not a non-negative number"));
  }

  /** Tests parsing error with negative row count. */
  @Test void testRowCountNegative() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "rowCount": -1, "columns": [
              {"name": "c", "type": "integer"}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Field 'rowCount' in table 't' is not a non-negative number"));
  }

  /** Tests parsing precision, length and scale for columns. */
  @Test void testPrecisionAndScale() throws Exception {
    JsonSchema schema = new JsonSchema("""
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
        () -> new JsonSchema("""
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
        () -> new JsonSchema("""
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
        () -> new JsonSchema("""
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
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "varchar", "length": 10, "scale": 2}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Type VARCHAR of column 't.c' does not support scale"));
  }

  /** Tests parsing error with scale specified without precision. */
  @Test void testScaleWithoutPrecision() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new JsonSchema("""
            {"tables": [{"name": "t", "columns": [
              {"name": "c", "type": "decimal", "scale": 2}
            ]}]}
            """));

    assertThat(e.getMessage(), is("Scale is specified without precision for column 't.c'"));
  }

  /** Tests tables in default schema. */
  @Test void testDefaultSchema() throws Exception {
    JsonSchema schema = new JsonSchema();

    assertThat(schema.tables().getNames(LikePattern.any()),
        containsInAnyOrder("users", "departments"));
    assertThat(schema.getSchemaTable("users").getRowType(typeFactory).getFullTypeString(),
        is("RecordType(INTEGER NOT NULL id, VARCHAR NOT NULL name, INTEGER dep_id) NOT NULL"));
  }
}
