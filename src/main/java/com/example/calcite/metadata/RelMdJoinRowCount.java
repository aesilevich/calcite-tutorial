package com.example.calcite.metadata;

import java.util.List;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinInfo;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.util.ImmutableBitSet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Custom implementation of row count estimation for joins that uses unique keys.
 */
public class RelMdJoinRowCount implements MetadataHandler<BuiltInMetadata.RowCount> {
  public static final RelMetadataProvider SOURCE =
      ReflectiveRelMetadataProvider.reflectiveSource(new RelMdJoinRowCount(),
          BuiltInMetadata.RowCount.Handler.class);

  @Override public MetadataDef<BuiltInMetadata.RowCount> getDef() {
    return BuiltInMetadata.RowCount.DEF;
  }

  /**
   * Returns estimated row count for join node taking into account unique keys.
   */
  public @Nullable Double getRowCount(Join join, RelMetadataQuery mq) {
    // splitting condition into key equalities and remaining condition
    JoinInfo info =
        JoinInfo.createWithStrictEquality(join.getLeft(), join.getRight(), join.getCondition());

    // if list of key equalities is empty then use default row count calculation
    if (info.leftKeys.isEmpty()) {
      return getDefaultRowCount(join, mq);
    }

    Double leftRowCount = mq.getRowCount(join.getLeft());
    Double rightRowCount = mq.getRowCount(join.getRight());

    // using default row count calculation if row count info is missing
    // for left or right operands
    if (leftRowCount == null || rightRowCount == null) {
      return getDefaultRowCount(join, mq);
    }

    // checking if equality columns of left and right operands contain unique keys
    boolean isLeftUnique = Boolean.TRUE.equals(
        mq.areColumnsUnique(join.getLeft(), ImmutableBitSet.of(info.leftKeys), true));
    boolean isRightUnique = Boolean.TRUE.equals(
        mq.areColumnsUnique(join.getRight(), ImmutableBitSet.of(info.rightKeys), true));

    // computing result for different join types
    switch (join.getJoinType()) {
    case INNER: {
      // scaling row count of other operand by fraction of rows left in unique operand
      // after filtering
      double rowCount;
      if (isLeftUnique && isRightUnique) {
        // estimated row count for an inner join with unique keys in the left and right operands
        // is the minimum estimated row count of the two operands
        rowCount = Math.min(
            leftRowCount * getFraction(join.getRight(), info.rightKeys, rightRowCount, mq),
            rightRowCount * getFraction(join.getLeft(), info.leftKeys, leftRowCount, mq));
      } else if (isRightUnique) {
        // estimated row count for inner join with unique keys in right operand
        // is estimated row count for left operand
        rowCount =
            leftRowCount * getFraction(join.getRight(), info.rightKeys, rightRowCount, mq);
      } else if (isLeftUnique) {
        // estimated row count for inner join with unique keys in left operand
        // is estimated row count for right operand
        rowCount =
            rightRowCount * getFraction(join.getLeft(), info.leftKeys, leftRowCount, mq);
      } else {
        return getDefaultRowCount(join, mq);
      }

      // applying selectivity of remaining condition for inner join
      RexNode remaining =
          RexUtil.composeConjunction(join.getCluster().getRexBuilder(), info.nonEquiConditions);
      Double selectivity = mq.getSelectivity(join, remaining);
      return selectivity == null ? rowCount : rowCount * selectivity;
    }

    case LEFT:
      // if right operand in left join has unique keys then we use estimated row count for left
      return isRightUnique ? leftRowCount : getDefaultRowCount(join, mq);

    case RIGHT:
      // if left operand in right join has unique keys then we use estimated row count for right
      return isLeftUnique ? rightRowCount : getDefaultRowCount(join, mq);

    default:
      // we don't use custom row count calculation for other join types
      return getDefaultRowCount(join, mq);
    }
  }

  /**
   * Returns fraction of rows left in operand after filtering, computed as ratio of operand
   * row count and population size of operand keys. Returns 1 if population size is unknown.
   */
  private static double getFraction(RelNode input, List<Integer> keys, double rowCount,
      RelMetadataQuery mq) {
    Double populationSize = mq.getPopulationSize(input, ImmutableBitSet.of(keys));
    if (populationSize == null || populationSize <= 0) {
      return 1;
    }

    return Math.min(1, rowCount / populationSize);
  }

  /** Returns row count of join estimated by default Calcite implementation. */
  private static @Nullable Double getDefaultRowCount(Join join, RelMetadataQuery mq) {
    return RelMdUtil.getJoinRowCount(mq, join, join.getCondition());
  }
}
