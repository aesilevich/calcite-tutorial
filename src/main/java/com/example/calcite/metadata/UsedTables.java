package com.example.calcite.metadata;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.apache.calcite.linq4j.tree.Types;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.Metadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.RelMetadataQuery;

/**
 * Metadata that contains set of qualified names of tables used by relational node
 * and its inputs.
 */
public interface UsedTables extends Metadata {
  Method METHOD = Types.lookupMethod(UsedTables.class, "getUsedTables");

  MetadataDef<UsedTables> DEF =
      MetadataDef.of(UsedTables.class, UsedTables.Handler.class, METHOD);

  /** Returns set of qualified names of tables used by relational node and its inputs. */
  Set<List<String>> getUsedTables();

  /** Handler API. */
  interface Handler extends MetadataHandler<UsedTables> {
    Set<List<String>> getUsedTables(RelNode rel, RelMetadataQuery mq);

    @Override default MetadataDef<UsedTables> getDef() {
      return DEF;
    }
  }
}
