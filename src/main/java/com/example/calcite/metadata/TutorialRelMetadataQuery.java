package com.example.calcite.metadata;

import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.ChainedRelMetadataProvider;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider;
import org.apache.calcite.rel.metadata.MetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;

/** Metadata query that supports standard metadata and {@link UsedTables} metadata. */
public class TutorialRelMetadataQuery extends RelMetadataQuery {
  private final UsedTables.Handler usedTablesHandler;

  public TutorialRelMetadataQuery(MetadataHandlerProvider provider) {
    super(provider);
    usedTablesHandler = revise(UsedTables.Handler.class);
  }

  /** Sets custom metadata providers and tutorial metadata query for cluster. */
  public static void register(RelOptCluster cluster) {
    // adding provider for custom metadata
    RelMetadataProvider provider = ChainedRelMetadataProvider.of(
        List.of(RelMdUsedTables.SOURCE,
                RelMdJoinRowCount.SOURCE,
                RelMdScanColumnUniqueness.SOURCE,
                DefaultRelMetadataProvider.INSTANCE));
    cluster.setMetadataProvider(provider);
    JaninoRelMetadataProvider handlerProvider = JaninoRelMetadataProvider.of(provider);
    cluster.setMetadataQuerySupplier(() -> new TutorialRelMetadataQuery(handlerProvider));
  }

  /** Returns set of qualified names of tables used by relational node and its inputs. */
  public Set<List<String>> getUsedTables(RelNode rel) {
    return usedTablesHandler.getUsedTables(rel, this);
  }
}
