package net.stemmaweb.rest;

import net.stemmaweb.Util;
import net.stemmaweb.Util.GetTraditionFunction;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.services.Database;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Novus is a RESTful sandbox service.
 */

public class Novus {
    private final String tradId;
    GetTraditionFunction<Transaction, Node> getTraditionNode;
    Database db = Database.getInstance();
    Node node;// Assuming Database is a singleton that provides access to the GraphDatabaseService
    public Novus(String tradId, Node node) {
        // Constructor can be used for initialization if needed
        this.tradId = tradId;
        this.node = node;
        // this.getTraditionNode = getNodeFunction;
    }

    @GET
    @Path("/reading")
    @Produces("application/json; charset=utf-8")
    public Ordo getReading() throws Exception {
        // This method should return a JSON representation of readings.
        // The implementation is not provided in the original code snippet.
        ReadingModel ReadingNode;

        try (Transaction tx = db.session.beginTx()) {
            GetTraditionFunction<Transaction, Node> getTraditionNode = Util.getTraditionNode(tradId);
            // TraditionModel metadata = new TraditionModel(getTraditionNode.apply(tx), tx);
            // Node newNode = tx.createNode(Nodes.READING);
            // ReadingNode = new ReadingModel(newNode, tx);
            // tx.commit();
            return new Ordo(tradId);
        }

        // return Response.ok(ReadingNode).build(); // Placeholder for actual implementation
    }
}
