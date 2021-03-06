/*
 * Copyright 2011 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.test.jdbc.misc;

import java.util.Map;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;
import org.lealone.hbase.util.HBaseUtils;
import org.lealone.test.jdbc.TestBase;

public class RegionSplitTest extends TestBase {

    @Test
    public void run() throws Exception {
        stmt.executeUpdate("DROP TABLE IF EXISTS RegionSplitTest");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS RegionSplitTest (id int primary key, name varchar(500))");

        int size = 50;
        for (int i = 1; i < size; i++)
            stmt.executeUpdate("INSERT INTO RegionSplitTest(id, name) VALUES(" + i + ", 'a1')");

        String tableName = "RegionSplitTest".toUpperCase();

        HBaseAdmin admin = HBaseUtils.getHBaseAdmin();
        //admin.getConnection().clearRegionCache();
        admin.split(Bytes.toBytes(tableName), Bytes.toBytes(10));

        for (int i = 1; i < size; i++)
            stmt.executeUpdate("INSERT INTO RegionSplitTest(id, name) VALUES(" + i + ", 'a1')");

        //admin.getConnection().clearRegionCache();
        Thread.sleep(2000);
        admin.split(Bytes.toBytes(tableName), Bytes.toBytes(30));

        sql = "select id, name from RegionSplitTest";
        printResultSet();

        for (int i = 1; i < size; i++)
            stmt.executeUpdate("INSERT INTO RegionSplitTest(id, name) VALUES(" + i + ", 'a1')");

        //admin.getConnection().clearRegionCache();
        Thread.sleep(2000);
        admin.split(Bytes.toBytes(tableName), Bytes.toBytes(40));

        sql = "select id, name from RegionSplitTest";
        printResultSet();
    }

    void closeRegionWithEncodedRegionName(String tableName) throws Exception {
        printRegions(tableName);

        HBaseAdmin admin = HBaseUtils.getHBaseAdmin();
        //admin.getConnection().clearRegionCache();
        //admin.split(Bytes.toBytes(tableName), Bytes.toBytes(10));
        HTable t = new HTable(HBaseConfiguration.create(), tableName.toUpperCase());
        for (Map.Entry<HRegionInfo, ServerName> e : t.getRegionLocations().entrySet()) {
            HRegionInfo info = e.getKey();
            System.out.println("info.getEncodedName()=" + info.getEncodedName());
            ServerName server = e.getValue();

            System.out.println("HRegionInfo = " + info.getRegionNameAsString());
            System.out.println("ServerName = " + server);
            System.out.println();

            //admin.closeRegion(server, info);
            admin.closeRegionWithEncodedRegionName(info.getEncodedName(), server.getServerName());
            break;
        }
        t.close();
    }
}
