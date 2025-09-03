package net.stemmaweb.services;

import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

import java.util.HashMap;
import java.util.Map;

public class GraphService {

    public void sectionGraph(Transaction tx, Long witness_count){

        // section graph can't be generated for the whole database
        String graph_query = String.format("MATCH (s:READING)-[r:SEQUENCE]->(t:READING)\n" +
                " WITH *, %s/size(r.witnesses) as wSize\n" +
                " WITH gds.graph.project(\n" +
                "  'section',\n" +
                "  s,\n" +
                "  t,\n" +
                "  { relationshipProperties: r { wSize } }\n" +
                ") as g" +
                " RETURN g.graphName AS graph", witness_count);
        Result res  = tx.execute(graph_query);
        // System.out.printf("graph %s is ready%n", res.next().get("graph"));
    }
    public void removeSectionGraph(Transaction tx){
        String related_graph = "RETURN gds.graph.exists('section') AS exists";
        Result g = tx.execute(related_graph);
        if (g.hasNext() && (Boolean) g.next().get("exists")) {
            String drop_graph = "CALL gds.graph.drop('section') YIELD graphName";
            Result drop_res = tx.execute(drop_graph);
            if(drop_res.hasNext()){
                // enforce graph drop
                drop_res.resultAsString();
            }
        }
    }
    public Result get_section(Node section, Long witnesses_count, Transaction tx){

        String section_query = String.format("MATCH (source:READING {text: \"#START#\"}), (target:READING {text: \"#END#\"})\n" +
                " WHERE source.section_id = \"%s\" AND target.section_id = \"%s\"\n" +
                " CALL gds.shortestPath.dijkstra.stream('section', {\n" +
                "    sourceNode: source,\n" +
                "    targetNodes: target,\n" +
                "    relationshipWeightProperty: 'wSize'\n" +
                " })\n" +
                " YIELD index, sourceNode, targetNode, totalCost, nodeIds, costs, path\n" +
                " RETURN\n" +
                "    index,\n" +
                "    gds.util.asNode(sourceNode).text AS sourceNodeName,\n" +
                "    gds.util.asNode(targetNode).text AS targetNodeName,\n" +
                "    totalCost,\n" +
                "    [nodeId IN nodeIds | gds.util.asNode(nodeId).text] AS nodeNames,\n" +
                "    costs,\n" +
                "    nodes(path) as path\n" +
                " ORDER BY index", section.getElementId(), section.getElementId());
        String relations_query = String.format("MATCH (n:READING)-[r:SEQUENCE]->(t:READING)\n" +
                " WHERE n.section_id = \"%s\"\n" +
                " WITH ID(r) as relId, elementId(r) as uid\n" +
                " RETURN relId, uid ORDER BY relId ASC", section.getElementId());

        this.removeSectionGraph(tx);
        this.sectionGraph(tx, witnesses_count);
        Result relations = tx.execute(relations_query);
        while (relations.hasNext()){
            Relationship rel = tx.getRelationshipByElementId(relations.next().get("uid").toString());
            String[] witnesses_length = (String[]) rel.getProperty("witnesses");
            rel.setProperty("weight", witnesses_length.length);
        }
        return tx.execute(section_query);
    }
    public Result getSectionByRank(String section_id, Transaction tx){
        /*
        get all readings in a section by rank
         */
        String query = String.format("MATCH (n:READING {section_id: \"%s\"})\n" +
                " WITH n.rank AS rank, collect(n) AS nodes\n" +
                " UNWIND nodes AS node\n" +
                " WITH rank, nodes, node\n" +
                " ORDER BY rank, id(node) ASC\n" +
                " WITH rank, collect(node)[0] AS selectedNode\n" +
                " RETURN elementId(selectedNode) as id\n" +
                " ORDER BY selectedNode.rank\n", section_id);
        return tx.execute(query);
    }
    public Result getTraditionWitnesses(String tradition_id, Transaction tx) {
        /*
        get all witnesses in a tradition
         */
        String query = String.format("MATCH (t:TRADITION {id: \"%s\"})-[:HAS_WITNESS]->(w:WITNESS)\n" +
                " RETURN w", tradition_id);
        return tx.execute(query);
    }
    public long getEndRank(String section_id, Transaction tx){

        Map<String, Object> properties = new HashMap<>();
        properties.put("text", "#END#");
        properties.put("section_id", section_id);
        ResourceIterator<Node> rank = tx.findNodes(Nodes.READING, properties);
        long end_rank = 0;
        try{
            if (rank.hasNext()){
                Node endNode = rank.next();
                if (endNode.hasProperty("rank")) {
                    end_rank = (long) endNode.getProperty("rank");
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return end_rank - 1;
    }
}
