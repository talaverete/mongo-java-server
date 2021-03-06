package de.bwaldvogel;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

import de.bwaldvogel.mongo.MongoBackend;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.ReadOnlyProxy;

public abstract class AbstractReadOnlyProxyTest {
    private Mongo readOnlyClient;
    private MongoServer mongoServer;
    private MongoServer writeableServer;
    private Mongo writeClient;

    protected abstract MongoBackend createBackend() throws Exception;

    @Before
    public void setUp() throws Exception {
        MongoBackend mongoBackend = createBackend();
        writeableServer = new MongoServer(mongoBackend);
        writeClient = new MongoClient(new ServerAddress(writeableServer.bind()));

        mongoServer = new MongoServer(new ReadOnlyProxy(mongoBackend));
        readOnlyClient = new MongoClient(new ServerAddress(mongoServer.bind()));
    }

    @After
    public void tearDown() {
        writeClient.close();
        readOnlyClient.close();
        mongoServer.shutdownNow();
        writeableServer.shutdownNow();
    }

    @Test
    public void testMaxBsonSize() throws Exception {
        int maxBsonObjectSize = readOnlyClient.getMaxBsonObjectSize();
        assertThat(maxBsonObjectSize).isEqualTo(16777216);
    }

    @Test
    public void testServerStatus() throws Exception {
        CommandResult serverStatus = readOnlyClient.getDB("admin").command("serverStatus");
        serverStatus.throwOnError();
    }

    @Test
    public void testCurrentOperations() throws Exception {
        DBObject currentOperations = readOnlyClient.getDB("admin").getCollection("$cmd.sys.inprog").findOne();
        assertThat(currentOperations).isNotNull();
    }

    @Test
    public void testStats() throws Exception {
        CommandResult stats = readOnlyClient.getDB("testdb").getStats();
        stats.throwOnError();
        assertThat(((Number) stats.get("objects")).longValue()).isZero();
    }

    @Test
    public void testListDatabaseNames() throws Exception {
        assertThat(readOnlyClient.getDatabaseNames()).isEmpty();
        writeClient.getDB("testdb").getCollection("testcollection").insert(new BasicDBObject());
        assertThat(readOnlyClient.getDatabaseNames()).containsExactly("testdb");
        writeClient.getDB("bar").getCollection("testcollection").insert(new BasicDBObject());
        assertThat(readOnlyClient.getDatabaseNames()).containsExactly("bar", "testdb");
    }

    @Test
    public void testIllegalCommand() throws Exception {
        try {
            readOnlyClient.getDB("testdb").command("foo").throwOnError();
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getMessage()).contains("no such cmd");
        }

        try {
            readOnlyClient.getDB("bar").command("foo").throwOnError();
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getMessage()).contains("no such cmd");
        }
    }

    @Test
    public void testQuery() throws Exception {
        DBCollection collection = readOnlyClient.getDB("testdb").getCollection("testcollection");
        DBObject obj = collection.findOne(new BasicDBObject("_id", 1));
        assertThat(obj).isNull();
        assertThat(collection.count()).isEqualTo(0);
    }

    @Test
    public void testDistinctQuery() {
        DBCollection collection = writeClient.getDB("testdb").getCollection("testcollection");
        collection.insert(new BasicDBObject("n", 1));
        collection.insert(new BasicDBObject("n", 2));
        collection.insert(new BasicDBObject("n", 1));
        collection = readOnlyClient.getDB("testdb").getCollection("testcollection");
        assertThat(collection.distinct("n")).containsExactly(1, 2);
    }

    @Test
    public void testInsert() throws Exception {
        DBCollection collection = readOnlyClient.getDB("testdb").getCollection("testcollection");
        assertThat(collection.count()).isEqualTo(0);
        try {
            collection.insert(new BasicDBObject());
            fail("exception expected");
        } catch (MongoException e) {
            // okay
        }
    }

    @Test
    public void testUpdate() throws Exception {
        DBCollection collection = readOnlyClient.getDB("testdb").getCollection("testcollection");
        BasicDBObject object = new BasicDBObject("_id", 1);
        BasicDBObject newObject = new BasicDBObject("_id", 1);
        try {
            collection.update(object, newObject);
            fail("MongoException expected");
        } catch (MongoException e) {
            // okay
        }
    }

    @Test
    public void testUpsert() throws Exception {
        DBCollection collection = readOnlyClient.getDB("testdb").getCollection("testcollection");

        BasicDBObject object = new BasicDBObject("_id", 1);
        BasicDBObject newObject = new BasicDBObject("_id", 1);
        try {
            collection.update(object, newObject, true, false);
            fail("MongoException expected");
        } catch (MongoException e) {
            // okay
        }
    }

    @Test
    public void testDropDatabase() throws Exception {
        try {
            readOnlyClient.dropDatabase("testdb");
            fail("MongoException expected");
        } catch (MongoException e) {
            // okay
        }
    }

    @Test
    public void testDropCollection() throws Exception {
        DBCollection collection = readOnlyClient.getDB("testdb").getCollection("foo");
        try {
            collection.drop();
            fail("MongoException expected");
        } catch (MongoException e) {
            // okay
        }
    }

}
