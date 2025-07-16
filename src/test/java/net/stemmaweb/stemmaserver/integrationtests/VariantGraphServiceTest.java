package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

public class VariantGraphServiceTest {
    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private final GraphDatabaseService db = dbServiceProvider.getDatabase();
    private String traditionId;
    private String userId;

    public VariantGraphServiceTest() throws IOException {
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

    // public void sectionInTraditionTest()

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(traditionId, tx);
            assertNotNull(startNode);
            assertEquals("#START#", startNode.getProperty("text"));
            assertEquals(true, startNode.getProperty("is_start"));
            tx.close();
        }
    }

    @Test
    public void getEndNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node endNode = VariantGraphService.getEndNode(traditionId, tx);
            assertNotNull(endNode);
            assertEquals("#END#", endNode.getProperty("text"));
            assertEquals(true, endNode.getProperty("is_end"));
            tx.close();
        }
    }

    @Test
    public void getSectionNodesTest(Transaction tx) {
        Node tradNode = tx.getNodeByElementId(traditionId);
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(tradNode, tx);
        assertEquals(1, sectionNodes.size());
        try {
            assertTrue(sectionNodes.get(0).hasLabel(Label.label("SECTION")));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    public void getTraditionNodeTest(Transaction tx) throws Exception {
        Node foundTradition = VariantGraphService.getTraditionNode(traditionId, tx);
        assertNotNull(foundTradition);
        // Now by section node
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(foundTradition, tx);
        assertEquals(1, sectionNodes.size());
        assertEquals(foundTradition, VariantGraphService.getSectionTraditionNode(sectionNodes.get(0), tx));
    }

    @Test
    public void normalizeGraphTest() throws KernelException {
        String newTradId = Util.getValueFromJson(
                Util.createTraditionDirectly("Tradition", "LR", userId,
                        "src/TestFiles/globalrel_test.xml", "stemmaweb"),
                "tradId"
        );
        ArrayList<Node> sections = null;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = tx.getNodeByElementId(traditionId);
            sections = VariantGraphService.getSectionNodes(tradNode, tx);
            HashMap<Node,Node> representatives = VariantGraphService.normalizeGraph(sections.get(0), List.of("collated"), tx);
            for (Node n : representatives.keySet()) {
                // If it is represented by itself, it should have an NSEQUENCE both in and out; if not, not.
                if (!n.hasProperty("is_end"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(Direction.OUTGOING, ERelations.NSEQUENCE));
                if (!n.hasProperty("is_start"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(Direction.INCOMING, ERelations.NSEQUENCE));
                // If it's at rank 6, it should have a REPRESENTS link
                if (n.getProperty("rank").equals(6L)) {
                    Direction d = n.getProperty("text").equals("weljellensä") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(d, ERelations.REPRESENTS));
                } else if (n.getProperty("rank").equals(9L)) {
                    Direction d = n.getProperty("text").equals("Hämehen") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(d, ERelations.REPRESENTS));
                }
            }
            tx.commit();
        } catch (Exception e) {
            fail();
        }

        // Now clear the normalization and make sure we didn't fail.
        try (Transaction tx = db.beginTx()) {
            VariantGraphService.clearNormalization(sections.get(0), tx);
            assertTrue(tx.getAllRelationships().stream().noneMatch(x -> x.isType(ERelations.NSEQUENCE)));
            assertTrue(tx.getAllRelationships().stream().noneMatch(x -> x.isType(ERelations.REPRESENTS)));
            tx.commit();
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void calculateMajorityTest() throws KernelException {
        String newTradId = Util.getValueFromJson(
                Util.createTraditionDirectly("Tradition", "LR", userId,
                        "src/TestFiles/globalrel_test.xml", "stemmaweb"),
                "tradId"
        );
        String expectedMajority;
        ArrayList<Node> sections = null;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = tx.getNodeByElementId(newTradId);
            sections = VariantGraphService.getSectionNodes(tradNode, tx);
            expectedMajority = "sanoi herra Heinärickus Erjkillen weljellensä Läckämme Hämehen maallen";
            List<Node> majorityReadings = VariantGraphService.calculateMajorityText(sections.get(0), tx);
            List<String> words = majorityReadings.stream()
                    .filter(x -> !x.hasProperty("is_start") && !x.hasProperty("is_end"))
                    .map(x -> x.getProperty("text").toString())
                    .collect(Collectors.toList());
            assertEquals(expectedMajority, String.join(" ", words));
            tx.commit();
        } catch (Exception e) {
            fail();
        }

        // Now lemmatize some smaller readings, normalize, and make sure the majority text adjusts
        expectedMajority = "sanoi herra Heinäricki Erjkillen weliellensä Läckämme Hämehen maallen";
        try (Transaction tx = db.beginTx()) {
            // Lemmatise a minority reading
            Node n = tx.findNode(Nodes.READING, "text", "weliellensä");
            assertNotNull(n);
            n.setProperty("is_lemma", true);
            // Collate two readings so that together they outweigh the otherwise-majority
            Node n1 = tx.findNode(Nodes.READING, "text", "Heinäricki");
            Node n2 = tx.findNode(Nodes.READING, "text", "Henärickus");
            RelationModel rm = new RelationModel();
            rm.setSource(n1.getElementId());
            rm.setTarget(n2.getElementId());
            rm.setType("collated");
            rm.setScope("local");
            Relation relRest = new Relation(newTradId, net.stemmaweb.Util.getTraditionNode(traditionId));
            Response r = relRest.create(rm);
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            VariantGraphService.normalizeGraph(sections.get(0), List.of("collated"), tx); // TODO not sure this has an effect??
            List<Node> majorityReadings = VariantGraphService.calculateMajorityText(sections.get(0), tx);
            List<String> words = majorityReadings.stream()
                    .filter(x -> !x.hasProperty("is_start") && !x.hasProperty("is_end"))
                    .map(x -> x.getProperty("text").toString())
                    .collect(Collectors.toList());
            assertEquals(expectedMajority, String.join(" ", words));
            tx.commit();
        } catch (Exception e) {
            fail();
        }
    }

    // clearMajorityTest()

    // returnEntireTraditionTest()

    // returnTraditionSectionTest()

    // returnTraditionRelationsTest()

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
