package com.example.calcite.distribution;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.calcite.physical.PhysicalConvention;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Tests for Distribution. */
class DistributionTest {
  /** List of all distributions */
  private static final List<Distribution> DISTRIBUTIONS = List.of(Distribution.REPLICATED,
      Distribution.RANDOM, Distribution.HASH_SHARDED, Distribution.SINGLETON,
      Distribution.UNKNOWN);

  /** Checks that distribution satisfies only specified distributions */
  private static void assertSatisfies(Distribution distribution, Distribution... satisfied) {
    Set<Distribution> satisfiedSet = Set.of(satisfied);
    for (Distribution required : DISTRIBUTIONS) {
      assertThat(distribution + " satisfies " + required, distribution.satisfies(required),
          is(satisfiedSet.contains(required)));
    }
  }

  /** Tests distributions satisfied by REPLICATED distribution */
  @Test void testReplicatedSatisfies() {
    assertSatisfies(Distribution.REPLICATED, Distribution.REPLICATED, Distribution.UNKNOWN);
  }

  /** Tests distributions satisfied by RANDOM distribution */
  @Test void testRandomSatisfies() {
    assertSatisfies(Distribution.RANDOM, Distribution.RANDOM, Distribution.UNKNOWN);
  }

  /** Tests distributions satisfied by HASH_SHARDED distribution */
  @Test void testHashShardedSatisfies() {
    assertSatisfies(Distribution.HASH_SHARDED, Distribution.HASH_SHARDED, Distribution.RANDOM,
        Distribution.UNKNOWN);
  }

  /** Tests distributions satisfied by SINGLETON distribution */
  @Test void testSingletonSatisfies() {
    assertSatisfies(Distribution.SINGLETON, Distribution.SINGLETON, Distribution.UNKNOWN);
  }

  /** Tests distributions satisfied by UNKNOWN distribution */
  @Test void testUnknownSatisfies() {
    assertSatisfies(Distribution.UNKNOWN, Distribution.UNKNOWN);
  }

  /** Tests that distribution trait doesn't satisfy trait of other trait definition */
  @Test void testNotSatisfiesConvention() {
    assertThat(Distribution.SINGLETON.satisfies(PhysicalConvention.INSTANCE), is(false));
  }

  /** Tests that default distribution is SINGLETON */
  @Test void testDefault() {
    assertThat(DistributionTraitDef.INSTANCE.getDefault(), is(Distribution.SINGLETON));
  }
}
