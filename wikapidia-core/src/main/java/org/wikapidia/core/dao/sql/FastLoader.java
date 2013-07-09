package org.wikapidia.core.dao.sql;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.quote.AlwaysQuoteMode;
import org.supercsv.quote.QuoteMode;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.utils.WpIOUtils;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Bulk loads data in batch form to speed up insertions.
 *
 * Right now this saves the data in a CSV and then loads it from the CSV.
 * While this is faster for databases that support loading from files, it
 * requires custom coding for each database and also requires that the
 * program is running on the same server as the database.
 *
 * TODO: improve batch performance for non-csv-loading dbs by using prepared
 * statements and batch execution.
 *
 * @author Shilad Sen
 */
public class FastLoader {
    static final Logger LOG = Logger.getLogger(FastLoader.class.getName());

    // these files could get huge, so be careful not to put them in /tmp which might be small.
    static final File DEFAULT_TMP_DIR = new File(".tmp");

    private final SQLDialect dialect;
    private String schema;

    private TableField[] fields;
    private DataSource ds;
    private Table table;
    private File csvFile = null;
    private CsvListWriter writer = null;


    public FastLoader(DataSource ds, TableField[] fields) throws DaoException {
        this(ds, fields, DEFAULT_TMP_DIR);
    }

    public FastLoader(DataSource ds, TableField[] fields, File tmpDir) throws DaoException {
        this.ds = ds;
        this.table = fields[0].getTable();
        this.fields = fields;
        Connection cnx = null;
        try {
            cnx = ds.getConnection();
            this.dialect = JooqUtils.dialect(cnx);
            if (tmpDir.exists() && !tmpDir.isDirectory()) {
                FileUtils.forceDelete(tmpDir);
            }
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            csvFile = File.createTempFile("table", ".csv", tmpDir);
            csvFile.deleteOnExit();
            LOG.info("creating tmp csv for " + table.getName() + " at " + csvFile.getAbsolutePath());
            CsvPreference pref = new CsvPreference.Builder(CsvPreference.STANDARD_PREFERENCE)
                    .useQuoteMode(new AlwaysQuoteMode())
                    .build();
            writer = new CsvListWriter(WpIOUtils.openWriter(csvFile), pref);
            String [] names = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                names[i] = fields[i].getName();
            }
            writer.writeHeader(names);
        } catch (IOException e) {
            throw new DaoException(e);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            AbstractSqlDao.quietlyCloseConn(cnx);
        }
    }

    /**
     * Sets the schema used to create the tables.
     * This must be called before inserting any records into the database.
     * @param sql If not-null, the schema has not been created and the loader
     *               MUST create the schema.
     */
    public void setSchema(String sql) {
        this.schema = sql;
        if (this.schema == null) {
            return;
        }
        this.schema = this.schema.trim();
        while (this.schema.endsWith(";")) {
            this.schema = this.schema.substring(0, this.schema.length() - 1);
            this.schema = this.schema.trim();
        }
        if (!this.schema.toLowerCase().startsWith("create table") ||
                !this.schema.toLowerCase().endsWith(")")) {
            throw new IllegalArgumentException("expect schema string to be a create table and that's all!");
        }
    }

    /**
     * Saves a value to the datastore.
     * @param values
     * @throws DaoException
     */
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    public void load(Object [] values) throws DaoException {
        if (values.length != fields.length) {
            throw new IllegalArgumentException();
        }
        try {
            synchronized (writer) {
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != null && values[i] instanceof Date) {
                        values[i] = DATE_FORMAT.format((Date)values[i]);
                    }
                }
                writer.write(values);
            }
        } catch (IOException e) {
            throw new DaoException(e);
        }
    }

    public void endLoad() throws DaoException {
        try {
            writer.close();
        } catch (IOException e) {
            throw new DaoException(e);
        }
        if (dialect == SQLDialect.H2) {
            loadH2();
        } else {
            loadGeneric();
        }
    }

    /**
     * TODO: generic loading could be faster by processing
     * the file in chunks with prepared statements. JOOQ loader
     * doesn't yet do this. BOO!
     *
     * @throws DaoException
     */
    private void loadGeneric() throws DaoException {
        Connection cnx = null;
        Reader reader = null;
        try {
            cnx = ds.getConnection();
            if (schema != null) {
                cnx.createStatement().execute(schema);
            }
            DSLContext create = DSL.using(cnx, dialect);
            reader = WpIOUtils.openReader(csvFile);
            create.loadInto(table)
                    .loadCSV(reader)
                    .fields(fields)
                    .execute();
        } catch (IOException e) {
            throw new DaoException(e);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            FileUtils.deleteQuietly(csvFile);
            AbstractSqlDao.quietlyCloseConn(cnx);
            if (reader != null) IOUtils.closeQuietly(reader);
        }
    }

    private void loadH2() throws DaoException {
        Connection cnx = null;
        try {
            cnx = ds.getConnection();
            Statement s = cnx.createStatement();
            String quotedPath = csvFile.getAbsolutePath().replace("'", "''");
            if (schema == null) {
                s.execute("INSERT INTO " + table.getName() +
                        " SELECT * " +
                        " FROM CSVREAD('" + quotedPath + "', null, 'charset=UTF-8')");
            } else {
                s.execute(schema + " AS SELECT * " +
                        " FROM CSVREAD('" + quotedPath + "', null, 'charset=UTF-8')");
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            FileUtils.deleteQuietly(csvFile);
            AbstractSqlDao.quietlyCloseConn(cnx);
        }
    }

    public String getSchema() {
        return schema;
    }
}
