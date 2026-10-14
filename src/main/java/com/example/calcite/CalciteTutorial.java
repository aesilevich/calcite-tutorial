package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;


public class CalciteTutorial {
  public static void main(String[] args) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:");
         Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery(
             "SELECT x AS x, x * 2 AS doubled FROM (VALUES 1, 2, 3) AS t(x)")) {
      while (rs.next()) {
        System.out.println(rs.getInt("x") + " -> " + rs.getInt("doubled"));
      }
    }
  }
}
