package net.stemmaweb.services;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.stemmaweb.rest.Tradition;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.PathExpanders;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.*;

import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.WitnessTokensModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

public class VariantGraphService {

    /**
     * Check whether a given section actually belongs to the given tradition.
     *
     * @return - true or false
     */

    public static TraversalDescription simpleTraverser(Transaction tx, int section_length, Long witnesses_count){
        return tx.traversalDescription()
                .order(BranchOrderingPolicies.PREORDER_BREADTH_FIRST)
                .evaluator(new VariantEvaluator(witnesses_count))
                .evaluator(Evaluators.atDepth(section_length))
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(ERelations.COLLATION, Direction.OUTGOING)
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING);
    }


    public static Boolean sectionInTradition(String tradId, String aSectionId, GraphDatabaseService db) {
        boolean found = false;
        try (Transaction tx = db.beginTx()) {
        	found = sectionInTradition(tradId, aSectionId, tx);
            tx.close();
        }
        return found;
    }

    public static Boolean sectionInTradition(String tradId, String aSectionId, Transaction tx) {
    	Node traditionNode = getTraditionNode(tradId, tx);
    	if (traditionNode == null)
    		return false;
    	
    	boolean found = false;
		for (Node s : DatabaseService.getRelated(traditionNode, ERelations.PART, tx)) {
			if (s.getElementId().equals(aSectionId)) {
				found = true;
				break;
			}
		}
    	return found;
    }
    
    /**
     * Get the start node of a section, or the first section in a tradition
     *
     * @param nodeId the ID of the tradition or section whose start node should be returned
     * @param tx  the main database transaction
     * @return  the start node, or null if there is none.
     *      NOTE if there are multiple unordered sections, an arbitrary start node may be returned!
     */
    // public static Node getStartNode(String nodeId, Transaction tx) {
    // 	return getBoundaryNode(nodeId, tx, ERelations.COLLATION);
    // }

    public static Node getStartNode(String nodeId, Transaction tx) {
    	return getBoundaryNode(nodeId, tx, ERelations.COLLATION);
    }
    
    /**
     * Get the end node of a section, or the last section in a tradition
     *
     * @param nodeId the ID of the tradition or section whose end node should be returned
     * @param db  the GraphDatabaseService where the tradition is stored
     * @return  the end node, or null if there is none
     *      NOTE if there are multiple unordered sections, an arbitrary end node may be returned!
     */
    public static Node getEndNode(String nodeId, GraphDatabaseService db, Transaction tx) {
    	// Transaction tx = db.beginTx();
        // tx.close();
    	return getBoundaryNode(nodeId, tx, ERelations.HAS_END);
    }

    public static Node getEndNode(String nodeId, Transaction tx) {
    	return getBoundaryNode(nodeId, tx, ERelations.HAS_END);
    }
    
    private static Node getBoundaryNode(String nodeId, Transaction tx, ERelations direction) {
        Node boundNode = null;
        // If we have been asked for a tradition node, use either the first or the last of
        // its section nodes instead.
        Node currentNode = getTraditionNode(nodeId, tx);
        if (currentNode != null) {
            ArrayList<Node> sections = getSectionNodes(currentNode, tx);
            if (!sections.isEmpty()) {
                Node relevantSection = direction.equals(ERelations.HAS_END)
                        ? sections.get(sections.size() - 1)
                        : sections.get(0);
                return getBoundaryNode(relevantSection.getElementId(), tx, direction);
            } else return null;
        }
//        // Were we asked for a nonexistent tradition node (i.e. a non-Long that corresponds to no tradition)?
//        long nodeIndex;
//        try {
//            nodeIndex = Long.parseLong(nodeId);
//        } catch (NumberFormatException e) {
//            return null;
//        }
        // If we are here, we were asked for a section node.
		currentNode = tx.getNodeByElementId(nodeId);
		if (currentNode != null)
            if(currentNode.getDegree(direction, Direction.OUTGOING) > 0){
			    return currentNode.getSingleRelationship(direction, Direction.OUTGOING).getEndNode();
            }else {
                boundNode = currentNode;
            }
		return boundNode;
    }

    /**
     * Return the list of a tradition's sections, ordered by NEXT relationship
     *
     * @param tradNode    the tradition whose sections to return
     * @param tx        the database transaction
     * @return          a list of sections, which is empty if the tradition doesn't exist
     */
    // public static ArrayList<Node> getSectionNodes(String tradId, Transaction tx) {
    // 	ArrayList<Node> sectionNodes;
    // 	try {
    // 		sectionNodes = getSectionNodes(tradId, tx);
    //     }
    // 	return sectionNodes;
    // }
    public static ArrayList<Node> getSectionNodes(Node tradNode, Transaction tx) {
        // Node tradition = getTraditionNode(tradId, tx);
        ArrayList<Node> sectionNodes = new ArrayList<>();
        if (tradNode == null)
            return sectionNodes;
        ArrayList<Node> sections = DatabaseService.getRelated(tradNode, ERelations.PART, tx);
        int size = sections.size();
        for(Node n: sections) {
            if (!n.getRelationships(Direction.INCOMING, ERelations.NEXT)
                    .iterator()
                    .hasNext()) {
                tx.traversalDescription()
                        .depthFirst()
                        .relationships(ERelations.NEXT, Direction.OUTGOING)
                        .evaluator(Evaluators.toDepth(size))
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(n)
                        .nodes()
                        .forEach(sectionNodes::add);
                break;
            }
        }
        return sectionNodes;
    }

    /**
     * Get the node of the specified tradition
     *
     * @param tradId  the string ID of the tradition we're hunting
     * @param tx      the GraphDatabaseService where the tradition is stored
     * @return        the relevant tradition node
     */
    // public static Node getTraditionNode(String tradId, GraphDatabaseService db, Transaction tx) {
        // Node tradition;
        // try (Transaction tx = db.beginTx()) {
        //     tradition = tx.findNode(Nodes.TRADITION, "id", tradId);
        //     tx.close();
        // }
        // return tx.findNode(Nodes.TRADITION, "id", tradId);
    // }

    public static Node getTraditionNode(String tradId, Transaction tx) {
    	Node tradition;
    	tradition = tx.findNode(Nodes.TRADITION, "id", tradId);
    	return tradition;
    }
    
    /**
     * Get the tradition node that the specified section belongs to
     *
     * @param n  the section node whose tradition we're hunting
     * @param tx  database transaction
     * @return         the relevant tradition node
     */
    // public static Node getTraditionNode(Node section, Transaction tx) {
    // 	return getTraditionNode(section, tx);
    // }

    public static Node getSectionTraditionNode(Node n, Transaction tx) {
        Node tradition;
        Node section;
        if(Objects.equals(n.getLabels().iterator().next().toString(), "READING")){
    	    section = tx.getNodeByElementId(n.getProperty("section_id").toString());
        }else{
            section = tx.getNodeByElementId(n.getElementId());
        }
        tradition = section.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();

        return tradition;
    }

    /**
     * Calculate the common readings within a section, either in normalized view or not
     *
     * @param sectionNode - The section for which to perform the calculation
     */
    public static void calculateCommon(Node sectionNode, Transaction tx) {

        // Get an AlignmentModel for the given section, and go rank by rank to find
        // the common nodes.

        AlignmentModel am = new AlignmentModel(sectionNode, tx);
        // try (Transaction tx = db.beginTx()) {
        Node startNode = VariantGraphService.getStartNode(sectionNode.getElementId(), tx);
        // See which kind of flag we are setting
        String propName = startNode.hasRelationship(Direction.OUTGOING, ERelations.NSEQUENCE) ? "ncommon" : "is_common";
        // Go through the table rank by rank - if a given rank has only a single reading
        // apart from lacunae, and no gaps, it is common
        for (AtomicInteger i = new AtomicInteger(0); i.get() < am.getLength(); i.getAndIncrement()) {
            List<ReadingModel> readingsAtRank = am.getAlignment().stream()
                    .map(x -> x.getTokens().get(i.get())).collect(Collectors.toList());
            HashSet<String> distinct = new HashSet<>();
            for (ReadingModel rm : readingsAtRank) {
                if (rm == null) distinct.add("");
                else if (!rm.getIs_lacuna()) distinct.add(rm.getId());
            }
            // Set the commonality property. It is true if the size of the 'distinct' set is 1.
            distinct.stream().filter(x -> !x.isEmpty())
                    .forEach(x -> tx.getNodeByElementId(x).setProperty(propName, distinct.size() == 1));

        }
    }


    /*
     * Methods for calcuating and removing shadow graphs - normalization and majority text
     */

    /**
     * Make a graph normalization sequence on the given section according to the given relation type,
     * creating NSEQUENCE and REPRESENTS relationships between readings where appropriate, and return
     * a map of each section node to its representative node.
     *
     * @param sectionNode     The section to be normalized
     * @param normalizeTypeList   List containing the (string) name of the type on which we are normalizing
     * @return                A HashMap of nodes to their representatives
     *
     * @throws                Exception if clusters cannot be got, if the requested relation type doesn't
     *                        exist, or if something goes wrong with the transaction
     */

    public static HashMap<Node,Node> normalizeGraph(Node sectionNode, List<String> normalizeTypeList, Transaction tx) throws Exception {
        HashMap<Node,Node> representatives = new HashMap<>();
    	GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        // Make sure the relation type exists
        //*
        Node tradition = getSectionTraditionNode(sectionNode, tx);
        Node relType;
        for (String normalizeType : normalizeTypeList) {
            relType = new RelationTypeModel(normalizeType).lookup(tradition);
            if (relType == null)
                throw new Exception("Relation type " + normalizeType + " does not exist in this tradition");
        }

        Node sectionStart = sectionNode.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
        // Get the list of all readings in this section
        //            Set<Node> sectionNodes = returnTraditionSection(sectionNode).nodes().stream()
        //                    .filter(x -> x.hasLabel(Label.label("READING"))).collect(Collectors.toSet());
        Iterable<Node> tmp = returnTraditionSection(sectionNode.getElementId(), tx).nodes();
        Set<Node> sectionNodes = new HashSet<>();
        for(Node n: tmp){
            if(!n.hasLabel(Nodes.HYPERREADING) && n.hasLabel(Nodes.READING)){
                sectionNodes.add(n);
            };
        }
        // Set<Node> sectionNodes = StreamSupport
        //         .stream(returnTraditionSection(sectionNode.getElementId(), db.beginTx()).nodes().spliterator(), false)
        //         .filter(x -> x.hasLabel(Label.label("READING"))).collect(Collectors.toSet());

        // Find the normalisation clusters and nominate a representative for each
        String tradId = tradition.getProperty("id").toString();
        String sectionId = sectionNode.getElementId();
        for (Set<Node> cluster : RelationService.getCloselyRelatedClusters(
                tradition, sectionNode, tx, normalizeTypeList)) {
            if (cluster.size() == 0) continue;
            Node representative = RelationService.findRepresentative(cluster, tx);
            if (representative == null)
                throw new Exception("No representative found for cluster");
            // Set the representative for all cluster members.
            for (Node n : cluster) {
                representatives.put(n, representative);
                if (!n.equals(representative))
                    representative.createRelationshipTo(n, ERelations.REPRESENTS);
                if (!sectionNodes.remove(n))
                    throw new Exception("Tried to make equivalence for node (" + n.getElementId()
                            + ": " + n.getAllProperties().toString()
                            + ") that was not in sectionNodes");
            }
        }

        // All remaining un-clustered readings are represented by themselves
        sectionNodes.forEach(x -> representatives.put(x, x));

        // Make sure we didn't have any accidental recursion in representation
        for (Node n : representatives.values()) {
            if (n.hasRelationship(Direction.INCOMING, ERelations.REPRESENTS))
                throw new Exception("Recursive representation was created on node " + n.getElementId() + ": " + n.getAllProperties().toString());
        }

        // Now that we have done this, make the shadow sequence

        try(Transaction subTx = db.beginTx()){
            for (Relationship r : subTx.traversalDescription().breadthFirst()
                    .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(sectionStart).relationships()) {
                Node repstart = representatives.getOrDefault(r.getStartNode(), r.getStartNode());
                Node repend = representatives.getOrDefault(r.getEndNode(), r.getEndNode());
                ReadingService.transferWitnesses(repstart, repend, r, ERelations.NSEQUENCE);
            }
            // and calculate the common readings.
            calculateCommon(sectionNode, subTx);
            subTx.commit();
        }

        return representatives;

    }

    /**
     * Clean up after performing normalizeGraph. Removes all NSEQUENCE and REPRESENTS relationships within a section.
     *
     * @param sectionNode  the section to clean up
     * @throws Exception if anything was missed
     */

    public static void clearNormalization(Node sectionNode) throws Exception {
//        GraphDatabaseService db = sectionNode.getGraphDatabase();
    	GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        try (Transaction tx = db.beginTx()) {
            Node sectionStartNode = sectionNode.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            sectionStartNode.removeProperty("ncommon");
            tx.traversalDescription().breadthFirst()
                    .relationships(ERelations.NSEQUENCE,Direction.OUTGOING)
                    .relationships(ERelations.REPRESENTS, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(sectionStartNode).relationships()
                    .forEach(x -> {
                        x.getEndNode().removeProperty("ncommon");
                        x.delete();
                    });

            // TEMPORARY: Check that we aren't polluting the graph DB
//            if (VariantGraphService.returnTraditionSection(sectionNode).relationships()
//                    .stream().anyMatch(x -> x.isType(ERelations.NSEQUENCE) || x.isType(ERelations.REPRESENTS)))
        	if (StreamSupport.stream(VariantGraphService.returnTraditionSection(sectionNode.getElementId(), tx).relationships().spliterator(), false)
        			.anyMatch(x -> x.isType(ERelations.NSEQUENCE) || x.isType(ERelations.REPRESENTS)))
                throw new Exception("Data consistency error on normalization cleanup of section " + sectionNode.getElementId());
            tx.commit();
        }
    }

    /**
     * Return a list of nodes which constitutes the majority text for a section.
     *
     * @param  sectionNode - The section to calculate
     * @return an ordered List of READING nodes that make up the majority text
     */
    public static List<Node> calculateMajorityText(Node sectionNode, Transaction tx) {
        // Get the IDs of our majority readings by going through the alignment table rank by rank
        AlignmentModel am = new AlignmentModel(sectionNode, tx);
        ArrayList<String> majorityReadings = new ArrayList<>();
        for (int rank = 1; rank <= am.getLength(); rank++) {
            int numNulls = 0;
            ArrayList<ReadingModel> rankReadings = new ArrayList<>();
            for (WitnessTokensModel wtm : am.getAlignment()) {
                ReadingModel rdgAtRank = wtm.getTokens().get(rank - 1);
                if (rdgAtRank == null)
                    numNulls++;
                else
                    rankReadings.add(rdgAtRank);
            }
            // Now find the winner
            Optional<ReadingModel> winner = rankReadings.stream().max(Comparator.comparingInt(x -> x.getWitnesses().size()));
            if (winner.isPresent() && winner.get().getWitnesses().size() >= numNulls) {
                majorityReadings.add(winner.get().getId());
            }
        }

        // Now make the relations between them
        ArrayList<Node> result = new ArrayList<>();
        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        try (Transaction subTx = db.beginTx()) {
            // Go through the alignment model rank by rank, finding the majority reading for each rank
            String sectionId = sectionNode.getElementId();
            result.add(getStartNode(sectionId, subTx));
            majorityReadings.forEach(x -> result.add(subTx.getNodeByElementId(x)));
            result.add(getEndNode(sectionId, subTx));
            subTx.commit();
        }
        return result;
    }

    /**
     * Collect all annotations, recursively, on the set of nodes that has been passed in.
     *
     *
     * @return The annotation nodes that point (ultimately) to the nodes in question
     */
    public static List<Node> collectAnnotationsOnSet(List<Node> nodeSet, boolean collectReferents, Transaction tx) {
        ArrayList<Node> annotationNodes = new ArrayList<>();
        try {
            // We want to find all annotation nodes that are linked both to the tradition node
            // and (perhaps indirectly through other annotations) to some node in this set.
            HashSet<Node> foundAnns = new HashSet<>();
            for (Node n : nodeSet) {
                if (collectReferents) {
                    Traverser theseAnnotations = returnTraverser(n.getElementId(), nodeAnnotations, PathExpanders.forDirection(Direction.INCOMING), tx);
                    theseAnnotations.nodes().forEach(foundAnns::add);
                } else {
                    for (Relationship r : n.getRelationships(Direction.INCOMING))
                        if (r.getStartNode().hasRelationship(Direction.INCOMING, ERelations.HAS_ANNOTATION))
                            foundAnns.add(r.getStartNode());
                }
            }
            annotationNodes = new ArrayList<>(foundAnns);
        }catch (Exception e){
            e.printStackTrace();
        }
        return annotationNodes;
    }


    /*
     * Tradition and section crawlers, respectively
     */

    // Returns every node pointing outward from a TRADITION.
    private static final Evaluator traditionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    // Returns every node pointing outward from a TRADITION, stopping at PART relationships and
    // HAS_ANNOTATION relationships to exclude sections and annotations.
    private static final Evaluator traditionMetaCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        // Stop at the sections, inclusively
        if (path.lastRelationship().getStartNode().hasLabel(Nodes.SECTION)) {
            // We want to keep the relationship if it is a NEXT or a PUB_ORDER one, otherwise truncate.
            ArrayList<String> allowed = new ArrayList<>(Arrays.asList("NEXT", "PUB_ORDER"));
            if (allowed.contains(path.lastRelationship().getType().name()))
                return Evaluation.INCLUDE_AND_PRUNE;
            else
                return Evaluation.EXCLUDE_AND_PRUNE;
        }
        // Also exclude any annotations
        if (path.lastRelationship().getType().name().equals(ERelations.HAS_ANNOTATION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static final Evaluator sectionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        String type = path.lastRelationship().getType().name();
        if (type.equals(ERelations.PART.toString()) || type.equals(ERelations.NEXT.toString())
                || type.equals(ERelations.PUB_ORDER.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static final Evaluator traditionRelations = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        if (path.lastRelationship().getType().name().equals(ERelations.RELATED.toString()))
            return Evaluation.INCLUDE_AND_CONTINUE;
        return Evaluation.EXCLUDE_AND_CONTINUE;
    };

    private static final Evaluator nodeAnnotations = path -> {
        // Don't include the node in question
        if (path.length() == 0)
            return Evaluation.EXCLUDE_AND_CONTINUE;
        // Truncate before we get back to the tradition itself
        if (path.lastRelationship().getType().name().equals(ERelations.HAS_ANNOTATION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        // Do follow through any annotation nodes, identified by the existence of that relationship
        if (path.lastRelationship().getStartNode().hasRelationship(Direction.INCOMING, ERelations.HAS_ANNOTATION))
            return Evaluation.INCLUDE_AND_CONTINUE;
        // Don't follow anything else
        return Evaluation.EXCLUDE_AND_PRUNE;
    };

    private static final Evaluator sequenceLinks = path -> {
        final Set<String> sequenceTypes = new HashSet<>();
        sequenceTypes.add("SEQUENCE");
        sequenceTypes.add("LEMMA_TEXT");
        sequenceTypes.add("NSEQUENCE"); // this really shouldn't be found though
        sequenceTypes.add("MAJORITY");  // nor this
        sequenceTypes.add("EMENDED");
        // Don't include the start node
        if (path.length() == 0)
            return Evaluation.EXCLUDE_AND_CONTINUE;
        Relationship lr = path.lastRelationship();
        // If we are on a tradition node or a sequence node we need to traverse down to the sequence start nodes
        if (lr.getStartNode().hasLabel(Label.label("TRADITION")))
            return lr.getType().name().equals("PART")
                    ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        if (lr.getStartNode().hasLabel(Label.label("SECTION")))
            return lr.getType().name().equals("HAS_COLLATION")
                    ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        // By this point we should have got to the section start. Follow all readings, knowing that emendations
        // are also readings.
        if (lr.getStartNode().hasLabel(Label.label("READING")))
            return sequenceTypes.contains(lr.getType().name())
                    ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        // If we are in any other situation, cut it off.
        return Evaluation.EXCLUDE_AND_PRUNE;
    };

    @SuppressWarnings("rawtypes")
    private static Traverser returnTraverser (String startNodeId, Evaluator ev, PathExpander ex, Transaction tx) {
        Traverser tv;
        Node startNode = tx.getNodeByElementId(startNodeId);
        tv = tx.traversalDescription()
                .depthFirst()
                .expand(ex)
                .evaluator(ev)
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                .traverse(startNode);
        return tv;
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param tradId  the string ID of the tradition to crawl
     * @param db      the relevant GraphDatabaseService
     * @return        an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(String tradId, GraphDatabaseService db) {
        return returnEntireTradition(getTraditionNode(tradId, db.beginTx()), db.beginTx());
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param traditionNode   the Node object of the tradition to crawl
     * @return                an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(Node traditionNode, Transaction tx) {
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        return returnTraverser(traditionNode.getElementId(), traditionCrawler, PathExpanders.forDirection(Direction.OUTGOING), tx);
    }

    public static Traverser returnTraditionMeta(Node traditionNode, Transaction tx) {
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        return returnTraverser(traditionNode.getElementId(), traditionMetaCrawler, PathExpanders.forDirection(Direction.OUTGOING), tx);
    }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param sectionId  the string ID of the section to crawl
     * @param db         the relevant GraphDatabaseService
     * @return           an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    // public static Traverser returnTraditionSection(String sectionId, Transaction tx) {
    //     Traverser tv;
    //     tv = returnTraditionSection(sectionId, tx);
    //     return tv;
    // }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param sectionId  the Node object of the section to crawl
     * @return             an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    public static Traverser returnTraditionSection(String sectionId, Transaction tx) {
        return returnTraverser(sectionId, sectionCrawler, PathExpanders.forDirection(Direction.OUTGOING), tx);
    }

    /**
     * Return a traverser that includes all RELATED relationships in a tradition.
     *
     * @param traditionNode the Node object of the tradition to crawl
     * @return             an org.neo4j.graphdb.traversal.Traverser object containing the relations
     */
    public static Traverser returnTraditionRelations(Node traditionNode) {
        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        return returnTraverser(traditionNode.getElementId(), traditionRelations, PathExpanders.allTypesAndDirections(), db.beginTx());
    }

    /**
     * Return a traverser that includes all sequence-like relations in a tradition or section.
     * It can start from the tradition node, the section node, or the section start node.
     *
     * @param startNode the Node object of the tradition or section to crawl
     * @return          an org.neo4j.graphdb.traversal.Traverser object containing the sequences
     */
    public static Traverser returnAllSequences(Node startNode) {
        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        return returnTraverser(startNode.getElementId(), sequenceLinks, PathExpanders.forDirection(Direction.OUTGOING), db.beginTx());
    }
}
