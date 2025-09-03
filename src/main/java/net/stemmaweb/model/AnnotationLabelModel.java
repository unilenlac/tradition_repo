package net.stemmaweb.model;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the model to specify a valid annotation label for a given text tradition.
 */

public class AnnotationLabelModel {
    /**
     * What is the label name for this type of annotation?
     */
    private String name;
    /**
     * What are the valid property keys on this annotation node, and what is the correct data type for each?
     * Valid types are anything treated as valid in neo4j, except Point which seems not well-implemented.
     * e.g. {'text': 'String', 'notBefore': 'LocalDate'}
     */
    private Map<String, String> properties;
    /**
     * Which types (labels) of nodes can this annotation annotate, and what relationship type(s) can be
     * used to link them?
     * e.g. {'READING': 'BEGINS,ENDS', '}
     */
    private Map<String, String> links;

    /**
     * Initialize a new model
     */
    public AnnotationLabelModel() {
        this.properties = new HashMap<>();
        this.links = new HashMap<>();
    }

    /**
     * Initialize from a Neo4J node
     * @param alNode - the node to init from
     */
    public AnnotationLabelModel(Node alNode, Transaction tx) {
        initFromNode(alNode, tx);
    }

    /**
     * Look up by tradition ID and name. Returns an empty AnnotationLabelModel if no relevant node is found.
     *
     * @param tradNode - the tradition ID
     * @param name   - the label name to look for
     */
    public AnnotationLabelModel(Node tradNode, String name, Transaction tx) {
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        Node alNode = null;
        try {
            // Node tradNode = tx.findNode(Nodes.TRADITION, "id", tradId);
            for (Relationship r : tradNode.getRelationships(Direction.OUTGOING, ERelations.HAS_ANNOTATION_TYPE))
                if (r.getEndNode().getProperty("name", "").equals(name)) {
                    alNode = r.getEndNode();
                }
        }catch (Exception e){
            e.printStackTrace();
        }
        if (alNode != null)
            initFromNode(alNode, tx);
    }

    private void initFromNode(Node annNode, Transaction tx) {
        // GraphDatabaseService db = annNode.getGraphDatabase();
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        try {
            // Look up the name
            this.setName(annNode.getProperty("name").toString());

            // Look up the list of properties
            properties = new HashMap<>();
            Relationship prel = annNode.getSingleRelationship(ERelations.HAS_PROPERTIES, Direction.OUTGOING);
            if (prel != null) {
                Node pnode = prel.getEndNode();
                for (String key : pnode.getPropertyKeys()) {
                    properties.put(key, pnode.getProperty(key).toString());
                }
            }

            // Look up the list of links
            links = new HashMap<>();
            Relationship lrel = annNode.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
            if (lrel != null) {
                Node lnode = lrel.getEndNode();
                for (String key : lnode.getPropertyKeys()) {
                    links.put(key, lnode.getProperty(key).toString());
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void addProperty(String key, String dtype) {
        this.properties.put(key, dtype);
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    public void addLink(String label, String types) {
        this.links.put(label, types);
    }
}
