package com.example.calcite.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.type.SqlTypeName;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/** Cusom Calcite schema represented as JSON. */
public class JsonSchema extends AbstractSchema {
  /** Default database schema. */
  private static final String DEFAULT_SCHEMA_JSON = """
      {
        "tables": [
          {
            "name": "users",
            "rowCount": 10000,
            "columns": [
              {"name": "id", "type": "integer", "nullable": false},
              {"name": "name", "type": "varchar", "nullable": false},
              {"name": "dep_id", "type": "integer"}
            ]
          },
          {
            "name": "departments",
            "rowCount": 1000,
            "columns": [
              {"name": "id", "type": "integer", "nullable": false},
              {"name": "name", "type": "varchar", "nullable": false}
            ]
          }
        ]
      }
      """;

  /** Map of tables in schema. */
  private final Map<String, Table> tables;

  /** Creates default schema. */
  public JsonSchema() throws JsonProcessingException {
    this(DEFAULT_SCHEMA_JSON);
  }

  /** Parses JSON string and creates schema from it */
  public JsonSchema(String schemaJson) throws JsonProcessingException {
    JsonNode root = new ObjectMapper().readTree(schemaJson);
    if (!root.isObject()) {
      throw new IllegalArgumentException("Schema is not an object");
    }

    tables = new HashMap<>();
    for (JsonNode tableNode : getFieldArray(root, "tables", "schema")) {
      if (!tableNode.isObject()) {
        throw new IllegalArgumentException("Table is not an object");
      }

      String tableName = getFieldText(tableNode, "name", "table");

      // reading optional number of rows
      Double rowCount = null;
      JsonNode rowCountNode = tableNode.get("rowCount");
      if (rowCountNode != null) {
        if (!rowCountNode.isNumber() || rowCountNode.doubleValue() < 0) {
          throw new IllegalArgumentException("Field 'rowCount' in table '" + tableName
              + "' is not a non-negative number");
        }
        rowCount = rowCountNode.doubleValue();
      }

      List<SchemaTable.Column> columns = new ArrayList<>();
      for (JsonNode columnNode : getFieldArray(tableNode, "columns", "table '" + tableName + "'")) {
        if (!columnNode.isObject()) {
          throw new IllegalArgumentException("Column of table '" + tableName
              + "' is not an object");
        }

        // reading column name, type and nullable flag
        String columnName =
            getFieldText(columnNode, "name", "column of table '" + tableName + "'");
        String typeName =
            getFieldText(columnNode, "type", "column '" + tableName + "." + columnName + "'");

        boolean isNullable = true;
        JsonNode nullableNode = columnNode.get("nullable");
        if (nullableNode != null) {
          if (!nullableNode.isBoolean()) {
            throw new IllegalArgumentException("Field 'nullable' in column '" + tableName + "."
                + columnName + "' is not a boolean");
          }
          isNullable = nullableNode.booleanValue();
        }

        // parsing type name
        SqlTypeName type = SqlTypeName.get(typeName.toUpperCase(Locale.ROOT));
        if (type == null) {
          throw new IllegalArgumentException("Unknown type '" + typeName + "' for column '"
              + tableName + "." + columnName + "'");
        }

        // reading precision (or length) and scale
        String columnDescription = "column '" + tableName + "." + columnName + "'";
        Integer precision =
            getFieldNonNegativeInteger(columnNode, "precision", columnDescription);
        Integer length = getFieldNonNegativeInteger(columnNode, "length", columnDescription);
        Integer scale = getFieldNonNegativeInteger(columnNode, "scale", columnDescription);

        // checkint that only precision or length is specified
        if (precision != null && length != null) {
          throw new IllegalArgumentException("Both 'precision' and 'length' are specified for "
              + columnDescription);
        }
        if (length != null) {
          precision = length;
        }

        // checking that Calcite type supports precision without scale
        if (precision != null && !type.allowsPrecNoScale()) {
          throw new IllegalArgumentException("Type " + type + " of " + columnDescription
              + " does not support precision");
        }

        if (scale != null) {
          // checking that Calcite type supports scale
          if (!type.allowsPrecScale(true, true)) {
            throw new IllegalArgumentException("Type " + type + " of " + columnDescription
                + " does not support scale");
          }

          // checking that precision/length is specified
          if (precision == null) {
            throw new IllegalArgumentException("Scale is specified without precision for "
                + columnDescription);
          }
        }

        // adding column to list of columns
        columns.add(new SchemaTable.Column(columnName, type, isNullable, precision, scale));
      }

      tables.put(tableName, new SchemaTable(columns, rowCount));
    }
  }

  /** Returns table with specified name */
  public SchemaTable getSchemaTable(String name) {
    SchemaTable table = (SchemaTable) tables.get(name);
    if (table == null) {
      throw new IllegalArgumentException("Table '" + name + "' not found");
    }

    return table;
  }

  /**
   * Returns text value for specified field, throws exception if field is not found
   * or is not a string.
   */
  private static String getFieldText(JsonNode node, String field, String owner) {
    JsonNode value = node.get(field);
    if (value == null) {
      throw new IllegalArgumentException("Can't find field '" + field + "' in " + owner);
    }

    if (!value.isTextual()) {
      throw new IllegalArgumentException("Field '" + field + "' in " + owner
          + " is not a string");
    }

    return value.textValue();
  }

  /**
   * Returns integer value for specified optional field, or null if field is not found.
   * Throws exception if field is not a non-negative integer.
   */
  private static @Nullable Integer getFieldNonNegativeInteger(JsonNode node, String field,
      String description) {
    JsonNode value = node.get(field);
    if (value == null) {
      return null;
    }

    if (!value.isInt() || value.intValue() < 0) {
      throw new IllegalArgumentException("Field '" + field + "' in " + description
          + " is not a non-negative integer");
    }

    return value.intValue();
  }

  /**
   * Returns array value for specified field, throws exception if field is not found
   * or is not an array.
   */
  private static JsonNode getFieldArray(JsonNode node, String field, String owner) {
    JsonNode value = node.get(field);
    if (value == null) {
      throw new IllegalArgumentException("Can't find field '" + field + "' in " + owner);
    }

    if (!value.isArray()) {
      throw new IllegalArgumentException("Field '" + field + "' in " + owner
          + " is not an array");
    }

    return value;
  }

  @Override protected Map<String, Table> getTableMap() {
    return tables;
  }

  @Override protected Map<String, RelProtoDataType> getTypeMap() {
    return Map.of();
  }

  @Override protected Multimap<String, Function> getFunctionMultimap() {
    return ImmutableMultimap.of();
  }

  @Override protected Map<String, Schema> getSubSchemaMap() {
    return Map.of();
  }
}
