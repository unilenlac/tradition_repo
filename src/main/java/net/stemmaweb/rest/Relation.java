package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.services.RelationService.returnRelationType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.Util.GetTraditionFunction;
import net.stemmaweb.services.*;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;



import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.RelationService.TransitiveRelationTraverser;

/**
 * Comprises all the api calls related to a relation.
 * can be called by using http://BASE_URL/relation
 * @author PSE FS 2015 Team2
 */

public class Relation {

    private final GraphDatabaseService db;
    private final String tradId;
    private static final String SCOPE_LOCAL = "local";
    private static final String SCOPE_SECTION = "section";
    private static final String SCOPE_TRADITION = "tradition";

    private final GetTraditionFunction<Transaction, Node> getTraditionNode;

    public Relation(String traditionId, GetTraditionFunction<Transaction, Node> getNodeFunction) {
        db = Database.getInstance().session;
        tradId = traditionId;
        getTraditionNode = getNodeFunction;
    }

    /**
     * Creates a new relation between the specified reading nodes.
     *
     * @title Create relation
     * @param relationModel - JSON structure of the relation to create
     * @return The relation(s) created, as well as any other readings in the graph that
     * had a relation set between them.
     * @statuscode 201 - on success
     * @statuscode 304 - if the specified relation type/scope already exists
     * @statuscode 400 - if the request has an invalid scope
     * @statuscode 409 - if the relationship cannot legally be created
     * @statuscode 500 - on failure, with JSON error message
     */
    // TODO make this an idempotent PUT call
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    
    public Response create(RelationModel relationModel) throws IOException {
        // Make sure a scope is set
        try (Transaction tx = db.beginTx()) {
            if (relationModel.getScope() == null) relationModel.setScope(SCOPE_LOCAL);
            String scope = relationModel.getScope();
            GraphModel relationChanges = null;
            if (scope.equals(SCOPE_TRADITION) || scope.equals(SCOPE_SECTION) || scope.equals(SCOPE_LOCAL)) {
                relationChanges = new GraphModel();

                Response response = create_local(relationModel, getTraditionNode.apply(tx), tx);
                if (Status.CREATED.getStatusCode() != response.getStatus()) {
                    return response;
                }
                GraphModel createResult = (GraphModel) response.getEntity();
                relationChanges.addReadings(createResult.getReadings());
                relationChanges.addRelations(createResult.getRelations());
                // Fish out the ID of the relationship that we explicitly created
                Optional<RelationModel> orm = createResult.getRelations().stream()
                        .filter(x -> x.getTarget().equals(relationModel.getTarget())
                                && x.getSource().equals(relationModel.getSource())).findFirst();
                assert (orm.isPresent());
                String thisRelId = orm.get().getId();
                if (!scope.equals(SCOPE_LOCAL)) {
                    Node startingPoint = getTraditionNode.apply(tx);
                    Boolean use_normal = returnRelationType(startingPoint, relationModel.getType(), tx).getUse_regular();
                    Node readingA = tx.getNodeByElementId(relationModel.getSource());
                    Node readingB = tx.getNodeByElementId(relationModel.getTarget());
                    if (scope.equals(SCOPE_SECTION))
                        startingPoint = tx.getNodeByElementId(String.valueOf(readingA.getProperty("section_id")));
                    Relationship thisRelation = tx.getRelationshipByElementId(thisRelId);

                    // Get all the readings that belong to our tradition or section
                    Iterable<Node> tradReadings = VariantGraphService.returnEntireTradition(startingPoint, tx).nodes();
                    // Pick out the ones that share the readingA text
                    Function<Node, Object> nodefilter = (n) -> use_normal && n.hasProperty("normal_form")
                            ? n.getProperty("normal_form") : (n.hasProperty("text") ? n.getProperty("text") : "");
                    //                    HashSet<Node> ourA = tradReadings.stream()
                    HashSet<Node> ourA = StreamSupport.stream(tradReadings.spliterator(), false)
                            .filter(x -> nodefilter.apply(x).equals(nodefilter.apply(readingA)) && !x.equals(readingA))
                            .collect(Collectors.toCollection(HashSet::new));
                    HashMap<String, HashSet<Long>> ranks = new HashMap<>();
                    for (Node cur_node : ourA) {
                        String node_id = cur_node.getElementId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;
                        HashSet<Long> cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
                        cur_set.add(Long.valueOf(node_rank));
                        ranks.putIfAbsent(key, cur_set);
                    }

                    // Pick out the ones that share the readingB text
                    // HashSet<Node> ourB = tradReadings.stream().filter(x -> x.hasProperty("text")
                    HashSet<Node> ourB = StreamSupport.stream(tradReadings.spliterator(), false).filter(x -> x.hasProperty("text")
                                    && nodefilter.apply(x).equals(nodefilter.apply(readingB)) && !x.equals(readingB))
                            .collect(Collectors.toCollection(HashSet::new));
                    RelationModel userel;
                    for (Node cur_node : ourB) {
                        String node_id = cur_node.getElementId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;

                        HashSet cur_set = ranks.get(key);
                        if (cur_set != null) {
                            for (Object id : cur_set) {
                                userel = new RelationModel(thisRelation, tx);
                                userel.setSource(Long.toString((Long) id));
                                userel.setTarget(node_id);
                                response = create_local(userel, getTraditionNode.apply(tx), tx);
                                if (Status.NOT_MODIFIED.getStatusCode() != response.getStatus()) {
                                    if (Status.CREATED.getStatusCode() == response.getStatus()) {
                                        createResult = (GraphModel) response.getEntity();
                                        relationChanges.addReadings(createResult.getReadings());
                                        relationChanges.addRelations(createResult.getRelations());
                                    }  // This is a best-effort operation, so ignore failures
                                }
                            }
                        }
                    }
                }
            }
            tx.commit();
            return Response.status(Status.CREATED).entity(relationChanges).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
            // return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
        }
    }

    static public Response create_relation_model(RelationModel relationModel, Node tradNode, Transaction tx) throws Exception {
        // Make sure a scope is set
        try {
            if (relationModel.getScope() == null) relationModel.setScope(SCOPE_LOCAL);
            String scope = relationModel.getScope();
            GraphModel relationChanges = null;
            if (scope.equals(SCOPE_TRADITION) || scope.equals(SCOPE_SECTION) || scope.equals(SCOPE_LOCAL)) {
                relationChanges = new GraphModel();

                Response response = create_local(relationModel, tradNode, tx);
                if (Status.CREATED.getStatusCode() != response.getStatus()) {
                    return response;
                }
                GraphModel createResult = (GraphModel) response.getEntity();
                relationChanges.addReadings(createResult.getReadings());
                relationChanges.addRelations(createResult.getRelations());
                // Fish out the ID of the relationship that we explicitly created
                Optional<RelationModel> orm = createResult.getRelations().stream()
                        .filter(x -> x.getTarget().equals(relationModel.getTarget())
                                && x.getSource().equals(relationModel.getSource())).findFirst();
                assert (orm.isPresent());
                String thisRelId = orm.get().getId();
                if (!scope.equals(SCOPE_LOCAL)) {
                    Node startingPoint = tradNode;
                    Boolean use_normal = returnRelationType(startingPoint, relationModel.getType(), tx).getUse_regular();
                    Node readingA = tx.getNodeByElementId(relationModel.getSource());
                    Node readingB = tx.getNodeByElementId(relationModel.getTarget());
                    if (scope.equals(SCOPE_SECTION))
                        startingPoint = tx.getNodeByElementId(String.valueOf(readingA.getProperty("section_id")));
                    Relationship thisRelation = tx.getRelationshipByElementId(thisRelId);

                    // Get all the readings that belong to our tradition or section
                    Iterable<Node> tradReadings = VariantGraphService.returnEntireTradition(startingPoint, tx).nodes();
                    // Pick out the ones that share the readingA text
                    Function<Node, Object> nodefilter = (n) -> use_normal && n.hasProperty("normal_form")
                            ? n.getProperty("normal_form") : (n.hasProperty("text") ? n.getProperty("text") : "");
                    //                    HashSet<Node> ourA = tradReadings.stream()
                    HashSet<Node> ourA = StreamSupport.stream(tradReadings.spliterator(), false)
                            .filter(x -> nodefilter.apply(x).equals(nodefilter.apply(readingA)) && !x.equals(readingA))
                            .collect(Collectors.toCollection(HashSet::new));
                    HashMap<String, HashSet<Long>> ranks = new HashMap<>();
                    for (Node cur_node : ourA) {
                        String node_id = cur_node.getElementId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;
                        HashSet<Long> cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
                        cur_set.add(Long.valueOf(node_rank));
                        ranks.putIfAbsent(key, cur_set);
                    }

                    // Pick out the ones that share the readingB text
                    // HashSet<Node> ourB = tradReadings.stream().filter(x -> x.hasProperty("text")
                    HashSet<Node> ourB = StreamSupport.stream(tradReadings.spliterator(), false).filter(x -> x.hasProperty("text")
                                    && nodefilter.apply(x).equals(nodefilter.apply(readingB)) && !x.equals(readingB))
                            .collect(Collectors.toCollection(HashSet::new));
                    RelationModel userel;
                    for (Node cur_node : ourB) {
                        String node_id = cur_node.getElementId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;

                        HashSet cur_set = ranks.get(key);
                        if (cur_set != null) {
                            for (Object id : cur_set) {
                                userel = new RelationModel(thisRelation, tx);
                                userel.setSource(Long.toString((Long) id));
                                userel.setTarget(node_id);
                                response = create_local(userel, tradNode, tx);
                                if (Status.NOT_MODIFIED.getStatusCode() != response.getStatus()) {
                                    if (Status.CREATED.getStatusCode() == response.getStatus()) {
                                        createResult = (GraphModel) response.getEntity();
                                        relationChanges.addReadings(createResult.getReadings());
                                        relationChanges.addRelations(createResult.getRelations());
                                    }  // This is a best-effort operation, so ignore failures
                                }
                            }
                        }
                    }
                }
            }
            // tx.commit();
            return Response.status(Status.CREATED).entity(relationChanges).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
            // return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
        }
    }

    // Create a relation; return the relation created as well as any reading nodes whose
    // properties (e.g. rank) have changed.
    private static Response create_local(RelationModel relationModel, Node tradNode, Transaction tx) {
        GraphModel readingsAndRelationModel;
        try {
            /*
             * Currently search by id search, because is much faster by measurement. Because
             * the id search is O(n) just go through all ids without care. And the
             *
             */
            Node readingA = tx.getNodeByElementId(relationModel.getSource());
            Node readingB = tx.getNodeByElementId(relationModel.getTarget());

            Node ourSection = tx.getNodeByElementId(readingA.getProperty("section_id").toString());
            Node ourTradition = ourSection.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
            if (!ourTradition.getProperty("id").equals(tradNode.getProperty("id")))
                return Response.status(Status.CONFLICT)
                    .entity(jsonerror("The specified readings do not belong to the specified tradition"))
                    .build();

            if (!readingA.getProperty("section_id").equals(readingB.getProperty("section_id")))
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Cannot create relation across tradition sections"))
                        .build();

            if (isMetaReading(readingA) || isMetaReading(readingB))
                return Response.status(Status.CONFLICT)
                    .entity(jsonerror("Cannot set relation on a meta reading"))
                    .build();

            // Get, or create implicitly, the relation type node for the given type.
            // Node tradNode = getTraditionNode.apply(tx);
            RelationTypeModel rmodel = returnRelationType(tradNode, relationModel.getType(), tx);
            if(rmodel == null) rmodel = new RelationTypeModel(relationModel.getType());

            // Check that the relation type is compatible with the passed relation model
            if (!relationModel.getScope().equals("local") && !rmodel.getIs_generalizable())
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Relation type " + rmodel.getName() + " cannot be made outside a local scope"))
                        .build();

            // Remove any weak relations that might conflict
            // LATER better idea: write a traverser that will disregard weak relations
            Boolean colocation = rmodel.getIs_colocation();
            if (colocation) {
                Iterable<Relationship> relsA = readingA.getRelationships(ERelations.RELATED);
                for (Relationship r : relsA) {
                    RelationTypeModel rm = returnRelationType(tradNode, r.getProperty("type").toString(), tx);
                    if (rm.getIs_weak())
                        r.delete();
                }
                Iterable<Relationship> relsB = readingB.getRelationships(ERelations.RELATED);
                for (Relationship r : relsB) {
                    RelationTypeModel rm = returnRelationType(tradNode, r.getProperty("type").toString(), tx);
                    if (rm.getIs_weak())
                        r.delete();
                }
            }

            Boolean isCyclic = ReadingService.wouldGetCyclic(readingA, readingB, tx);
            if (isCyclic && colocation) {
                    return Response
                            .status(Status.CONFLICT)
                            .entity(jsonerror("This relation creation is not allowed, it would result in a cyclic graph."))
                            .build();
            } else if (!isCyclic && !colocation) {
                return Response
                        .status(Status.CONFLICT)
                        .entity(jsonerror("This relation creation is not allowed. The two readings can be co-located."))
                        .build();
            } // TODO add constraints about witness uniqueness or lack thereof

            // Check if relation already exists
            Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);
            for (Relationship relationship : relationships) {
                if (relationship.getOtherNode(readingA).equals(readingB)) {
                    RelationModel thisRel = new RelationModel(relationship, tx);
                    RelationTypeModel rtm = returnRelationType(tradNode, thisRel.getType(), tx);
                    if (thisRel.getType().equals(relationModel.getType())) {
                        // TODO allow for update of existing relation
                        tx.close();
                        return Response.status(Status.NOT_MODIFIED).type(MediaType.TEXT_PLAIN_TYPE).build();
                    } else if (!rtm.getIs_weak()) {
                        tx.close();
                        String msg = String.format("Relation of type %s already exists between readings %s and %s",
                                relationModel.getType(), relationModel.getSource(), relationModel.getTarget());
                        return Response.status(Status.CONFLICT).entity(jsonerror(msg)).build();
                    }
                }
            }

            // We are finally ready to write a relation.
            readingsAndRelationModel = createSingleRelation(readingA, readingB, relationModel, rmodel, tradNode, tx);
            // We can also write any transitive relationships.
            propagateRelation(readingsAndRelationModel, rmodel, tradNode, tx);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.status(Response.Status.CREATED).entity(readingsAndRelationModel).build();
    }

    /**
     * Muck with the database to set a relation
     *
     * @param readingA - the source reading
     * @param readingB - the target reading
     * @param relModel - the RelationModel to set
     * @param rtm      - the RelationTypeModel describing what sort of relation this is
     * @return a GraphModel containing the single n4j relationship plus whatever readings were re-ranked
     */
    static private GraphModel createSingleRelation(Node readingA, Node readingB,
                                            RelationModel relModel, RelationTypeModel rtm, Node tradNode, Transaction tx) throws Exception {

        try{

            ArrayList<ReadingModel> changedReadings = new ArrayList<>();
            ArrayList<RelationModel> createdRelations = new ArrayList<>();

            Boolean colocation = rtm.getIs_colocation();
            Relationship relationAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);

            relationAtoB.setProperty("type", nullToEmptyString(relModel.getType()));
            relationAtoB.setProperty("scope", nullToEmptyString(relModel.getScope()));
            relationAtoB.setProperty("annotation", nullToEmptyString(relModel.getAnnotation()));
            relationAtoB.setProperty("displayform",
                    nullToEmptyString(relModel.getDisplayform()));
            relationAtoB.setProperty("a_derivable_from_b", relModel.getA_derivable_from_b());
            relationAtoB.setProperty("b_derivable_from_a", relModel.getB_derivable_from_a());
            relationAtoB.setProperty("alters_meaning", relModel.getAlters_meaning());
            relationAtoB.setProperty("is_hyperrelation", relModel.getIs_hyperrelation());
            relationAtoB.setProperty("hsource", relModel.getHSource() == null ? "" : relModel.getHSource());
            relationAtoB.setProperty("htarget", relModel.getHTarget() == null ? "" : relModel.getHTarget());
            relationAtoB.setProperty("is_significant", relModel.getIs_significant());
            relationAtoB.setProperty("non_independent", relModel.getNon_independent());
            relationAtoB.setProperty("reading_a", readingA.getProperty("text"));
            relationAtoB.setProperty("reading_b", readingB.getProperty("text"));
            if (colocation) relationAtoB.setProperty("colocation", true);

            // Recalculate the ranks, if necessary
            Long rankA = (Long) readingA.getProperty("rank");
            Long rankB = (Long) readingB.getProperty("rank");
            if (!rankA.equals(rankB) && colocation) {
                // Which one is the lower-ranked reading? Promote it, and recalculate from that point
                Long higherRank = rankA < rankB ? rankB : rankA;
                Node lowerRanked = rankA < rankB ? readingA : readingB;
                lowerRanked.setProperty("rank", higherRank);
                changedReadings.add(new ReadingModel(lowerRanked, tx));
                // Node tradNode = getTraditionNode.apply(tx);
                Set<Node> changedRank = ReadingService.recalculateRank(tradNode, lowerRanked, false, tx);
                for (Node cr : changedRank)
                    if (!cr.equals(lowerRanked))
                        changedReadings.add(new ReadingModel(cr, tx));

            }

            createdRelations.add(new RelationModel(relationAtoB, tx));
            return new GraphModel(changedReadings, createdRelations, new ArrayList<>());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if a reading is a "Meta"-reading
     *
     * @param reading - the reading to check
     * @return true or false
     */
    static private boolean isMetaReading(Node reading) {
        return reading != null &&
                ((reading.hasProperty("is_lacuna") && reading.getProperty("is_lacuna").equals(true)) ||
                        (reading.hasProperty("is_start") && reading.getProperty("is_start").equals(true)) ||
                        (reading.hasProperty("is_ph") && reading.getProperty("is_ph").equals(true)) ||
                        (reading.hasProperty("is_end") && reading.getProperty("is_end").equals(true))
                );
    }

    /**
     * Propagates reading relations according to type specification.
     * NOTE - To be used inside a transaction
     *
     * @param newRelationResult - the GraphModel that contains a relation just created
     * @param rtm - the relation type specification
     */
    static private void propagateRelation(GraphModel newRelationResult, RelationTypeModel rtm, Node tradNode, Transaction tx) throws Exception {
        // First see if this relation type should be propagated.
        if (!rtm.getIs_transitive()) return;
        // Now go through all the relations that have been created, and make sure that any
        // transitivity effects have been accounted for.

        // Node tradNode = getTraditionNode.apply(tx);
        for (RelationModel rm : newRelationResult.getRelations()) {
            TransitiveRelationTraverser relTraverser = new TransitiveRelationTraverser(tradNode, rtm, tx);
            Node startNode = tx.getNodeByElementId(rm.getSource());
            ArrayList<Node> relatedNodes = new ArrayList<>();
            // Get all the readings that are related by this or a more closely-bound type.
            tx.traversalDescription().depthFirst()
                    .relationships(ERelations.RELATED)
                    .evaluator(relTraverser)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(relatedNodes::add);
            // Now go through them and make sure the relations are explicit.
            ArrayList<Node> iterateNodes = new ArrayList<>(relatedNodes);
            while (!iterateNodes.isEmpty()) {
                Node readingA = iterateNodes.remove(0);
                HashSet<Node> alreadyRelated = new HashSet<>();
                readingA.getRelationships(ERelations.RELATED).forEach(x -> alreadyRelated.add(x.getOtherNode(readingA)));
                // System.out.println(String.format("Propagating type model %s on node %d / %s",
                //        rtm.getName(), readingA.getId(), readingA.getProperty("text")));
                for (Node readingB : iterateNodes) {
                    if (!alreadyRelated.contains(readingB)) {
                        // System.out.println(String.format("...making relation %s to node %d / %s", rm.getType(), readingB.getId(), readingB.getProperty("text")));
                        GraphModel interim = createSingleRelation(readingA, readingB, rm, rtm, tradNode, tx);
                        newRelationResult.addReadings(interim.getReadings());
                        newRelationResult.addRelations(interim.getRelations());
                    }
                }
            }
            // Now go back through them and make sure that relations to more loosely-bound
            // transitive nodes are marked.
            for (Node sibling : relatedNodes) {
                HashMap<Node, Relationship> connections = new HashMap<>();
                // Get the nodes we are directly related to, and the relations involved, if
                // they meet the criteria
                for (Relationship r : sibling.getRelationships(ERelations.RELATED)) {
                    RelationTypeModel othertm = returnRelationType(tradNode, r.getProperty("type").toString(), tx);
                    if (othertm.getBindlevel() > rtm.getBindlevel() && othertm.getIs_transitive())
                        connections.put(r.getOtherNode(sibling), r);
                }

                HashSet<Node> cousins = new HashSet<>(relatedNodes);
                for (Node n : connections.keySet()) {
                    cousins.remove(n);
                    RelationModel newmodel = new RelationModel(connections.get(n), tx);
                    RelationTypeModel newtm = returnRelationType(tradNode, newmodel.getType(), tx);
                    for (Node c : cousins) {
                        ArrayList<Relationship> priorLinks = DatabaseService.getRelationshipTo(n, c, ERelations.RELATED);
                        if (priorLinks.size() == 0) {
                            // Create a relation based on the looser link
                            GraphModel interim = createSingleRelation(n, c, newmodel, newtm, tradNode, tx);
                            newRelationResult.addReadings(interim.getReadings());
                            newRelationResult.addRelations(interim.getRelations());
                        }
                    }
                }
            }
        }
        // tx.close();
    }

    /**
     * Remove the relation specified. There should be only one.
     *
     * @title Delete a relation specifed by JSON data.
     * @param relationModel - the JSON specification of the relationship(s) to delete
     * @return A list of all relationships that were removed.
     * @statuscode 200 - on success
     * @statuscode 400 - if an invalid scope was specified
     * @statuscode 404 - if no matching relationship was found
     * @statuscode 500 - on failure, with JSON error message
     */
    @POST
    @Path("/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    
    public Response deleteByData(RelationModel relationModel) {
        ArrayList<RelationModel> deleted = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node readingA = tx.getNodeByElementId(relationModel.getSource());
            Node readingB = tx.getNodeByElementId(relationModel.getTarget());

            switch (relationModel.getScope()) {
                case SCOPE_LOCAL:
                    ArrayList<Relationship> findRel = DatabaseService.getRelationshipTo(readingA, readingB, ERelations.RELATED);
                    if (findRel.isEmpty()) {
                        return Response.status(Status.NOT_FOUND).entity(jsonerror("Relation not found")).build();
                    } else {
                        Relationship theRel = findRel.get(0);
                        RelationModel relInfo = new RelationModel(theRel, tx);
                        theRel.delete();
                        deleted.add(relInfo);
                    }
                    break;

                case SCOPE_SECTION:
                case SCOPE_TRADITION:
                    Traverser toCheck = relationModel.getScope().equals(SCOPE_SECTION)
                            ? VariantGraphService.returnTraditionSection(readingA.getProperty("section_id").toString(), tx)
                            : VariantGraphService.returnEntireTradition(tradId, db);

                    for (Relationship rel : toCheck.relationships()) {
                        if (rel.getType().name().equals(ERelations.RELATED.name())) {
                            Node ra = tx.getNodeByElementId(relationModel.getSource());
                            Node rb = tx.getNodeByElementId(relationModel.getTarget());

                            if ((rel.getStartNode().getProperty("text").equals(ra.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(ra.getProperty("text")))
                                    && (rel.getStartNode().getProperty("text").equals(rb.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(rb.getProperty("text")))) {
                                RelationModel relInfo = new RelationModel(rel, tx);
                                rel.delete();
                                deleted.add(relInfo);
                            }
                        }
                    }
                    break;

                default:
                    return Response.status(Status.BAD_REQUEST).entity(jsonerror("Undefined Scope")).build();
            }
            tx.commit();
        }
        return Response.status(Response.Status.OK).entity(deleted).build();
    }
    
    /**
     * Removes a relation by internal ID.
     *
     * @title Delete relation by ID
     * @param relationId - the ID of the relation to delete
     * @return The deleted relation
     * @statuscode 200 - on success
     * @statuscode 403 - if the given ID does not belong to a relation
     * @statuscode 500 - on failure, with JSON error message
     */
    @DELETE
    @Path("{relationId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    
    public Response deleteById(@PathParam("relationId") String relationId) {
        RelationModel relationModel;

        try (Transaction tx = db.beginTx()) {
            Relationship relationship = tx.getRelationshipByElementId(relationId);
            if(relationship.getType().name().equals("RELATED")) {
                relationModel = new RelationModel(relationship, tx);
                relationship.delete();
            } else {
                return Response.status(Status.FORBIDDEN).entity(jsonerror("This is not a relation link")).build();
            }
            tx.commit();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(relationModel).build();
    }
    
    static private String nullToEmptyString(String str){
        return str == null ? "" : str;
    }
}