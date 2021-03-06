package com.nyaruka.db.dev;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import com.nyaruka.db.DBException;
import com.nyaruka.db.Statement;

public class DevStatement implements Statement {

	public DevStatement(SQLiteConnection connection, SQLiteStatement st) {
		m_connection = connection;
		m_statement = st;
	}
	
	@Override
	public long columnLong(int index) {
		try {
			return m_statement.columnLong(index);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public String columnString(int index) {
		try {
			return m_statement.columnString(index);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public boolean step() {
		try {
			return m_statement.step();
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public int columnInt(int index) {
		try {
			return m_statement.columnInt(index);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public void bind(int index, String value) {
		try {
			m_statement.bind(index, value);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}		
	}

	@Override
	public void bind(int index, int value) {
			try {
			m_statement.bind(index, value);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}	
		
	}

	@Override
	public void bind(int index, long value) {
		try {
			m_statement.bind(index, value);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}	
	}

	@Override
	public void bindNull(int index) {
		try {
			m_statement.bindNull(index);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public int columnCount() {
		try {
			return m_statement.columnCount();
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public String getColumnName(int i) {
		try {
			return m_statement.getColumnName(i);
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public long executeInsert() {
		try {
			m_statement.step();
			return m_connection.getLastInsertId();
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}


	@Override
	public void execute() {
		try {
			m_statement.step();
		} catch (SQLiteException e) {
			throw new DBException(e);
		}
	}

	@Override
	public void executeUpdate() {
		execute();
	}

	@Override
	public void executeQuery() {
		execute();
	}
	
	private SQLiteConnection m_connection;
	private SQLiteStatement m_statement;
	

}
