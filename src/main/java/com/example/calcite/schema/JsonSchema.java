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
            "columns": [
              {"name": "id", "type": "integer", "nullable": false},
              {"name": "name", "type": "varchar", "nullable": false},
              {"name": "dep_id", "type": "integer"}
            ]
          },
          {
            "name": "departments",
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

        // adding column to list of columns
        columns.add(new SchemaTable.Column(columnName, type, isNullable));
      }

      tables.put(tableName, new SchemaTable(columns));
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
