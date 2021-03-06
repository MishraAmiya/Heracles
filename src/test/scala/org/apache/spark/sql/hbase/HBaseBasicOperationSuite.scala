/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.hbase

import org.apache.hadoop.hbase.TableName
import org.apache.spark.sql.catalyst.util.DateTimeUtils

/**
 * Test insert / query against the table
 */
class HBaseBasicOperationSuite extends TestBaseWithSplitData {
  import org.apache.spark.sql.hbase.TestHbase._

  override def afterAll() = {
    if (TestHbase.hbaseAdmin.tableExists(TableName.valueOf("ht0"))) {
      TestHbase.hbaseAdmin.disableTable(TableName.valueOf("ht0"))
      TestHbase.hbaseAdmin.deleteTable(TableName.valueOf("ht0"))
    }
    if (TestHbase.hbaseAdmin.tableExists(TableName.valueOf("ht1"))) {
      TestHbase.hbaseAdmin.disableTable(TableName.valueOf("ht1"))
      TestHbase.hbaseAdmin.deleteTable(TableName.valueOf("ht1"))
    }
    super.afterAll()
  }

  test("DateType test") {
    sql( """CREATE TABLE date_table (c1 DATE, c2 DATE) TBLPROPERTIES(
        'hbaseTableName'='date_table',
        'keyCols'='c1',
        'nonKeyCols'='c2,f,c')""")
    sql("insert into table date_table values ('2010-12-31', '2010-01-01')")
    sql("insert into table date_table values ('2011-12-31', '2011-01-01')")
    sql("insert into table date_table values ('2012-12-31', '2012-01-01')")

    val result1 = sql("select * from date_table where c1 < cast('2012-12-31' as date) order by c2 desc").collect
    assert(result1.length == 2)

    val result2 = sql("select * from date_table where c2 < cast('2012-01-01' as date) order by c2 desc").collect
    assert(result2.length == 2)
    assert(result2(0).get(0).toString == "2011-12-31")
    assert(result2(0).get(1).toString == "2011-01-01")
    assert(result2(1).get(0).toString == "2010-12-31")
    assert(result2(1).get(1).toString == "2010-01-01")
    sql("drop table date_table")
  }

  test("TimestampType test") {
    sql( """CREATE TABLE ts_table (c1 TIMESTAMP, c2 TIMESTAMP) TBLPROPERTIES(
        'hbaseTableName'='ts_table',
        'keyCols'='c1',
        'nonKeyCols'='c2,f,c')""")
    sql("insert into table ts_table values ('2009-08-07 03:14:15', '2009-08-07 13:14:15')")
    sql("insert into table ts_table values ('2010-08-07 03:14:15', '2010-08-07 13:14:15')")
    sql("insert into table ts_table values ('2011-08-07 03:14:15', '2011-08-07 13:14:15')")

    val result1 = sql("select * from ts_table where c1 < cast('2011-08-07 03:14:15' as timestamp) order by c2 desc").collect
    assert(result1.length == 2)

    val result2 = sql("select * from ts_table where c2 < cast('2011-08-07 13:14:15' as timestamp) order by c2 desc").collect
    assert(result2.length == 2)
    assert(result2(0).get(0).toString == "2010-08-07 03:14:15.0")
    assert(result2(0).get(1).toString == "2010-08-07 13:14:15.0")
    assert(result2(1).get(0).toString == "2009-08-07 03:14:15.0")
    assert(result2(1).get(1).toString == "2009-08-07 13:14:15.0")
    sql("drop table ts_table")
  }

  test("Insert Into table in StringFormat") {
    sql( """CREATE TABLE tb0 (column2 INTEGER, column1 INTEGER, column4 FLOAT, column3 SHORT) TBLPROPERTIES(
        'hbaseTableName'='default.ht0',
        'keyCols'='column1',
        'nonKeyCols'='column2,family0,qualifier0;column3,family1,qualifier1;column4,family2,qualifier2')""")

    assert(sql( """SELECT * FROM tb0""").collect().length == 0)
    sql( """INSERT INTO TABLE tb0 SELECT col4,col4,col6,col3 FROM ta""")
    assert(sql( """SELECT * FROM tb0""").collect().length == 14)

    sql( """SELECT * FROM tb0""").show
    sql( """SELECT * FROM tb0 where column2 > 200""").show

    sql( """DROP TABLE tb0""")
    dropNativeHbaseTable("default.ht0")
  }

  test("Insert and Query Single Row") {
    sql( """CREATE TABLE tb1 (column1 INTEGER, column2 STRING) TBLPROPERTIES(
        'hbaseTableName'='ht1',
        'keyCols'='column1',
        'nonKeyCols'='column2,cf,cq')"""
    )

    assert(sql( """SELECT * FROM tb1""").collect().length == 0)
    sql( """INSERT INTO TABLE tb1 VALUES (1024, "abc")""")
    sql( """INSERT INTO TABLE tb1 VALUES (1028, "abd")""")
    assert(sql( """SELECT * FROM tb1""").collect().length == 2)
    assert(
      sql( """SELECT * FROM tb1 WHERE (column1 = 1023 AND column2 ="abc")""").collect().length == 0)
    assert(sql(
      """SELECT * FROM tb1 WHERE (column1 = 1024)
        |OR (column1 = 1028 AND column2 ="abd")""".stripMargin).collect().length == 2)

    sql( """DROP TABLE tb1""")
    dropNativeHbaseTable("ht1")
  }

  test("Insert and Query Single Row in StringFormat") {
    sql( """CREATE TABLE tb1 (col1 STRING,col2 BOOLEAN,col3 SHORT,col4 INTEGER,col5 LONG,col6 FLOAT,col7 DOUBLE) TBLPROPERTIES(
        'hbaseTableName'='ht2',
        'keyCols'='col1',
        'nonKeyCols'='col2,cf1,cq11;col3,cf1,cq12;col4,cf1,cq13;col5,cf2,cq21;col6,cf2,cq22;col7,cf2,cq23')""".stripMargin
    )

    assert(sql( """SELECT * FROM tb1""").collect().length == 0)
    sql( """INSERT INTO TABLE tb1 VALUES ("row1", false, 1000, 5050 , 50000 , 99.99 , 999.999)""")
    sql( """INSERT INTO TABLE tb1 VALUES ("row2", false, 99  , 10000, 9999  , 1000.1, 5000.5)""")
    sql( """INSERT INTO TABLE tb1 VALUES ("row3", true , 555 , 999  , 100000, 500.05, 10000.01)""")
    sql( """SELECT col1 FROM tb1 where col2<true order by col2""")
      .collect().zip(Seq("row1", "row2")).foreach{case (r,s) => assert(r.getString(0) == s)}
    sql( """SELECT * FROM tb1 where col3>500 order by col3""")
      .collect().zip(Seq("row3", "row1")).foreach{case (r,s) => assert(r.getString(0) == s)}
    sql( """SELECT * FROM tb1 where col4>5000 order by col4""")
      .collect().zip(Seq("row1", "row2")).foreach{case (r,s) => assert(r.getString(0) == s)}
    sql( """SELECT * FROM tb1 where col5>50000 order by col5""")
      .collect().zip(Seq("row3")).foreach{case (r,s) => assert(r.getString(0) == s)}
    sql( """SELECT * FROM tb1 where col6>500 order by col6""")
      .collect().zip(Seq("row3", "row2")).foreach{case (r,s) => assert(r.getString(0) == s)}
    sql( """SELECT * FROM tb1 where col7>5000 order by col7""")
      .collect().zip(Seq("row2", "row3")).foreach{case (r,s) => assert(r.getString(0) == s)}

    sql( """DROP TABLE tb1""")
    dropNativeHbaseTable("ht2")
  }

  test("Select test 0") {
    assert(sql( """SELECT * FROM ta""").count() == 14)
  }

  test("Count(*/1) and Non-Key Column Query") {
    assert(sql( """SELECT count(*) FROM ta""").collect()(0).get(0) == 14)
    assert(sql( """SELECT count(*) FROM ta where col2 < 8""").collect()(0).get(0) == 7)
    assert(sql( """SELECT count(*) FROM ta where col4 < 0""").collect()(0).get(0) == 7)
    assert(sql( """SELECT count(1) FROM ta where col2 < 8""").collect()(0).get(0) == 7)
    assert(sql( """SELECT count(1) FROM ta where col4 < 0""").collect()(0).get(0) == 7)
  }

  test("InSet Query") {
    assert(sql( """SELECT count(*) FROM ta where col2 IN (1, 2, 3)""").collect()(0).get(0) == 3)
    assert(sql( """SELECT count(*) FROM ta where col4 IN (1, 2, 3)""").collect()(0).get(0) == 1)
  }

  test("Point Aggregate Query") {
    sql( """CREATE TABLE tb2 (column2 INTEGER,column1 INTEGER,column4 FLOAT,column3 SHORT) TBLPROPERTIES(
        'hbaseTableName'='default.ht0',
        'keyCols'='column1;column2',
        'nonKeyCols'='column3,family1,qualifier1;column4,family2,qualifier2')"""
    )
    sql( """INSERT INTO TABLE tb2 SELECT col4,col4,col6,col3 FROM ta""")
    val result = sql( """SELECT count(*) FROM tb2 where column1=1 AND column2=1""").collect()
    assert(result.length == 1)
    assert(result(0).get(0) == 1)
  }

  test("Select test 1 (AND, OR)") {
    assert(sql( """SELECT * FROM ta WHERE col7 = 255 OR col7 = 127""").collect().length == 2)
    assert(sql( """SELECT * FROM ta WHERE col7 < 0 AND col4 < -255""").collect().length == 4)
  }

  test("Select test 2 (WHERE)") {
    assert(sql( """SELECT * FROM ta WHERE col7 > 128""").count() == 3)
    assert(sql( """SELECT * FROM ta WHERE (col7 - 10 > 128) AND col1 = ' p255 '""").collect().length == 1)
    assert(sql( """SELECT * FROM ta WHERE (col7  > 1) AND (col7 < 1)""").collect().length == 0)
    assert(sql( """SELECT * FROM ta WHERE (col7  > 1) OR (col7 < 1)""").collect().length == 13)
    assert(sql(
      """SELECT * FROM ta WHERE
        |((col7 = 1) AND (col1 < ' p255 ') AND (col1 > ' p255 ')) OR
        |((col7 = 2) AND (col1 < ' p255 ') AND (col1 > ' p255 '))
      """.stripMargin).collect().length == 0)
    assert(sql(
      """SELECT * FROM ta WHERE
        |((col7 = 1) AND (col3 < 128) AND (col3 > 128)) OR
        |((col7 = 2) AND (col3 < 127) AND (col3 > 127))
      """.stripMargin).collect().length == 0)
  }

  test("Select test 3 (ORDER BY)") {
    val result = sql( """SELECT col1, col7 FROM ta ORDER BY col7 DESC""").collect()
    val sortedResult = result.sortWith(
      (r1, r2) => r1(1).asInstanceOf[Int] > r2(1).asInstanceOf[Int])
    for ((r1, r2) <- result zip sortedResult) {
      assert(r1.equals(r2))
    }
  }

  test("Select test 4 (join)") {
    val enabled = sqlContext.getConf("spark.sql.crossJoin.enabled")
    sqlContext.setConf("spark.sql.crossJoin.enabled", "true")
    assert(sql( """SELECT ta.col2 FROM ta join tb on ta.col4=tb.col7""").collect().length == 2)
    assert(sql( """SELECT * FROM ta FULL OUTER JOIN tb WHERE tb.col7 = 1""").collect().length == 14)
    assert(sql( """SELECT * FROM ta LEFT JOIN tb WHERE tb.col7 = 1""").collect().length == 14)
    assert(sql( """SELECT * FROM ta RIGHT JOIN tb WHERE tb.col7 = 1""").collect().length == 14)
    sqlContext.setConf("spark.sql.crossJoin.enabled", enabled)
  }

  // alter table is not supported for now
  ignore("Alter Add column and Alter Drop column") {
    assert(sql( """SELECT * FROM ta""").collect()(0).length == 7)
    sql( """ALTER TABLE ta ADD col8 STRING MAPPED BY (col8 = cf1.cf13)""")
    assert(sql( """SELECT * FROM ta""").collect()(0).length == 8)
    sql( """ALTER TABLE ta DROP col8""")
    assert(sql( """SELECT * FROM ta""").collect()(0).length == 7)
  }
}
