package com.example.calcite.schema;

import java.util.Collections;
import java.util.Map;

import org.apache.calcite.schema.impl.AbstractSchema;

/** Calcite schema that contains map of sub schemas. */
public class SchemaCatalog extends AbstractSchema {
  /** Map of sub schemas. */
  private final Map<String, Schema> schemas;

  /** Creates catalog with specified map of schemas. */
  public SchemaCatalog(Map<String, Schema> schemas) {
    this.schemas = schemas;
  }

  /** Returns schema with specified name. */
  public Schema getSchema(String name) {
    Schema schema = schemas.get(name);
    if (schema == null) {
      throw new IllegalArgumentException("Schema '" + name + "' not found");
    }

    return schema;
  }

  @Override protected Map<String, org.apache.calcite.schema.Schema> getSubSchemaMap() {
    return Collections.unmodifiableMap(schemas);
  }
}
