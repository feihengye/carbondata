package org.apache.spark.carbondata.restructure.vectorreader

import java.math.{BigDecimal, RoundingMode}
import java.sql.Timestamp

import org.apache.spark.sql.Row
import org.apache.spark.sql.common.util.QueryTest
import org.scalatest.BeforeAndAfterAll

class AddColumnTestCases extends QueryTest with BeforeAndAfterAll {

  override def beforeAll {
    sqlContext.setConf("carbon.enable.vector.reader", "true")
    sql("DROP TABLE IF EXISTS addcolumntest")
    sql("drop table if exists hivetable")
    sql(
      "CREATE TABLE addcolumntest(intField int,stringField string,timestampField timestamp," +
      "decimalField decimal(6,2)) STORED BY 'carbondata'")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data4.csv' INTO TABLE addcolumntest " +
        s"options('FILEHEADER'='intField,stringField,timestampField,decimalField')")
    sql(
      "Alter table addcolumntest add columns(charField string) TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField', 'DEFAULT.VALUE.charfield'='def')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE addcolumntest " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
  }

  test("test like query on new column") {
    checkAnswer(sql("select charField from addcolumntest where charField like 'd%'"), Row("def"))
  }

  test("test is not null filter on new column") {
    checkAnswer(sql("select charField from addcolumntest where charField is not null"),
      Seq(Row("abc"), Row("def")))
  }

  test("test is null filter on new column") {
    checkAnswer(sql("select charField from addcolumntest where charField is null"), Seq())
  }

  test("test equals filter on new column") {
    checkAnswer(sql("select charField from addcolumntest where charField = 'abc'"), Row("abc"))
  }

  test("test add dictionary column and test greaterthan/lessthan filter on new column") {
    sql(
      "Alter table addcolumntest add columns(intnewField int) TBLPROPERTIES" +
      "('DICTIONARY_INCLUDE'='intnewField', 'DEFAULT.VALUE.intNewField'='5')")
    checkAnswer(sql("select charField from addcolumntest where intnewField > 2"),
      Seq(Row("abc"), Row("def")))
    checkAnswer(sql("select charField from addcolumntest where intnewField < 2"), Seq())
  }

  test("test add msr column and check aggregate") {
    sql(
      "alter table addcolumntest add columns(msrField decimal(5,2))TBLPROPERTIES ('DEFAULT.VALUE" +
      ".msrfield'= '123.45')")
    checkAnswer(sql("select sum(msrField) from addcolumntest"),
      Row(new BigDecimal("246.90").setScale(2, RoundingMode.HALF_UP)))
  }

  test("test compaction after adding new column") {
    sql("Alter table addcolumntest compact 'major'")
    checkExistence(sql("show segments for table addcolumntest"), true, "0Compacted")
    checkExistence(sql("show segments for table addcolumntest"), true, "1Compacted")
    checkExistence(sql("show segments for table addcolumntest"), true, "0.1Success")
    checkAnswer(sql("select charField from addcolumntest"), Seq(Row("abc"), Row("def")))
  }

  test("test add and drop column with data loading") {
    sql("DROP TABLE IF EXISTS carbon_table")
    sql(
      "CREATE TABLE carbon_table(intField int,stringField string,charField string,timestampField " +
      "timestamp,decimalField decimal(6,2))STORED BY 'carbondata' TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
    sql("Alter table carbon_table drop columns(timestampField)")
    sql("select * from carbon_table").collect
    sql("Alter table carbon_table add columns(timestampField timestamp)")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data5.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,charField,decimalField,timestampField')")
    sql("DROP TABLE IF EXISTS carbon_table")
  }

  test("test add/drop and change datatype") {
    sql("DROP TABLE IF EXISTS carbon_table")
    sql(
      "CREATE TABLE carbon_table(intField int,stringField string,charField string,timestampField " +
      "timestamp,decimalField decimal(6,2))STORED BY 'carbondata' TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
    sql("Alter table carbon_table drop columns(charField)")
    sql("select * from carbon_table").collect
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data4.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,timestampField,decimalField')")
    sql(
      "Alter table carbon_table add columns(charField string) TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data2.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,timestampField,decimalField,charField')")
    sql("select * from carbon_table").collect
    sql("ALTER TABLE carbon_table CHANGE decimalField decimalField decimal(22,6)")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data3.csv' INTO TABLE carbon_table " +
        s"options('FILEHEADER'='intField,stringField,timestampField,decimalField,charField')")
    sql("DROP TABLE IF EXISTS carbon_table")
  }

  test("test to check if select * works for new added column") {
    sql("DROP TABLE IF EXISTS carbon_new")
    sql(
      "CREATE TABLE carbon_new(intField int,stringField string,charField string,timestampField " +
      "timestamp,decimalField decimal(6,2))STORED BY 'carbondata' TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE carbon_new " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
    sql(
      "Alter table carbon_new add columns(newField string) TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='newField','DEFAULT.VALUE.newField'='def')")
    checkAnswer(sql("select * from carbon_new limit 1"),
      Row(new Integer(100),
        "spark",
        "abc",
        Timestamp.valueOf("2015-04-23 00:00:00.0"),
        new BigDecimal(21.23).setScale(2, RoundingMode.HALF_UP),
        "def"))
    sql("drop table carbon_new")
  }

  test("test to check data if all columns are provided in select") {
    sql("DROP TABLE IF EXISTS carbon_new")
    sql(
      "CREATE TABLE carbon_new(intField int,stringField string,charField string,timestampField " +
      "timestamp,decimalField decimal(6,2))STORED BY 'carbondata' TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE carbon_new " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
    sql(
      "Alter table carbon_new add columns(newField string) TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='newField')")
    assert(sql(
      "select intField,stringField,charField,timestampField,decimalField, newField from " +
      "carbon_new limit 1").count().equals(1L))
    sql("drop table carbon_new")
  }

  test("test to check data if new column query order is different from schema order") {
    sql("DROP TABLE IF EXISTS carbon_new")
    sql(
      "CREATE TABLE carbon_new(intField int,stringField string,charField string,timestampField " +
      "timestamp,decimalField decimal(6,2))STORED BY 'carbondata' TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='charField')")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data1.csv' INTO TABLE carbon_new " +
        s"options('FILEHEADER'='intField,stringField,charField,timestampField,decimalField')")
    sql(
      "Alter table carbon_new add columns(newField string) TBLPROPERTIES" +
      "('DICTIONARY_EXCLUDE'='newField','DEFAULT.VALUE.newField'='def')")
    checkAnswer(sql(
      "select intField,stringField,charField,newField,timestampField,decimalField from " +
      "carbon_new limit 1"), Row(new Integer(100),
      "spark",
      "abc",
      "def",
      Timestamp.valueOf("2015-04-23 00:00:00.0"),
      new BigDecimal(21.23).setScale(2, RoundingMode.HALF_UP)))
    sql("drop table carbon_new")
  }

  test("test to check if vector result collector is able to fetch large amount of data") {
    sql("DROP TABLE IF EXISTS carbon_new")
    sql(
      """CREATE TABLE carbon_new (CUST_ID int,CUST_NAME String,ACTIVE_EMUI_VERSION string, DOB
        |timestamp, DOJ timestamp, BIGINT_COLUMN1 bigint,BIGINT_COLUMN2 bigint,DECIMAL_COLUMN1
        |decimal(30,10), DECIMAL_COLUMN2 decimal(36,10),Double_COLUMN1 double, Double_COLUMN2
        |double,INTEGER_COLUMN1 int) STORED BY 'org.apache.carbondata.format' TBLPROPERTIES
        |("TABLE_BLOCKSIZE"= "256 MB")""".stripMargin)
    sql("alter table carbon_new drop columns(CUST_NAME)")
    sql(s"LOAD DATA INPATH '$resourcesPath/restructure/data_2000.csv' into table " +
        "carbon_new OPTIONS('DELIMITER'=',' , 'QUOTECHAR'='\"','BAD_RECORDS_ACTION'='FORCE'," +
        "'FILEHEADER'='CUST_ID,CUST_NAME,ACTIVE_EMUI_VERSION,DOB,DOJ,BIGINT_COLUMN1," +
        "BIGINT_COLUMN2,DECIMAL_COLUMN1,DECIMAL_COLUMN2,Double_COLUMN1,Double_COLUMN2," +
        "INTEGER_COLUMN1')")
    sql(
      """alter table carbon_new add columns(CUST_NAME string) TBLPROPERTIES
        ('DICTIONARY_EXCLUDE'='CUST_NAME', 'DEFAULT.VALUE.CUST_NAME'='testuser')""")
    checkAnswer(sql("select distinct(CUST_NAME) from carbon_new"),Row("testuser"))
  }


  override def afterAll {
    sql("DROP TABLE IF EXISTS addcolumntest")
    sql("drop table if exists hivetable")
    sqlContext.setConf("carbon.enable.vector.reader", "false")
  }
}
