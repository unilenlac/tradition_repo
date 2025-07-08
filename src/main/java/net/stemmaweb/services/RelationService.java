package net.stemmaweb.services;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.RelationType;

/**
 * 
 * Provides helper methods related to reading relations.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class RelationService {


    public static void linkRelationToTradition(Node tradNode, String relType, GraphDatabaseService db){
        String tmp_q = String.format("MATCH (r:RELATION_TYPE {name: '%s'}) RETURN r LIMIT 1", relType);

        try (Transaction subTx = db.beginTx()) {
            Result tmp = subTx.execute(tmp_q);
            Collection<Object> n = tmp.next().values();
            n.forEach(x -> {
                if (x instanceof Node) {
                    Node t = (Node) x;
                    tradNode.createRelationshipTo(t, ERelations.HAS_RELATION_TYPE);
                }
            });

            subTx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not link relation type to tradition", e);
        }
    }
    /**
     * Copies all the properties of a relationship to another if the property
     * exists.
     *
     * @param oldRelationship the relationship to copy from
     * @param newRelationship the relationship to copy to
     */
    public static void copyRelationshipProperties(Relationship oldRelationship,
                                                          Relationship newRelationship) {
        for (String key : oldRelationship.getPropertyKeys()) {
            if (oldRelationship.hasProperty(key)) {
                newRelationship.setProperty(key, oldRelationship.getProperty(key));
            }
        }
    }

    /**
     * Returns a RelationTypeModel for the given relation type string, associated with
     * the given tradition. Creates the type with default values if it doesn't already exist.
     *
     * @param tradNode   - The ID string of the tradition
     * @param relType       - The name of the relation type (e.g. "spelling")
     * @return A RelationTypeModel with the relation type information.
     */
    public static RelationTypeModel returnRelationType(Node tradNode, String relType, Transaction tx) {
        // RelationType rtRest = new RelationType(tradNode.getElementId(), relType);
        RelationTypeModel rtResult = RelationType.getRelationTypeMethod(relType, tradNode);
        RelationTypeModel res = null;
        if (rtResult != null) {
            RelationTypeModel rtm = new RelationTypeModel();
            rtm.setName(relType);
            rtm.setDefaultsettings(true);
            // res = rtRest.create(rtm);
            res = RelationType.create_relation_type(tradNode, relType, true, tx);
        }
        assert res != null;
        return res;
    }

    /**
     * Returns a list of RelationTypeModels that pertain to a tradition. The lookup can be
     * based on the tradition node, or any section or reading node therein.
     *
     * @param referenceNode - a Tradition, Section or Reading node that belongs to the tradition
     * @return - a list of RelationTypeModels for the tradition in question
     * @throws Exception - if the tradition node can't be determined from the referenceNode
     */
    public static List<RelationTypeModel> ourRelationTypes(Node referenceNode, Transaction tx) throws Exception {

        List<RelationTypeModel> result = new ArrayList<>();
        try {
        	// Must be under control of the same transaction!
        	// referenceNode = tx.getNodeByElementId(referenceNode.getElementId());
            // Find the tradition node
            Node traditionNode = null;
            if (referenceNode.hasLabel(Nodes.TRADITION))
                traditionNode = referenceNode;
            else if (referenceNode.hasLabel(Nodes.SECTION))
                traditionNode = VariantGraphService.getTraditionNode(referenceNode, tx);
            else if (referenceNode.hasLabel(Nodes.READING)) {
                Node sectionNode = tx.getNodeByElementId(referenceNode.getProperty("section_id").toString());
                traditionNode = VariantGraphService.getTraditionNode(sectionNode, tx);
            }
            assert(traditionNode != null);
            // ...and query its relation types.
            ResourceIterable<Relationship> tmpRel = traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE);
            traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE).forEach(
                    x -> {
                        String tmp = x.getEndNode().getRelationshipTypes().toString();
                        result.add(new RelationTypeModel(x.getEndNode()));
                    }
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not collect relation types", e);
        } catch (Throwable t) {
            System.out.println(t);
            t.printStackTrace();
        }
        return result;
    }

    /**
     * Retrieve clusters of readings, either colocated or non-, from the given section of the given tradition.
     *
     * @param tradNode - the node of the relevant tradition
     * @param sectionNode - the node of the relevant section
     * @param tx - the GraphDatabaseService to use
     * @param colocations - whether we are retrieving colocated clusters or non-colocated ones
     * @return - a list of sets, where each set represents a group of colocated readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong in the algorithm.
     */
    public static List<Set<Node>> getClusters(
            Node tradNode, Node sectionNode, Transaction tx, Boolean colocations)
            throws Exception {
        // Get the tradition node and find the relevant relation types
        HashSet<String> useRelationTypes = new HashSet<>();
        // Node traditionNode = VariantGraphService.getTraditionNode(tradId, tx);
        for (RelationTypeModel rtm : ourRelationTypes(tradNode, tx))
            if (rtm.getIs_colocation() == colocations)
                useRelationTypes.add(String.format("\"%s\"", rtm.getName()));

        // Now run the unionFind algorithm on the relevant subset of relation types
        return collectSpecifiedClusters(tradNode, sectionNode, tx, useRelationTypes);
    }

    /**
     * Retrieve clusters of readings that should be conflated according to the given threshold RelationType.
     *
     * @param tradNode - the node of the relevant tradition
     * @param sectionNode - the node (as a string) of the relevant section
     * @param tx - the GraphDatabaseService to use
     * @param thresholdNameList - the name of a RelationType; all of these relations and ones more closely bound will be clustered.
     * @return - a list of sets, where each set represents a group of closely related readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong with the algorithm
     */
    static List<Set<Node>> getCloselyRelatedClusters(
            Node tradNode, Node sectionNode, Transaction tx, List<String> thresholdNameList)
            throws Exception {
        // Is it a no-op?
        if (thresholdNameList == null) return new ArrayList<>();
        // Then we have some work to do.
        HashSet<String> closeRelations = new HashSet<>();
        // Node traditionNode = VariantGraphService.getTraditionNode(tradId, tx);
        List<RelationTypeModel> rtmlist = ourRelationTypes(tradNode, tx);
        for (RelationTypeModel thresholdName : rtmlist) {
            closeRelations.add(thresholdName.getName());
        }

        for (String thresholdName : thresholdNameList) {

            int bindlevel = 0;
            Optional<RelationTypeModel> thresholdModel = rtmlist.stream().filter(x -> x.getName().equals(thresholdName)).findFirst();
            if (thresholdModel.isPresent())
                bindlevel = thresholdModel.get().getBindlevel();
            for (RelationTypeModel rtm : rtmlist)
                if (rtm.getBindlevel() <= bindlevel)
                    closeRelations.add(String.format("%s", rtm.getName()));
        }

        return collectSpecifiedClusters(tradNode, sectionNode, tx, closeRelations);
    }

    private static List<Set<Node>> collectSpecifiedClusters(
            Node tradNode, Node sectionNode, Transaction tx, Set<String> relatedTypes)
            throws Exception {
        // Now run the unionFind algo - which is now the wcc algorithm - on the relevant subset of relation types
        List<Set<Node>> result = new ArrayList<>();
        Map<String, Set<String>> clusters = new HashMap<>();
        // Make the arguments
        // A struct to store the results
        String related_graph = "RETURN gds.graph.exists('related') AS exists";
        Result g = tx.execute(related_graph);
        if (g.hasNext() && (Boolean) g.next().get("exists")) {
            String drop_graph = "CALL gds.graph.drop('related') YIELD graphName";
            Result drop_res = tx.execute(drop_graph);
            if(drop_res.hasNext()){
                // enforce graph drop
                drop_res.resultAsString();
            }
        }
        relatedTypes = relatedTypes.stream().map(x -> x.replace(x, String.format("'%s'", x))).collect(Collectors.toSet());
        String rtypes = String.join(", ", relatedTypes);
        String graph = String.format("MATCH (source:READING)-[r:RELATED]->(target:READING)\n " +
                "WHERE source.section_id = '%s' AND target.section_id = '%s' AND r.type IN [%s] \n" +
                "WITH gds.graph.project('related', source, target) AS g\n " +
                "RETURN g.graphName AS graph, g.nodeCount AS nodes, g.relationshipCount AS rels", sectionNode.getElementId(), sectionNode.getElementId(), rtypes);
        Result res = tx.execute(graph);
        if (res.hasNext() && res.next().get("graph") != null) {
            // Node traditionNode = VariantGraphService.getTraditionNode(tradId, tx);
            String stream = "CALL gds.wcc.stream('related')\n " +
                    "YIELD nodeId, componentId\n " +
                    "RETURN elementId(gds.util.asNode(nodeId)) as nodeId, componentId as setId\n " +
                    "ORDER BY setId, nodeId";
            Result r = tx.execute(stream);
            while (r.hasNext()) {
                Map<String, Object> row = r.next();
                String setId = row.get("setId").toString();
                Set<String> cl = clusters.getOrDefault(setId, new HashSet<>());
                String nodeId = row.get("nodeId").toString();
                cl.add(nodeId);
                clusters.put(setId, cl);
            }
        }

        // Convert the map of setID -> set of nodeIDs into a list of nodesets
        clusters.keySet().stream().filter(x -> clusters.get(x).size() > 1)
                .forEach(x -> result.add(clusters.get(x).stream().map(tx::getNodeByElementId).collect(Collectors.toSet())));

        String drop_graph = "CALL gds.graph.drop('related', false) YIELD graphName";
        Result drop = tx.execute(drop_graph);
        if(drop.hasNext()){
            assert drop.hasNext();
        }
        return result;
    }

    static Node findRepresentative(Set<Node> alternatives, Transaction tx) {
        GraphDatabaseService db;
        // See if this is trivial
        if (alternatives.isEmpty()) return null;
        Node ref = alternatives.stream().findFirst().get();
        if (alternatives.size() == 1) return ref;

        // It's not trivial
        // db = ref.getGraphDatabase();
    	// db = new GraphDatabaseServiceProvider().getDatabase();
        Node representative = null;
        // Go through the alternatives
        try {
            // First see if one of the alternatives is a lemma
            Optional<Node> thelemma = alternatives.stream()
                    .filter(x -> (Boolean) x.getProperty("is_lemma", false)).findFirst();
            if (thelemma.isPresent())
                representative = thelemma.get();

            // Next sort through the readings with normal forms. If there is a majority
            // normal form, we want the reading that has this form as its text; failing
            // that, we want the majority-witness of these readings.
            else {
                // Do a frequency count of normal forms
                HashMap<String, Integer> normals = new HashMap<>();
                alternatives.stream().filter(x -> x.hasProperty("normal_form"))
                        .map(x -> x.getProperty("normal_form").toString())
                        .forEach(x -> normals.put(x, normals.getOrDefault(x, 0) + 1));
                if (normals.size() > 0) {
                    String nf = normals.keySet().stream().max(Comparator.comparingInt(normals::get)).get();
                    Optional<Node> rep = alternatives.stream().filter(x -> x.getProperty("text").equals(nf)).findFirst();
                    if (rep.isPresent())
                        representative = rep.get();
                    else {
                        rep = alternatives.stream()
                                .filter(x -> x.getProperty("normal_form", "").equals("nf"))
                                .min((a, b) -> RelationService.byWitnessesDescending(a, b, tx));
                        if (rep.isPresent()) representative = rep.get();
                    }
                }
            }

            // If that didn't get us an answer, return the most "popular" reading
            if (representative == null)
                representative = alternatives.stream().sorted((a, b) -> RelationService.byWitnessesDescending(a, b, tx))
                        .collect(Collectors.toList()).get(0);

            tx.commit();
        }catch(Exception e){
            e.printStackTrace();
        }
        return representative;
    }

    private static int byWitnessesDescending (Node a, Node b, Transaction tx) {
        Integer aCount = new ReadingModel(a, tx).getWitnesses().size();
        Integer bCount = new ReadingModel(b, tx).getWitnesses().size();
        return bCount.compareTo(aCount);
    }

    /**
     *
     */
    public static class RelatedReadingsTraverser implements Evaluator {
        private final HashMap<String, RelationTypeModel> ourTypes;
        private final Function<RelationTypeModel, Boolean> criterion;

        public RelatedReadingsTraverser(Node fromReading, Transaction tx) throws Exception {
            this(fromReading, x -> true, tx);
        }

        public RelatedReadingsTraverser(Node fromReading, Function<RelationTypeModel, Boolean> criterion, Transaction tx) throws Exception {
            this.criterion = criterion;
            // Make a lookup table of relation types
            ourTypes = new HashMap<>();
            ourRelationTypes(fromReading, tx).forEach(x -> ourTypes.put(x.getName(), x));
        }

        @Override
        public Evaluation evaluate (Path path) {
            // Keep going from the start node
            if (path.endNode().equals(path.startNode()))
                return Evaluation.EXCLUDE_AND_CONTINUE;
            // Check to see if the relation type satisfies our specified criterion
            if (!path.lastRelationship().hasProperty("type"))
                return Evaluation.EXCLUDE_AND_PRUNE;
            RelationTypeModel thisrtm = ourTypes.get(path.lastRelationship().getProperty("type").toString());
            if (criterion.apply(thisrtm))
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;

        }
    }

    public static class TransitiveRelationTraverser implements Evaluator {
        private final RelationTypeModel rtm;
        private final Node tradNode;
        private final Transaction tx;

        public TransitiveRelationTraverser(Node tradNode, RelationTypeModel reltypemodel, Transaction tx) {
            this.tradNode = tradNode;
            this.rtm = reltypemodel;
            this.tx = tx;
        }

        @Override
        public Evaluation evaluate(Path path) {
            if (path.endNode().equals(path.startNode()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            // If the relation isn't transitive, we don't follow it.
            if (!rtm.getIs_transitive())
                return Evaluation.EXCLUDE_AND_PRUNE;
            // If it's the same relation type, we do follow it.
            if (path.lastRelationship().getProperty("type").equals(rtm.getName()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            // If it's a different relation type, we follow it if it is bound more closely
            // than our type (lower bindlevel) and if that type is also transitive.

            RelationTypeModel othertm = returnRelationType(tradNode, path.lastRelationship().getProperty("type").toString(), this.tx);
            if (rtm.getBindlevel() > othertm.getBindlevel() && othertm.getIs_transitive())
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;
        }
    }

}
