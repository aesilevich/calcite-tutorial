package com.example.calcite.metadata;

import java.util.List;
import java.util.Set;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.MetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;

/** Metadata query that supports standard metadata and {@link UsedTables} metadata. */
public class TutorialRelMetadataQuery extends RelMetadataQuery {
  private final UsedTables.Handler usedTablesHandler;

  public TutorialRelMetadataQuery(MetadataHandlerProvider provider) {
    super(provider);
    usedTablesHandler = revise(UsedTables.Handler.class);
  }

  /** Returns set of qualified names of tables used by relational node and its inputs. */
  public Set<List<String>> getUsedTables(RelNode rel) {
    return usedTablesHandler.getUsedTables(rel, this);
  }
}
