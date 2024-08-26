package net.stemmaweb.exporter;

import net.stemmaweb.services.GraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Traverser;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.VariantGraphService;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TeiExporter {
    private final GraphDatabaseService db;
    private final GraphService graphService = new GraphService();

    public TeiExporter(GraphDatabaseService db) {
        this.db = db;
    }

    public Response SimpleHnExporter(String section_id) throws XMLStreamException {

        StringWriter result = new StringWriter();
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter writer;
        try {
            writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(result));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        writer.writeStartDocument();
        writer.writeStartElement("TEI");
        writer.writeAttribute("xmlns", "http://www.tei-c.org/ns/1.0");

        writer.writeStartElement("text");
        writer.writeStartElement("body");
        writer.writeStartElement("div");
        writer.writeStartElement("p");

        try(Transaction tx = db.beginTx()){

            Node tradition_node = tx.getNodeByElementId("4:d9d77f98-2607-4616-97cb-50995b926dd7:112");
            Node section_node = tx.getNodeByElementId(section_id);
            Long witness_count = tradition_node.getRelationships(ERelations.HAS_WITNESS).stream().count();

            Node start_node = section_node.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            String rank = section_node.getSingleRelationship(ERelations.HAS_END, Direction.OUTGOING).getEndNode().getProperty("rank").toString();

            Result variants = getVariants(section_node, tx);
            Result hypernodes = getHypernodes(section_node, tx);

            List<Map<String, Object>> section_table = variants.stream().collect(Collectors.toList());
            List<Map<String, Object>> hn_table = hypernodes.stream().collect(Collectors.toList());

            Result section = graphService.get_section(section_node, witness_count, tx);

            Map<String, Map<String, Object>> tmp = hypernodes.stream().collect(Collectors.toMap(e -> e.get("nodeId").toString(), Function.identity()));

            Long node_skip = 0L;
            Long nodes_in_hn_count = 0L;

            while (section.hasNext()){
                ArrayList<Node> nodes = (ArrayList<Node>) section.next().get("path");
                for(Node node: nodes){
                    if(node_skip == 0){
                        List<Map<String, Object>> filtered_variants = section_table.stream().filter(x -> x.get("rank") == node.getProperty("rank")).collect(Collectors.toList());
                        List<Map<String, Object>> filtered_top_hn = hn_table.stream().filter(x -> Objects.equals(x.get("nodeUuid").toString(), node.getElementId())).collect(Collectors.toList());
                        List<Map<String, Object>> filtered_bottom_hn = null;
                        for (Map<String, Object> variant: filtered_variants){
                            if(!variant.get("nodeId").equals(node.getElementId())){
                                filtered_bottom_hn = hn_table.stream().filter(x -> Objects.equals(x.get("nodeUuid").toString(), variant.get("nodeId"))).collect(Collectors.toList());
                            }
                        }
                        if(filtered_top_hn.size() >=1){
                            writer.writeStartElement("app");
                            node_skip = nodes_in_hn_count = count_nodes(filtered_top_hn.get(0).get("hyperId").toString(), tx);
                            populate_variants(filtered_top_hn, hn_table, new HashMap<>(), section_table, writer, tx);
                            System.out.println(filtered_top_hn);
                            System.out.println(filtered_bottom_hn);
                            writer.writeEndElement();
                        }
                        // if (filtered_bottom_hn != null && filtered_bottom_hn.size() >= 1) {
                        //     for (Map<String, Object> hn: filtered_bottom_hn){
                        //         System.out.println(hn);
                        //         List<String> hn_nodes = hn_table.stream().filter(x -> x.get("hyperId").equals(hn.get("hyperId"))).map(x -> x.get("text").toString()).collect(Collectors.toList());
                        //         writer.writeStartElement("rdg");
                        //         for(String n: hn_nodes){
                        //             writer.writeCharacters(n+" ");
                        //         }
                        //     }
                        // }
                    }
                }
            }
        }

        writer.writeEndDocument();
        return Response.ok().entity(result.toString()).build();
    }

    public void populate_variants(List<Map<String, Object>> top_hn_list, List<Map<String, Object>> filtered_hn, Map<String, String> hn_stats, List<Map<String, Object>> variant_list, XMLStreamWriter writer, Transaction tx) throws XMLStreamException {
        Map<String, Object> top_hn = top_hn_list.get(0);
        List<String> nodes = filtered_hn.stream().filter(x -> x.get("hyperId").equals(top_hn.get("hyperId"))).map(x -> x.get("nodeUuid").toString()).collect(Collectors.toList());
        int hn_node_count = nodes.size();
        // List<String> remaining_nodes = filtered_hn.stream().filter(x -> !x.get("hyperId").equals(top_hn.get("hyperId"))).map(x -> x.get("nodeUuid").toString()).collect(Collectors.toList());
        List<Map<String, Object>> remaining_hn = filtered_hn.stream().filter(x -> !x.get("hyperId").equals(top_hn.get("hyperId"))).collect(Collectors.toList());
        String node_sample = nodes.get(0);

        writer.writeStartElement("lem");
        Long node_skip = 0L;
        for (String id: nodes){
            Long count = 0L;
            Node node = tx.getNodeByElementId(id);
            List<Map<String, Object>> local_top_hn = remaining_hn.stream().filter(x -> x.get("nodeUuid").equals(id)).collect(Collectors.toList());
            List<Map<String, Object>> variant_locus = variant_list.stream().filter(x -> x.get("rank").equals(node.getProperty("rank"))).collect(Collectors.toList());
            if(local_top_hn.size() >= 1 && variant_locus.size() > 1){
                node_skip = count_nodes(local_top_hn.get(0).get("hyperId").toString(), tx);
                writer.writeStartElement("app");
                populate_variants(local_top_hn, remaining_hn, new HashMap<>(), variant_list, writer, tx);
                writer.writeEndElement();
                remaining_hn = remaining_hn.stream().filter(x -> !x.get("hyperId").equals(local_top_hn.get(0).get("hyperId"))).collect(Collectors.toList());
            }else{
                if(count < node_skip){
                    count++;
                } else {
                    writer.writeCharacters(node.getProperty("text").toString()+" ");
                }
            }
        }
        List<Map<String, Object>> filtered_variants = variant_list.stream().filter(x -> x.get("rank") == tx.getNodeByElementId(node_sample).getProperty("rank")).collect(Collectors.toList());
        List<Map<String, Object>> filtered_bottom_hn = null;
        for (Map<String, Object> variant: filtered_variants){
            if(!variant.get("nodeId").equals(node_sample)){
                filtered_bottom_hn = remaining_hn.stream().filter(x -> Objects.equals(x.get("nodeUuid").toString(), variant.get("nodeId"))).collect(Collectors.toList());
            }
        }
        if (filtered_bottom_hn != null && filtered_bottom_hn.size() >= 1) {
            for (Map<String, Object> hn: filtered_bottom_hn){
                System.out.println(hn);
                List<String> hn_nodes = filtered_hn.stream().filter(x -> x.get("hyperId").equals(hn.get("hyperId"))).map(x -> x.get("text").toString()).collect(Collectors.toList());
                if(hn_nodes.size() == hn_node_count){
                    writer.writeStartElement("rdg");
                    for(String n: hn_nodes){
                        writer.writeCharacters(n+" ");
                    }
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();

    }

    public Long count_nodes(String hn, Transaction tx){
        String query = String.format("match (h:HYPERREADING)<-[r:HAS_HYPERNODE]-(n:READING)\n" +
                "where elementId(h) = \"%s\"\n" +
                "return count(n) as countNode", hn);
        return (Long) tx.execute(query).next().get("countNode");
    }

    public Response SimpleExporter(String section_id) throws XMLStreamException {

        StringWriter result = new StringWriter();
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter writer;
        try {
            writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(result));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        writer.writeStartDocument();
        writer.writeStartElement("TEI");
        writer.writeAttribute("xmlns", "http://www.tei-c.org/ns/1.0");

        writer.writeStartElement("text");
        writer.writeStartElement("body");
        writer.writeStartElement("div");
        writer.writeStartElement("p");

        try(Transaction tx = db.beginTx()){

            Node tradition_node = tx.getNodeByElementId("4:d9d77f98-2607-4616-97cb-50995b926dd7:112");
            Node section_node = tx.getNodeByElementId(section_id);
            Long witness_count = tradition_node.getRelationships(ERelations.HAS_WITNESS).stream().count();

            Node start_node = section_node.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            String rank = section_node.getSingleRelationship(ERelations.HAS_END, Direction.OUTGOING).getEndNode().getProperty("rank").toString();

            Result variants = getVariants(section_node, tx);
            List<Map<String, Object>> table = variants.stream().collect(Collectors.toList());
            Result section = graphService.get_section(section_node, witness_count, tx);

            // while (section.hasNext()){
            //     Object nodes = section.next().get("path");
            //     System.out.println(nodes.toString());
            // }
            // Traverser td = VariantGraphService.simpleTraverser(tx, Integer.parseInt(rank), witness_count).traverse(start_node);

            while (section.hasNext()){
                ArrayList<Node> nodes = (ArrayList<Node>) section.next().get("path");
                for(Node node: nodes){

                    System.out.println(node);

                    List<Map<String, Object>> filtered_variants = table.stream().filter(x -> x.get("rank") == node.getProperty("rank")).collect(Collectors.toList());
                    System.out.println(filtered_variants);
                    if(filtered_variants.size() == 1) {
                        if(!Objects.equals(node.getProperty("text").toString(), "#START#") && !Objects.equals(node.getProperty("text").toString(), "#END#")){
                            writer.writeCharacters(node.getProperty("text").toString()+" ");
                        }
                    } else {
                        List<Map<String, String>> app_elements = new ArrayList<>();
                        writer.writeStartElement("app");
                        for(Map<String, Object> row: filtered_variants){

                            if(Objects.equals(node.getElementId(), row.get("nodeId").toString())){
                                // xmlement.put("el", "lem");
                                // xmlement.put("text", node.getProperty("text").toString());
                                Node rdg_node = tx.getNodeByElementId(row.get("nodeId").toString());
                                HashMap<String, String> xmlement = find_lemma(rdg_node);
                                if(Objects.equals(xmlement.get("el"), "lem")){
                                    app_elements.add(0, xmlement);
                                }else{
                                    app_elements.add(xmlement);
                                }
                            }else{
                                Node rdg_node = tx.getNodeByElementId(row.get("nodeId").toString());
                                HashMap<String, String> xmlement = find_lemma(rdg_node);
                                if(Objects.equals(xmlement.get("el"), "lem")){
                                    app_elements.add(0, xmlement);
                                }else{
                                    app_elements.add(xmlement);
                                }
                            }
                        }
                        for(Map<String, String> el: app_elements){
                            writer.writeStartElement(el.get("el"));
                            writer.writeCharacters(el.get("text"));
                            writer.writeEndElement();
                        }
                        writer.writeEndElement();
                    }
                    System.out.println(node.toString());
                }
            }
        }

        writer.writeEndDocument();
        return Response.ok().entity(result.toString()).build();
    }

    public HashMap<String, String> find_lemma(Node node){
        /*
        use relationships (RELATED) between node in a collocation to  define wich node is the lemma reading
        todo: create an ordered list to reflect the polarsation
         */
        HashMap<String, String> xmlelement = new HashMap<>();
        if (node.getDegree(ERelations.RELATED, Direction.INCOMING) >= 1 && node.getDegree(ERelations.RELATED, Direction.OUTGOING) == 0){
            xmlelement.put("el", "lem");
            xmlelement.put("text", node.getProperty("text").toString());
        } else {
            xmlelement.put("el", "rdg");
            xmlelement.put("text", node.getProperty("text").toString());
        };
        return xmlelement;
    }

    public Result getVariants(Node section_node, Transaction tx){

        String variant_query = String.format("MATCH (source:READING)\n" +
                " WHERE source.section_id = \"%s\"\n" +
                " WITH source, source.rank as rank\n" +
                " RETURN elementId(source) as nodeId, rank ORDER BY rank", section_node.getElementId());
        return tx.execute(variant_query);
    }
    public Result getHypernodes(Node section_node, Transaction tx){
        /*
        get section hypernodes and all linked nodes
         */
        String query = String.format("match (r:READING)-[l:HAS_HYPERNODE]->(h:HYPERREADING)\n" +
                " WHERE r.section_id=\"%s\"\n" +
                " WITH r.text as text, id(r) as nodeId, elementId(r) as nodeUuid, elementId(h) as hyperId\n" +
                " RETURN text, nodeId, nodeUuid, hyperId ORDER BY nodeId ASC", section_node.getElementId());
        return tx.execute(query);
    }
    public Result hn_stats(Node section_node, Transaction tx){
        /*
        get section hypernodes and
        Node frequency per hypernodes

        hid: hypernodes id
        rCount: reading counts per hypernodes
         */
        String query = String.format("match (r:READING)-[l:HAS_HYPERNODE]->(h:HYPERREADING)\n" +
                " WHERE r.section_id=\"%s\"\n" +
                " with elementId(h) as hid, count(h) as rCount\n" +
                " return *", section_node.getElementId());
        return tx.execute(query);
    }
    public Result node_hn_stats(Node section_node, Transaction tx){
        String query = String.format("match (r:READING)-[l:HAS_HYPERNODE]->(h:HYPERREADING)\n" +
                " WHERE r.section_id=\"%s\"\n" +
                " with elementId(r) as ruid, count(r) as rCount, collect(elementId(h)) as huid, r.text as text, id(r) as rid, r.rank as rank\n" +
                " return text, ruid, rCount, huid, rank ORDER BY rid ASC", section_node.getElementId());
        return tx.execute(query);
    }
}
