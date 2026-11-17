package com.example.calcite.schema;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

import com.example.calcite.enumerable.EnumerableFileScan;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;

/** Table of departments read from CSV file. */
public class CsvDepartmentsTable extends AbstractTable implements TranslatableTable {
  /** Mapper for reading CSV rows as arrays of strings */
  private static final CsvMapper MAPPER = new CsvMapper();

  /** Creates new reader of CSV file for each scan */
  private final Supplier<Reader> readerSupplier;

  public CsvDepartmentsTable(Supplier<Reader> readerSupplier) {
    this.readerSupplier = readerSupplier;
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return typeFactory.builder()
        .add("id", SqlTypeName.INTEGER)
        .add("name", SqlTypeName.VARCHAR)
        .add("description", SqlTypeName.VARCHAR)
        .build();
  }

  @Override public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
    return EnumerableFileScan.create(context.getCluster(), relOptTable, null);
  }

  /** Returns rows of table with columns at specified indexes. */
  public Enumerable<Object[]> scan(int[] projection) {
    return new AbstractEnumerable<>() {
      @Override public Enumerator<Object[]> enumerator() {
        return new CsvEnumerator(readerSupplier.get(), projection);
      }
    };
  }

  /** Converts CSV value to value of table column. */
  private static Object convert(int column, String value) {
    return switch (column) {
      case 0 -> Integer.parseInt(value);
      case 1, 2 -> value;
      default -> throw new IllegalArgumentException("Unknown column " + column);
    };
  }

  /** Enumerator over CSV rows that closes reader when closed. */
  private static class CsvEnumerator implements Enumerator<Object[]> {
    private final MappingIterator<String[]> rows;
    private final int[] projection;
    private Object[] current;

    CsvEnumerator(Reader reader, int[] projection) {
      try {
        this.rows = MAPPER.readerFor(String[].class)
            .with(CsvParser.Feature.WRAP_AS_ARRAY)
            .readValues(reader);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      this.projection = projection;
    }

    @Override public Object[] current() {
      return current;
    }

    @Override public boolean moveNext() {
      if (!rows.hasNext()) {
        return false;
      }

      String[] values = rows.next();
      current = new Object[projection.length];
      for (int i = 0; i < projection.length; i++) {
        current[i] = convert(projection[i], values[projection[i]]);
      }
      return true;
    }

    @Override public void reset() {
      throw new UnsupportedOperationException();
    }

    @Override public void close() {
      try {
        rows.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
