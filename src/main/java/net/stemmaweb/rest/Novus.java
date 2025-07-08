package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.Util.GetTraditionFunction;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.services.DatabaseService;
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
    GraphDatabaseService db;
    public Novus(String tradId, GetTraditionFunction<Transaction, Node> getNodeFunction, GraphDatabaseService db) throws Exception {
        // Constructor can be used for initialization if needed
        this.tradId = tradId;
        this.getTraditionNode = getNodeFunction;
        this.db = db;
    }

    @GET
    @Path("/reading")
    @Produces("application/json; charset=utf-8")
    public Response getReading() throws Exception {
        // This method should return a JSON representation of readings.
        // The implementation is not provided in the original code snippet.
        ReadingModel ReadingNode;

        try (Transaction tx = db.beginTx()) {
            TraditionModel metadata = new TraditionModel(getTraditionNode.apply(tx));
            Node newNode = tx.createNode(Nodes.READING);
            ReadingNode = new ReadingModel(newNode, tx);
            // tx.commit();
        }

        return Response.ok(ReadingNode).build(); // Placeholder for actual implementation
    }
}
