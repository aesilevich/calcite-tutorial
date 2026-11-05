package com.example.calcite.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.apache.calcite.util.ImmutableBitSet;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/** Tests for JdbcSchema. */
class JdbcSchemaTest {
  /** URL of in-memory HSQLDB database */
  private static final String JDBC_URL = "jdbc:hsqldb:mem:keys";

  /** Tests reading primary key and unique indexes of table in custom JDBC schema */
  @Test void testKeys() throws Exception {
    try (Connection connection = DriverManager.getConnection(JDBC_URL, "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table \"t\" (\"a\" integer not null, \"b\" integer not null, "
          + "\"c\" integer not null, \"d\" integer, \"e\" integer not null, "
          + "primary key (\"a\"), unique (\"b\", \"c\"), unique (\"d\"))");
      statement.execute("create index \"t_e\" on \"t\" (\"e\")");

      Schema schema = JdbcSchema.readCatalog(connection).getSchema("PUBLIC");

      // unique index with nullable column and non-unique index are not keys
      List<ImmutableBitSet> keys = schema.getSchemaTable("t").getStatistic().getKeys();
      assertThat(keys, containsInAnyOrder(ImmutableBitSet.of(0), ImmutableBitSet.of(1, 2)));
      assertThat(keys.size(), is(2));
    }
  }

  /** Tests that table without primary key and unique indexes has no keys */
  @Test void testNoKeys() throws Exception {
    try (Connection connection = DriverManager.getConnection(JDBC_URL, "SA", "");
        Statement statement = connection.createStatement()) {
      statement.execute("create table \"n\" (\"a\" integer not null)");

      Schema schema = JdbcSchema.readCatalog(connection).getSchema("PUBLIC");

      assertThat(schema.getSchemaTable("n").getStatistic().getKeys(), is(List.of()));
    }
  }
}
