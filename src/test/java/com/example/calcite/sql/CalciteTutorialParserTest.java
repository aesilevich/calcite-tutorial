package com.example.calcite.sql;

import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParserFixture;
import org.apache.calcite.sql.parser.SqlParserTest;
import org.junit.jupiter.api.Test;

import com.example.calcite.parser.impl.SqlParserImpl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Parser tests for SQL extension with DEALLOCATE statement
 */
class CalciteTutorialParserTest extends SqlParserTest {
  @Override public SqlParserFixture fixture() {
    return super.fixture()
        .withConfig(c -> c.withParserFactory(SqlParserImpl.FACTORY));
  }

/**
 * Tests parsing DEALLOCATE statement with identifier
 */
  @Test void testDeallocate() {
    // parsing statement
    SqlParserFixture fixture = sql("deallocate a");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking identifier
    DeallocateStatement deallocate = (DeallocateStatement)node;
    assertThat(deallocate.id.getSimple(), is("A"));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE `A`");
  }

/**
 * Tests parsing DEALLOCATE ALL statement
 */
  @Test void testDeallocateAll() {
    // parsing statement
    SqlParserFixture fixture = sql("deallocate all");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking identifier (ALL has no identifier)
    DeallocateStatement deallocate = (DeallocateStatement) node;
    assertThat(deallocate.id, nullValue());

    // checking parsed SQL string
    fixture.ok("DEALLOCATE ALL");
  }

/**
 * Tests parsing DEALLOCATE PREPARE statement with identifier
 */
  @Test void testDeallocatePrepare() {
    // parsing statement
    SqlParserFixture fixture = sql("deallocate prepare x");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking identifier
    DeallocateStatement deallocate = (DeallocateStatement) node;
    assertThat(deallocate.id.getSimple(), is("X"));
    assertThat(deallocate.prepare, is(true));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE PREPARE `X`");
  }

/**
 * Tests parsing DEALLOCATE PREPARE ALL statement
 */
  @Test void testDeallocatePrepareAll() {
    // parsing statement
    SqlParserFixture fixture = sql("deallocate prepare all");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking identifier (ALL has no identifier)
    DeallocateStatement deallocate = (DeallocateStatement) node;
    assertThat(deallocate.id, nullValue());
    assertThat(deallocate.prepare, is(true));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE PREPARE ALL");
  }
}
