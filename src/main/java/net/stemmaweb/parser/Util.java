package net.stemmaweb.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.compress.utils.IOUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.internal.helpers.collection.Iterables;
import org.w3c.dom.Document;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;

/**
 * Utility functions for the parsers
 * Created by tla on 14/02/2017.
 */
public class Util {

    // Start and end node creation
    static Node createStartNode(Node parentNode, Transaction tx) {

        Node startNode;
        Node parentNodeTx = tx.getNodeByElementId(parentNode.getElementId());
        startNode = tx.createNode(Nodes.READING);
        startNode.setProperty("is_start", true);
        startNode.setProperty("section_id", parentNode.getElementId());
        startNode.setProperty("rank", 0L);
        startNode.setProperty("text", "#START#");
        parentNodeTx.createRelationshipTo(startNode, ERelations.COLLATION);

        return startNode;
    }

    // Start and end node creation
    static Node createEndNode(Node parentNode, Long rank, Transaction tx) {
        // GraphDatabaseService db = parentNode.getGraphDatabase();
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        Node endNode;
        // try (Transaction tx = db.beginTx()) {
            // Node parentNodeTx = tx.getNodeByElementId(parentNode.getElementId());
	        endNode = tx.createNode(Nodes.READING);
	        endNode.setProperty("is_end", true);
	        endNode.setProperty("section_id", parentNode.getElementId());
	        endNode.setProperty("text", "#END#");
            endNode.setProperty("rank", rank);
	        parentNode.createRelationshipTo(endNode, ERelations.HAS_END);
	    //     tx.commit();
        // }
        return endNode;
    }

    // Witness node creation
    static Node createWitness(Node traditionNode, String sigil, Boolean hypothetical, Transaction tx) throws IllegalArgumentException {
        // First check if the sigil has any characters that will cause trouble for REST
        for (String illegal : new String[] {"<", ">", "#", "%", "\"", "{", "}", "|", "\\", "^", "`", "(", ")"})
            if (sigil.contains(illegal))
                throw new IllegalArgumentException("The character " + illegal + " may not appear in a sigil name.");
        Node witnessNode;

        Node trad = tx.getNodeByElementId(traditionNode.getElementId());
        witnessNode = tx.createNode(Nodes.WITNESS);
        witnessNode.setProperty("sigil", sigil);
        witnessNode.setProperty("hypothetical", hypothetical);
        witnessNode.setProperty("quotesigil", !isDotId(sigil));
        trad.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);

        return witnessNode;
    }

    static Node findOrCreateExtant(Node traditionNode, String sigil, Transaction tx) {
        // This list should contain either zero or one items.

        ArrayList<Node> existingWit = DatabaseService.getRelatedWitness(traditionNode, ERelations.HAS_WITNESS, sigil, tx);

        if (existingWit.size() == 0) {
            return createWitness(traditionNode, sigil, false, tx);
        } else {
            return existingWit.get(0);
        }
    }

    static void ensureSectionLink (Node traditionNode, Node sectionNode, Transaction tx) {
        // GraphDatabaseService db = traditionNode.getGraphDatabase();
        // GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        try  {
            // String tradId = traditionNode.getProperty("id").toString();
            ArrayList<Node> tsections = VariantGraphService.getSectionNodes(traditionNode, tx);
            if (!tsections.contains(sectionNode)) {
                traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
                if (!tsections.isEmpty())
                    tsections.get(tsections.size()-1).createRelationshipTo(sectionNode, ERelations.NEXT);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

    // XML parsing utilities
    static Document openFileStream(InputStream filestream) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Zip parsing utilities - public because also used by test suite
    // Returns a structure which is a list of zip
    public static LinkedHashMap<String,File> extractGraphMLZip(InputStream is) throws IOException {
        LinkedHashMap<String,File> result = new LinkedHashMap<>();
        BufferedInputStream buf = new BufferedInputStream(is);
        ZipInputStream zipIn = new ZipInputStream(buf);
        ZipEntry ze;
        while ((ze = zipIn.getNextEntry()) != null) {
            // SOMEDAY can we do this without writing out to a file?
            String zfName = ze.getName();
            File someTmp = File.createTempFile(zfName, "");
            FileOutputStream fo = new FileOutputStream(someTmp);
            IOUtils.copy(zipIn, fo);
            fo.close();
            zipIn.closeEntry();
            result.put(zfName, someTmp);
        }
        zipIn.close();
        return result;
    }

    public static void cleanupExtractedZip(LinkedHashMap<String,File> result) throws IOException {
        for (File f : result.values())
            Files.deleteIfExists(f.toPath());
    }

    // Helper to get any existing SEQUENCE link between two readings.
    // NOTE: For use inside a transaction
    static Relationship getSequenceIfExists (Node source, Node target) {
        Relationship found = null;
        Iterable<Relationship> allseq = source.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);
        for (Relationship r : allseq) {
            if (r.getEndNode().equals(target)) {
                found = r;
                break;
            }
        }
        return found;
    }

    // Helper to set colocation flags on all colocated RELATED links.
    // NOTE: For use inside a transaction
    static void setColocationFlags (Node traditionNode) throws IOException {
        HashSet<String> colocatedTypes = new HashSet<>();
        for (Relationship r : traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE)) {
            RelationTypeModel relType = new RelationTypeModel(r.getEndNode());
            if (relType.getIs_colocation()) colocatedTypes.add(relType.getName());
        }

        // Traverse the tradition looking for these types
        for (Relationship rel : VariantGraphService.returnTraditionRelations(traditionNode).relationships()) {
            if (colocatedTypes.contains(rel.getProperty("type").toString()))
                rel.setProperty("colocation", true);
            else if (rel.hasProperty("colocation"))
                rel.removeProperty("colocation");
        }
    }

    @SuppressWarnings("rawtypes")
    public static PathExpander getExpander (Direction d, String stemmaName) {
        final String pStemmaName = stemmaName;
        return new PathExpander() {
            @Override
            public ResourceIterable expand(Path path, BranchState branchState) {
                ArrayList<Relationship> goodPaths = new ArrayList<>();
                for (Relationship link : path.endNode()
                        .getRelationships(d, ERelations.TRANSMITTED)) {
                    if (link.getProperty("hypothesis").equals(pStemmaName)) {
                        goodPaths.add(link);
                    }
                }
				return Iterables.resourceIterable(goodPaths);
            }

            @Override
            public PathExpander reverse() {
                return null;
            }
        };
    }

}
