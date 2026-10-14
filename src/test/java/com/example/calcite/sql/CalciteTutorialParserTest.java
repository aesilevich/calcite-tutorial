package com.example.calcite.sql;

import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParserFixture;
import org.apache.calcite.sql.parser.SqlParserPos;
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
    SqlParserFixture fixture = sql("deallocate somefoo");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking node position
    assertThat(node.getParserPosition(), is(new SqlParserPos(1, 1, 1, 18)));

    // checking identifier
    DeallocateStatement deallocate = (DeallocateStatement)node;
    assertThat(deallocate.id.getSimple(), is("SOMEFOO"));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE `SOMEFOO`");
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

    // checking node position
    assertThat(node.getParserPosition(), is(new SqlParserPos(1, 1, 1, 14)));

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
    SqlParserFixture fixture = sql("deallocate prepare somefoo");

    // checking node type
    SqlNode node = fixture.node();
    assertThat(node, instanceOf(DeallocateStatement.class));

    // checking node position
    assertThat(node.getParserPosition(), is(new SqlParserPos(1, 1, 1, 26)));

    // checking identifier
    DeallocateStatement deallocate = (DeallocateStatement) node;
    assertThat(deallocate.id.getSimple(), is("SOMEFOO"));
    assertThat(deallocate.prepare, is(true));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE PREPARE `SOMEFOO`");
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

    // checking node position
    assertThat(node.getParserPosition(), is(new SqlParserPos(1, 1, 1, 22)));

    // checking identifier (ALL has no identifier)
    DeallocateStatement deallocate = (DeallocateStatement) node;
    assertThat(deallocate.id, nullValue());
    assertThat(deallocate.prepare, is(true));

    // checking parsed SQL string
    fixture.ok("DEALLOCATE PREPARE ALL");
  }
}
