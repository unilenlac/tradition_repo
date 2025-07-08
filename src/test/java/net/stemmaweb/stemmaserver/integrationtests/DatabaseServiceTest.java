package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import javax.ws.rs.core.Response;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
// import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    private String traditionId;
    private String userId;
	private DatabaseManagementService dbbuilder;

    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();

    private final GraphDatabaseService db = dbServiceProvider.getDatabase();

    public DatabaseServiceTest() throws IOException {
    }

    @Before
    public void setUp() throws Exception {

//      db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
    	// dbbuilder = new DatabaseManagementServiceBuilder(Path.of("")).build();
    	// dbbuilder.createDatabase("stemmatest");
    	// db = dbbuilder.database("stemmatest");
        userId = "simon";
        Util.setupTestDB(db);

        /*
         * load a tradition to the test DB, without Jersey
         */
        Response result = Util.createTraditionDirectly("Tradition", "LR", userId,
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
        /*
         * gets the generated id of the inserted tradition
         */
        traditionId = Util.getValueFromJson(result, "tradId");
    }

    @Test
    public void getRelatedTest() {
        Node tradition = VariantGraphService.getTraditionNode(traditionId, db.beginTx());
        ArrayList<Node> witnesses = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS, null);
        assertEquals(3, witnesses.size());
    }

    @Test
    public void userExistsTest() {
        assertTrue(DatabaseService.userExists(userId, db));
    }

    /*
     * Shut down the database
     */
    @After
    public void tearDown() {
        DatabaseManagementService service = dbServiceProvider.getManagementService();

        if (service != null) {
            service.shutdownDatabase(db.databaseName());
        }
    }
}
