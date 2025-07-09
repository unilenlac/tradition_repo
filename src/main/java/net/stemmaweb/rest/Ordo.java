package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.AnnotationModel;
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

public class Ordo {
    private final Database db;
    private final String tradId;

    public Ordo(String tradId) {
        // Constructor can be used for initialization if needed
        this.tradId = tradId;
        this.db = Database.getInstance(); // Assuming Database is a singleton that provides access to the GraphDatabaseService
    }

    @GET
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getTextOrdo() {
        // This method should return a JSON representation of text ordo.
        // The implementation is not provided in the original code snippet.
        TraditionModel tradModel;
        Node tradition;
        try (Transaction tx = this.db.session.beginTx()) {
            tradition = tx.findNode(Nodes.TRADITION, "id", tradId);
            tradModel = new TraditionModel(tradition, tx);
        }
        return Response.ok(tradModel).build();
    }
}
