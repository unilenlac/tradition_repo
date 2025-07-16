package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import javax.ws.rs.core.Response;

import net.stemmaweb.services.Database;
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
import org.neo4j.graphdb.Transaction;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    private String traditionId;
    private String userId;

    private final GraphDatabaseService db = Database.getInstance().session;

    public DatabaseServiceTest() throws IOException {
    }

    @Before
    public void setUp() throws Exception {

        userId = "admin@example.org";
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
        try (Transaction tx = db.beginTx()) {
            Node tradition = VariantGraphService.getTraditionNode(traditionId, tx);
            ArrayList<Node> witnesses = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS, tx);
            assertEquals(4, witnesses.size()); // 3 if the archetype W is excluded, 4 with it. Actually the base can't make the difference
        }
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

    }
}
