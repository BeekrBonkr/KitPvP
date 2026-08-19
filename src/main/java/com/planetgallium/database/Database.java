package com.planetgallium.database;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Database {

    private DataSource dataSource;
    private boolean usingSQLite;
    private String fallbackSQLitePath = "storage.db";
    private final Map<String, Table> tables;

    public Database(String localDatabaseFileName) {
        this("none", -1, localDatabaseFileName, "none", "none");
    }

    /** True when the connection in use is the local SQLite file rather than MySQL. */
    public boolean isUsingSQLite() { return usingSQLite; }

    public Database(String host, int port, String database, String username, String password) {
        this(host, port, database, username, password, "storage.db");
    }

    public Database(String host, int port, String database, String username, String password,
                    String fallbackSQLitePath) {
        this.tables = new HashMap<>();
        this.fallbackSQLitePath = fallbackSQLitePath;

        if (!host.equals("none")) {
            setupMySQL(host, port, database, username, password);
        } else {
            setupSQLite(database);
        }
    }

    private void setupMySQL(String host, int port, String database, String username, String password) {
        try {
            MysqlDataSource dataSource = new MysqlConnectionPoolDataSource(); // new MysqlDataSource();
            dataSource.setServerName(host);
            dataSource.setPortNumber(port);
            dataSource.setCreateDatabaseIfNotExist(true); // idk
            dataSource.setDatabaseName(database);
            dataSource.setUser(username);
            dataSource.setPassword(password);

            this.dataSource = dataSource;
            this.usingSQLite = false;

            testDatabaseConnection();
        } catch (SQLException exception) {
            System.out.println("[Database] Connection to MySQL database failed (" + exception.getMessage() +
                    "). Falling back to the local SQLite file.");
            // the same file the plugin would have used had SQLite been configured,
            // so a temporary MySQL outage does not start a second stats database
            setupSQLite(fallbackSQLitePath);
        }
    }

    private void setupSQLite(String databaseFileName) {
        java.io.File databaseFile = new java.io.File(databaseFileName);
        if (databaseFile.getParentFile() != null) {
            databaseFile.getParentFile().mkdirs();
        }

        SQLiteDataSource sqLiteDataSource = new SQLiteDataSource();
        sqLiteDataSource.setUrl("jdbc:sqlite:" + databaseFile.getPath());

        this.dataSource = sqLiteDataSource;
        this.usingSQLite = true;
    }

    private void testDatabaseConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(1)) {
                throw new SQLException("Could not establish database connection");
            }
        }

    }

    public Table createTable(String tableName, Record masterRecord) {
        Table table = new Table(dataSource, tableName, masterRecord);
        tables.put(tableName, table);
        return table;
    }

    public void deleteTable(String tableName) {
        Table table = tables.get(tableName);
        if (table == null) {
            System.out.printf("[Database] Failed to delete table %s; table not found\n", tableName);
            return;
        }

        String deleteTableQuery = Table.DELETE_TABLE_QUERY.replace("{table_name}", tableName);
        try (Connection connection = dataSource.getConnection() ;
             PreparedStatement statement = connection.prepareStatement(deleteTableQuery)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
            return;
        }

        // forget the handle too, or later lookups keep finding a table that is gone
        tables.remove(tableName);
    }

    public Table getTable(String tableName) {
        return tables.get(tableName);
    }

    public Map<String, Table> getTables() { return tables; }

}
