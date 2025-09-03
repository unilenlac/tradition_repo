package net.stemmaweb.rest;

import static net.stemmaweb.Util.getTraditionNode;
import static net.stemmaweb.Util.jsonerror;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.StreamSupport;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.Util;
import net.stemmaweb.services.Database;
import net.stemmaweb.services.RelationService;
import org.neo4j.codegen.api.Throw;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.*;

import com.qmino.miredot.annotations.ReturnType;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.router.transaction.TransactionLookup;

/**
 * Module to handle the specification and definition of relation types that may exist on
 * this tradition.
 *
 * @author tla
 */

public class RelationType {
    private final GraphDatabaseService db = Database.getInstance().session;
    /**
     * The name of a type of reading relation.
     */
    private final String traditionId;
    private final String typeName;

    public RelationType(String tradId, String requestedType) {
        traditionId = tradId;
        typeName = requestedType;
    }

    /**
     * Gets the information for the given relation type name.
     *
     * @title Get relation type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("net.stemmaweb.model.RelationTypeModel")
    public Response getRelationType() {
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Node foundRelType = rtModel.lookup(VariantGraphService.getTraditionNode(traditionId, db.beginTx()));
        if (foundRelType == null) {
            return Response.noContent().build();
        }

        return Response.ok(new RelationTypeModel(foundRelType)).build();
    }

    static public RelationTypeModel getRelationTypeMethod(String typeName, Node tradNode){
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Node foundRelType = rtModel.lookup(tradNode);
        if (foundRelType == null) {
            return null;
        }

        return new RelationTypeModel(foundRelType);
    }

    /**
     * Creates or updates a relation type according to the specification given.
     *
     * @title Create / update relation type specification
     *
     * @param rtModel - a user specification
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success, if an existing type was updated
     * @statuscode 201 on success, if a new type was created
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = RelationTypeModel.class)
    public Response create(RelationTypeModel rtModel) {
        // Find any existing relation type on this tradition
        try (Transaction ctx = this.db.beginTx()) {
            Util.GetTraditionFunction<Transaction, Node> getTraditionNode = getTraditionNode(traditionId);
            Node traditionNode = getTraditionNode.apply(ctx);
            Node extantRelType = rtModel.lookup(traditionNode);
            // Node extantRelType = ctx.findNode(Nodes.RELATION_TYPE, "name", rtModel.getName());
            /*

            if (rtModel.getDefaultsettings() != null) {
                // This won't work if we also have an extant type of this name.
                if (extantRelType != null)
                    return Response.status(Response.Status.CONFLICT)
                            .entity(jsonerror("Cannot instantiate a default for a type that already exists")).build();
                Result result = ctx.execute("SHOW TRANSACTIONS");

                System.out.println("Active transactions:");
                while (result.hasNext()) {
                    Map<String, Object> row = result.next();
                    System.out.println("Transaction: " + row);
                }
                res = this.makeDefaultType(ctx);
                Result afterRes = ctx.execute("SHOW TRANSACTIONS");

                System.out.println("Active transactions:");
                while (afterRes.hasNext()) {
                    Map<String, Object> row = afterRes.next();
                    System.out.println("Transaction: " + row);
                }
                if (res.getStatus() == Response.Status.CREATED.getStatusCode()) {
                    // RelationService.linkRelationToTradition(traditionNode, rtModel.getName(), db);
                    // tx.commit();
                    System.out.println("Second transaction started");

                    Node relNode = ctx.findNode(Nodes.RELATION_TYPE, "name", rtModel.getName());
                    Node tradNode = ctx.findNode(Nodes.TRADITION, "id", traditionId);

                    System.out.println("Found nodes - relNode: " + (relNode != null) + ", tradNode: " + (tradNode != null));

                    if (relNode == null || tradNode == null) {
                        System.out.println("One or both nodes not found, skipping relationship creation");
                        return Response.serverError().entity(jsonerror("Required nodes not found")).build();
                    }

                    // Check if relationship already exists
                    boolean relationshipExists = false;
                    for (Relationship rel : tradNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE)) {
                        if (rel.getEndNode().equals(relNode)) {
                            relationshipExists = true;
                            System.out.println("Relationship already exists");
                            break;
                        }
                    }

                    if (!relationshipExists) {
                        System.out.println("Creating relationship between tradition and relation type");

                        // Use direct relationship creation instead of Cypher
                        Relationship rel = tradNode.createRelationshipTo(relNode, ERelations.HAS_RELATION_TYPE);
                        // System.out.println("Relationship created: " + rel.getType());

                        System.out.println("About to commit second transaction...");
                        // Check for any locks or constraints
                        try {
                            Result trResult = ctx.execute("SHOW TRANSACTIONS");

                            System.out.println("Active transactions:");
                            while (trResult.hasNext()) {
                                Map<String, Object> row = trResult.next();
                                System.out.println("Transaction: " + row);
                            }
                        } catch (Exception e) {
                            System.out.println("Could not retrieve constraints: " + e.getMessage());
                        }
                        ctx.commit();
                        System.out.println("Second transaction committed successfully");
                    } else {
                        System.out.println("Relationship already exists, no commit needed");
                    }

                    return res;
                }
            }
            */
            if (extantRelType != null) {
                extantRelType = rtModel.update(traditionNode, ctx);
                if (extantRelType != null)
                    return Response.ok().entity(rtModel).build();
            } else {
                extantRelType = rtModel.instantiate(traditionNode, ctx);
                traditionNode.createRelationshipTo(extantRelType, ERelations.HAS_RELATION_TYPE);
                ctx.commit();
                if (extantRelType != null)
                    return Response.status(Response.Status.CREATED).entity(rtModel).build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Response.serverError()
                .entity(jsonerror("Could not create or update relation type")).build();
    }

    static public RelationTypeModel create_relation_type(Node tradNode, String relType, Boolean defaultSetting, Transaction tx){
        // Find any existing relation type on this tradition
        // Response res = Response.ok().build();
        RelationTypeModel rtModel = new RelationTypeModel();
        rtModel.setName(relType);
        rtModel.setDefaultsettings(defaultSetting);
        try {
            // Node traditionNode = VariantGraphService.getTraditionNode(traditionId, tx);
            // Node traditionNode = tx.getNodeByElementId(traditionId);
            Node extantRelType = rtModel.lookup(tradNode);
            // Node extantRelType = tx.findNode(Nodes.RELATION_TYPE, "name", rtModel.getName());
            if (extantRelType != null) {
                extantRelType = rtModel.update(tradNode, tx);
                if (extantRelType != null)
                    return rtModel;
            } else {
                extantRelType = rtModel.instantiate(tradNode, tx);
                tradNode.createRelationshipTo(extantRelType, ERelations.HAS_RELATION_TYPE);
                // ctx.commit();
                if (extantRelType != null)
                    return rtModel;
            }
        }catch (Throwable t){
            t.printStackTrace();
        }
        return rtModel;
    }
    static public Boolean create_type(RelationTypeModel rtModel, Transaction tx, String traditionId){
        Boolean res = false;
        Node traditionNode = tx.findNode(Nodes.TRADITION, "id", traditionId);
        Node extantRelType = rtModel.lookup(traditionNode);

        // Were we asked for the secret Stemmaweb defaults?
        if (rtModel.getDefaultsettings() != null) {
            // This won't work if we also have an extant type of this name.
            if (extantRelType != null)
                return false;
            // Result result = tx.execute("SHOW TRANSACTIONS");
            // System.out.println("Active transactions:");
            // while (result.hasNext()) {
            //     Map<String, Object> row = result.next();
            //     System.out.println("Transaction: " + row);
            // }
            Node createdDefaultType = makeDefaultType(tx, traditionId, rtModel.getName());

            // RelationService.linkRelationToTradition(traditionNode, rtModel.getName(), db);

            // Check if relationship already exists
            boolean relationshipExists = false;
            for (Relationship rel : traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE)) {
                if (rel.getEndNode().equals(createdDefaultType)) {
                    relationshipExists = true;
                    System.out.println("Relationship already exists");
                    break;
                }
            }

            if (!relationshipExists) {
                // Use direct relationship creation instead of Cypher
                traditionNode.createRelationshipTo(createdDefaultType, ERelations.HAS_RELATION_TYPE);

            } else {
                System.out.println("Relationship already exists, no commit needed");
            }

            return true;

        }
        return res;
    }

    /**
     * Deletes the named relation type.
     *
     * @title Delete a relation type
     * @return A JSON RelationTypeModel of the deleted type
     * @statuscode 200 on success
     * @statuscode 404 if the specified type doesn't exist
     * @statuscode 409 if relations of the type still exist in the tradition
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = RelationTypeModel.class)
    public Response delete() {
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Node tradition = VariantGraphService.getTraditionNode(traditionId, db.beginTx());
        Node foundRelType = rtModel.lookup(tradition);
        if (foundRelType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try (Transaction tx = db.beginTx()) {
            // Do we have any relations that use this type?
//        	if (VariantGraphService.returnTraditionRelations(tradition).relationships().stream()
            if (StreamSupport.stream(VariantGraphService.returnTraditionRelations(tradition).relationships().spliterator(), false)
                    .anyMatch(x -> x.getProperty("type", "").equals(typeName)))
                return Response.status(Response.Status.CONFLICT)
                        .entity(jsonerror("Relations of this type still exist; please alter them then try again.")).build();

            // Then I guess we can delete it.
            foundRelType.getSingleRelationship(ERelations.HAS_RELATION_TYPE, Direction.INCOMING).delete();
            foundRelType.delete();
            tx.close();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        // Return the thing we deleted.
        return Response.ok(rtModel).build();
    }

    /**
     * Creates a relation type with the given name according to default values.
     * Method for use internally, logic intended for Stemmaweb backwards compatibility.
     *
     * @title Create a default relation type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success, if an existing type was updated
     * @statuscode 201 on success, if a new type was created
     * @statuscode 500 on failure, with an error report in JSON format
     */
    static Node makeDefaultType(Transaction tx, String traditionId, String typeName) {
        Map<String, String> defaultRelations = new HashMap<>() {{
            put("collated", "Internal use only");
            put("orthographic", "These are the same reading, neither unusually spelled.");
            put("punctuation", "These are the same reading apart from punctuation.");
            put("spelling", "These are the same reading, spelled differently.");
            put("grammatical", "These readings share a root (lemma), but have different parts of speech (morphologies).");
            put("lexical", "These readings share a part of speech (morphology), but have different roots (lemmata).");
            put("uncertain", "These readings are related, but a clear category cannot be assigned.");
            put("other", "These readings are related in a way not covered by the existing types.");
            put("transposition", "This is the same (or nearly the same) reading in a different location.");
            put("repetition", "This is a reading that was repeated in one or more witnesses.");
        }};
        // try (Transaction tx = db.beginTx()){

            Node tradNode = VariantGraphService.getTraditionNode(traditionId, tx);
            RelationTypeModel relType = new RelationTypeModel(typeName);
            // Does this already exist?
            Node extantRelType = relType.lookup(tradNode);
            if (extantRelType != null)
                throw new RuntimeException("Relation type " + typeName + " already exists in tradition " + traditionId);

            // If we don't have any settings for the requested name, use the settings for "other"
            String useType = typeName;
            if (!defaultRelations.containsKey(typeName)) useType = "other";

            relType.setDescription(defaultRelations.get(useType));
            // Set the bindlevel
            int bindlevel = 0; // orthographic, punctuation, uncertain, other
            switch (useType) {
                case "spelling":
                    bindlevel = 1;
                    break;
                case "grammatical":
                case "lexical":
                    bindlevel = 2;
                    break;
                case "collated":
                case "transposition":
                case "repetition":
                    bindlevel = 50;
                    break;
            }
            relType.setBindlevel(bindlevel);
            // Set the booleans
            relType.setIs_colocation(!(useType.equals("transposition") || useType.equals("repetition")));
            relType.setIs_weak(useType.equals("collated"));
            relType.setIs_transitive(!(useType.equals("uncertain") || useType.equals("other")
                    || useType.equals("repetition") || useType.equals("transposition")));
            relType.setIs_generalizable(!(useType.equals("collated")|| useType.equals("uncertain")
                    || useType.equals("other")));
            relType.setUse_regular(!useType.equals("orthographic"));
            // Create the node
            Node result = relType.instantiate(tradNode, tx);
            if (result == null){
                throw new RuntimeException("Could not create relation type: " + typeName);
            } else {
                // tx.commit();
                return result;
            }
        // }
    }
}
