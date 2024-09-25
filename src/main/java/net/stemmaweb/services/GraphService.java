package net.stemmaweb.services;

import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

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
        System.out.printf("graph %s is ready%n", res.next().get("graph"));
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
            // String log = String.format("rel: %s witnesses: %s weight: %s", rel.getElementId(), rel.getProperty("witnesses"), rel.getProperty("weight"));
            // System.out.println(log);
        }
        return tx.execute(section_query);
    }
}
