/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.dbobject.index;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import org.lealone.dbobject.table.Column;
import org.lealone.dbobject.table.IndexColumn;
import org.lealone.dbobject.table.TableLink;
import org.lealone.engine.Constants;
import org.lealone.engine.Session;
import org.lealone.message.DbException;
import org.lealone.result.Row;
import org.lealone.result.SearchRow;
import org.lealone.result.SortOrder;
import org.lealone.util.New;
import org.lealone.util.StatementBuilder;
import org.lealone.value.Value;
import org.lealone.value.ValueNull;

/**
 * A linked index is a index for a linked (remote) table.
 * It is backed by an index on the remote table which is accessed over JDBC.
 */
public class LinkedIndex extends BaseIndex {

    private final TableLink link;
    private final String targetTableName;
    private long rowCount;

    public LinkedIndex(TableLink table, int id, IndexColumn[] columns, IndexType indexType) {
        initBaseIndex(table, id, null, columns, indexType);
        link = table;
        targetTableName = link.getQualifiedTable();
    }

    public String getCreateSQL() {
        return null;
    }

    public void close(Session session) {
        // nothing to do
    }

    private static boolean isNull(Value v) {
        return v == null || v == ValueNull.INSTANCE;
    }

    public void add(Session session, Row row) {
        ArrayList<Value> params = New.arrayList();
        StatementBuilder buff = new StatementBuilder("INSERT INTO ");
        buff.append(targetTableName).append(" VALUES(");
        for (int i = 0; i < row.getColumnCount(); i++) {
            Value v = row.getValue(i);
            buff.appendExceptFirst(", ");
            if (v == null) {
                buff.append("DEFAULT");
            } else if (isNull(v)) {
                buff.append("NULL");
            } else {
                buff.append('?');
                params.add(v);
            }
        }
        buff.append(')');
        String sql = buff.toString();
        try {
            link.execute(sql, params, true);
            rowCount++;
        } catch (Exception e) {
            throw TableLink.wrapException(sql, e);
        }
    }

    public Cursor find(Session session, SearchRow first, SearchRow last) {
        ArrayList<Value> params = New.arrayList();
        StatementBuilder buff = new StatementBuilder("SELECT * FROM ");
        buff.append(targetTableName).append(" T");
        for (int i = 0; first != null && i < first.getColumnCount(); i++) {
            Value v = first.getValue(i);
            if (v != null) {
                buff.appendOnlyFirst(" WHERE ");
                buff.appendExceptFirst(" AND ");
                Column col = table.getColumn(i);
                buff.append(col.getSQL());
                if (v == ValueNull.INSTANCE) {
                    buff.append(" IS NULL");
                } else {
                    buff.append(">=");
                    addParameter(buff, col);
                    params.add(v);
                }
            }
        }
        for (int i = 0; last != null && i < last.getColumnCount(); i++) {
            Value v = last.getValue(i);
            if (v != null) {
                buff.appendOnlyFirst(" WHERE ");
                buff.appendExceptFirst(" AND ");
                Column col = table.getColumn(i);
                buff.append(col.getSQL());
                if (v == ValueNull.INSTANCE) {
                    buff.append(" IS NULL");
                } else {
                    buff.append("<=");
                    addParameter(buff, col);
                    params.add(v);
                }
            }
        }
        String sql = buff.toString();
        try {
            PreparedStatement prep = link.execute(sql, params, false);
            ResultSet rs = prep.getResultSet();
            return new LinkedCursor(link, rs, session, sql, prep);
        } catch (Exception e) {
            throw TableLink.wrapException(sql, e);
        }
    }

    private void addParameter(StatementBuilder buff, Column col) {
        if (col.getType() == Value.STRING_FIXED && link.isOracle()) {
            // workaround for Oracle
            // create table test(id int primary key, name char(15));
            // insert into test values(1, 'Hello')
            // select * from test where name = ? -- where ? = "Hello" > no rows
            buff.append("CAST(? AS CHAR(").append(col.getPrecision()).append("))");
        } else {
            buff.append('?');
        }
    }

    public double getCost(Session session, int[] masks, SortOrder sortOrder) {
        return 100 + getCostRangeIndex(masks, rowCount + Constants.COST_ROW_OFFSET, sortOrder);
    }

    public void remove(Session session) {
        // nothing to do
    }

    public void truncate(Session session) {
        // nothing to do
    }

    public void checkRename() {
        throw DbException.getUnsupportedException("LINKED");
    }

    public boolean needRebuild() {
        return false;
    }

    public boolean canGetFirstOrLast() {
        return false;
    }

    public Cursor findFirstOrLast(Session session, boolean first) {
        // TODO optimization: could get the first or last value (in any case;
        // maybe not optimized)
        throw DbException.getUnsupportedException("LINKED");
    }

    public void remove(Session session, Row row) {
        ArrayList<Value> params = New.arrayList();
        StatementBuilder buff = new StatementBuilder("DELETE FROM ");
        buff.append(targetTableName).append(" WHERE ");
        for (int i = 0; i < row.getColumnCount(); i++) {
            buff.appendExceptFirst("AND ");
            Column col = table.getColumn(i);
            buff.append(col.getSQL());
            Value v = row.getValue(i);
            if (isNull(v)) {
                buff.append(" IS NULL ");
            } else {
                buff.append('=');
                addParameter(buff, col);
                params.add(v);
                buff.append(' ');
            }
        }
        String sql = buff.toString();
        try {
            PreparedStatement prep = link.execute(sql, params, false);
            int count = prep.executeUpdate();
            link.reusePreparedStatement(prep, sql);
            rowCount -= count;
        } catch (Exception e) {
            throw TableLink.wrapException(sql, e);
        }
    }

    /**
     * Update a row using a UPDATE statement. This method is to be called if the
     * emit updates option is enabled.
     *
     * @param oldRow the old data
     * @param newRow the new data
     */
    public void update(Row oldRow, Row newRow) {
        ArrayList<Value> params = New.arrayList();
        StatementBuilder buff = new StatementBuilder("UPDATE ");
        buff.append(targetTableName).append(" SET ");
        for (int i = 0; i < newRow.getColumnCount(); i++) {
            buff.appendExceptFirst(", ");
            buff.append(table.getColumn(i).getSQL()).append('=');
            Value v = newRow.getValue(i);
            if (v == null) {
                buff.append("DEFAULT");
            } else {
                buff.append('?');
                params.add(v);
            }
        }
        buff.append(" WHERE ");
        buff.resetCount();
        for (int i = 0; i < oldRow.getColumnCount(); i++) {
            Column col = table.getColumn(i);
            buff.appendExceptFirst(" AND ");
            buff.append(col.getSQL());
            Value v = oldRow.getValue(i);
            if (isNull(v)) {
                buff.append(" IS NULL");
            } else {
                buff.append('=');
                params.add(v);
                addParameter(buff, col);
            }
        }
        String sql = buff.toString();
        try {
            link.execute(sql, params, true);
        } catch (Exception e) {
            throw TableLink.wrapException(sql, e);
        }
    }

    public long getRowCount(Session session) {
        return rowCount;
    }

    public long getRowCountApproximation() {
        return rowCount;
    }

    public long getDiskSpaceUsed() {
        return 0;
    }
}
