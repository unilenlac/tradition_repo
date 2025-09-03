package net.stemmaweb.exporter;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphService;
// import org.neo4j.cypher.internal.expressions.In;
import org.neo4j.graphdb.*;
import net.stemmaweb.rest.ERelations;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

import javax.ws.rs.core.Response;
import javax.xml.stream.*;
import java.io.StringReader;
import java.io.StringWriter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeiExporter {
    private final GraphDatabaseService db;
    private final GraphService graphService = new GraphService();

    public TeiExporter(GraphDatabaseService db) {
        this.db = db;
    }

    public Response SimpleHnExporter(String tradition_id, String section_id) throws XMLStreamException {

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
        /*
        writer.writeStartElement("TEI");
        writer.writeAttribute("xmlns", "http://www.tei-c.org/ns/1.0");

        writer.writeStartElement("text");
        writer.writeStartElement("body");
        writer.writeStartElement("div");
        writer.writeStartElement("p");

         */

        try(Transaction tx = db.beginTx()){

            Node tradition_node = tx.findNode(Nodes.TRADITION, "id", tradition_id);
            Node section_node = tx.getNodeByElementId(section_id);
            Long witness_count = tradition_node.getRelationships(ERelations.HAS_WITNESS).stream().count();

            writer.writeEmptyElement("milestone");
            writer.writeAttribute("n", section_node.getProperty("name").toString());

            //Node start_node = section_node.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            //String rank = section_node.getSingleRelationship(ERelations.HAS_END, Direction.OUTGOING).getEndNode().getProperty("rank").toString();

            Result variants = getVariants(section_node, tx);
            Result hypernodes = getHypernodes(section_node, tx);
            Result stats = hn_stats(section_node, tx);

            List<Map<String, Object>> variant_table = variants.stream().collect(Collectors.toList());
            List<Map<String, Object>> hn_table = hypernodes.stream().collect(Collectors.toList());
            List<Map<String, Object>> hn_stats = stats.stream().collect(Collectors.toList());

            Result section = graphService.getSectionByRank(section_id, tx);
            LinkedList<Node> node_section = new LinkedList<>();
            section.stream().map(x -> tx.getNodeByElementId(x.get("id").toString())).forEach(node_section::add);

            long node_skip = 0L;

            while (!node_section.isEmpty()){

                Node node = node_section.removeFirst();
                if(node_skip == 0){
                    // get all hypernodes linked to traversed section node
                    List<Map<String, Object>> node_hns = hn_table.stream().filter(x -> x.get("rank").equals(node.getProperty("rank"))).collect(Collectors.toList());

                    // get all node that share the same ranking, get the variant locus of the traversed nodes
                    Result variant_locus = variant_locus((String) node.getProperty("section_id"), (Long) node.getProperty("rank"), tx);

                    // later we gonna need to count all the variants without exhausting the variant locus iterator, this a basic list of all reading that share the same rank in the section
                    List<Map<String, Object>> filtered_variants = variant_table.stream().filter(x -> x.get("rank") == node.getProperty("rank")).collect(Collectors.toList());

                    // populate hyper-readings

                    // get main hn, the main hn should be the hn that has the most nodes and that has the traversed node as a member

                    Optional<Map<String, Object>> top_hn = node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).findAny();

                    if(!top_hn.isEmpty()){

                        List<Map<String, Object>> top_hn_list = node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).collect(Collectors.toList());
                        // hn_object fields are : text, nodeId, nodeUuid, hyperId, note
                        Map<String, Object> top_hn_object = target_top_hn(top_hn_list, hn_stats);
                        writer.writeStartElement("app");
                        node_skip = count_nodes(top_hn_object.get("hyperId").toString(), tx)-1;
                        populateHypernodes(top_hn_object, hn_table, hn_stats, variant_table, writer, tx);
                        writer.writeEndElement();
                    }
                    ReadingModel rdg = new ReadingModel(node, tx);
                    TraditionModel tradition = new TraditionModel(tradition_node, tx);
                    int trad_w_count = tradition.getWitnesses().size();
                    int rdg_w_count = rdg.getWitnesses().size();
                    boolean is_app = trad_w_count != rdg_w_count;
                    // populate variants outside hyper-readings
                    if(node_hns.isEmpty() && node_section.size() >= 1 && is_app){
                        populateVariants(node, variant_locus, writer, tx);
                    }
                    // populate non variating text on the main element
                    if(node_hns.isEmpty() && node_section.size() >= 1 && !is_app) {
                        // todo replace with start/endnode
                        if(!Objects.equals(node.getProperty("text").toString(), "#START#") && !Objects.equals(node.getProperty("text").toString(), "#END#")){
                            if(node_section.size() == 1){
                                addRdgContent(node.getProperty("text").toString(), writer);
                            } else {
                                addRdgContent(node.getProperty("text").toString() + " ", writer);
                            }
                        }
                    }
                } else {
                    node_skip--;
                }
            }
        }

        writer.writeEndDocument();
        return Response.ok().entity(result.toString()).build();
    }

    public Map<String, Object> target_top_hn(List<Map<String, Object>>hn_list, List<Map<String, Object>>stats){
        /*
        if a node is linked to multiple hypernodes that have the "main" mention,
        this method returns the largest hypernode group and allow to keep hypernode processing hierarchy
         */
        List<Map<String, Object>>filtered_stats = stats.stream().filter(s -> hn_list.stream().anyMatch(hn -> s.get("hid").equals(hn.get("hyperId")))).sorted(Comparator.comparingInt(x -> Integer.parseInt(x.get("rCount").toString()))).distinct().collect(Collectors.toList());
        Map<String, Object> top_hn_id = filtered_stats.get(filtered_stats.size() - 1);
        return hn_list.stream().filter(x -> x.get("hyperId").equals(top_hn_id.get("hid"))).findFirst().orElse(null);
    }
    public List<Map<String, Object>> populateHypernodes(Map<String, Object> top_hn, List<Map<String, Object>> filtered_hn, List<Map<String, Object>> hn_stats, List<Map<String, Object>> variant_list, XMLStreamWriter writer, Transaction tx) throws XMLStreamException {
        /*
        top_hn_list : list of all hn connected to the traversed section node
        filtered_hn : contains the list of all hypernodes, also contains a state of the hypernodes when a list of node is traversed
        on the first iteration the hn_list is complete
        */

        List<Node> nodes = get_hn_nodes(top_hn, tx);

        int hn_node_count = nodes.size();
        List<Map<String, Object>> remaining_hn = filtered_hn.stream().filter(x -> !x.get("hyperId").equals(top_hn.get("hyperId"))).collect(Collectors.toList());
        Node node_sample = nodes.get(1);
        ReadingModel rdg_sample = new ReadingModel(node_sample, tx);
        writer.writeStartElement("lem");
        writer.writeAttribute("wit", rdg_sample.getWitnesses().stream().map(x -> "#"+x).collect(Collectors.joining(" ")));

        // add rank attribute to the hypernode
        // todo: remove this when job is done
        writer.writeAttribute("rank", nodes.stream().map(x -> x.getProperty("rank").toString()).collect(Collectors.joining(", ")));

        Long node_skip = 0L;
        Long count = 0L;
        for (Node node: nodes){

            List<Map<String, Object>> local_node_hns = remaining_hn.stream().filter(x -> x.get("rank").equals(node.getProperty("rank"))).collect(Collectors.toList());
            Optional<Map<String, Object>> local_top_hn = local_node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).findAny();
            Result variant_locus = variant_locus((String) node.getProperty("section_id"), (Long) node.getProperty("rank"), tx);
            if (local_top_hn.isPresent()){
                List<Map<String, Object>> local_top_hn_list = local_node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).collect(Collectors.toList());
                Map<String, Object> top_hn_node = target_top_hn(local_top_hn_list, hn_stats);
                node_skip = count_nodes(top_hn_node.get("hyperId").toString(), tx)-1;
                writer.writeStartElement("app");
                remaining_hn = populateHypernodes(top_hn_node, remaining_hn, hn_stats, variant_list, writer, tx);
                writer.writeEndElement();
                remaining_hn = remaining_hn.stream().filter(x -> !x.get("hyperId").equals(local_top_hn.get().get("hyperId"))).collect(Collectors.toList());
            } else {
                if(count < node_skip){
                    count++;
                } else {
                    int node_relations = node.getDegree(ERelations.RELATED);
                    if (node_relations > 0){
                        populateVariants(node, variant_locus, writer, tx);
                    } else {
                        addRdgContent(node.getProperty("text").toString()+" ", writer);
                    }
                }
            }
        }
        writer.writeEndElement();

        List<Map<String, Object>> filtered_variants = variant_list.stream().filter(x -> x.get("rank") == tx.getNodeByElementId(node_sample.getElementId()).getProperty("rank")).collect(Collectors.toList());
        List<Map<String, Object>> filtered_bottom_hn = new ArrayList<>();
        for (Map<String, Object> variant: filtered_variants){
            if(!variant.get("nodeId").equals(node_sample) || variant.get("nodeId").equals(node_sample) && filtered_variants.size() == 1){
                filtered_bottom_hn.addAll(remaining_hn.stream().filter(x -> Objects.equals(x.get("nodeUuid").toString(), variant.get("nodeId"))).collect(Collectors.toList()));
            }
        }
        if (filtered_bottom_hn.size() >= 1) {
            filtered_bottom_hn.sort((m1, m2) -> {
                Integer w1 = (Integer) m1.get("weight");
                Integer w2 = (Integer) m2.get("weight");
                return w1.compareTo(w2);
            });
            for (Map<String, Object> hn: filtered_bottom_hn){

                List<Node> tmp_nodes = get_hn_nodes(hn, tx);
                List<String> hn_nodes = tmp_nodes.stream().map(x -> x.getProperty("text").toString()).collect(Collectors.toList());
                if(hn_nodes.size() == hn_node_count){
                    writer.writeStartElement("rdg");
                    ReadingModel w_node = new ReadingModel(tmp_nodes.get(1), tx);
                    writer.writeAttribute("wit", w_node.getWitnesses().stream().map(x -> "#"+x).collect(Collectors.joining(" ")));
                    for(String n: hn_nodes){
                        addRdgContent(n+" ", writer);
                    }
                    writer.writeEndElement();
                }
            }
        }
        return remaining_hn;
    }

    public List<Node> get_hn_nodes(Map<String, Object> top_hn, Transaction tx){

        String hn_node_query = String.format("MATCH (h:HYPERREADING)<-[l:HAS_HYPERNODE]-(r:READING)\n" +
                "WHERE elementId(h) = \"%s\"\n" +
                "RETURN r as node ORDER BY id(l) ASC", top_hn.get("hyperId"));

        Result node_res = tx.execute(hn_node_query);
        List<Node> nodes = new ArrayList<>();
        while(node_res.hasNext()){
            Node tmp_node = (Node) node_res.next().get("node");
            nodes.add(tmp_node);
        }
        return nodes;
    }

    public void populateVariants(Node section_node, Result filtered_variants, XMLStreamWriter writer, Transaction tx) throws XMLStreamException {
        LinkedList<HashMap<String, String>> app_elements = new LinkedList<>();
        writer.writeStartElement("app");
        writer.writeAttribute("rank", section_node.getProperty("rank").toString());
        while (filtered_variants.hasNext()){
            Node variant = (Node) filtered_variants.next().get("node");
            if(Objects.equals(section_node.getProperty("section_id"), variant.getProperty("section_id"))){
                HashMap<String, String> xmlement = find_lemma(variant);
                ReadingModel rdg = new ReadingModel(variant, tx);
                xmlement.put("wit", rdg.getWitnesses().stream().map(x -> "#"+x).collect(Collectors.joining(" ")));
                app_elements.add(xmlement);
            }
        }
        for(Map<String, String> el: app_elements){
            writer.writeStartElement(el.get("el"));
            writer.writeAttribute("wit", el.get("wit"));
            addRdgContent(el.get("text"), writer);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }
    public static void addRdgContent(String rdgTextContent, XMLStreamWriter writer){
        List<String> nc_elements = new ArrayList<>(List.of("gap", "unclear", "space", "c"));
        try{
            List<String> elements = splitTextAndXml(cleanText(rdgTextContent));
            for (String el : elements) {
                if (isValidXml(el)) {
                    XMLInputFactory inputFactory = XMLInputFactory.newInstance();
                    XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(el.trim()));

                    while (reader.hasNext()) {

                        int event = reader.next();
                        switch (event) {
                            case XMLStreamReader.START_ELEMENT:
                                if (nc_elements.contains(reader.getLocalName())){
                                    writer.writeEmptyElement(reader.getLocalName());
                                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                                        writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                                    }
                                    break;
                                }
                                writer.writeStartElement(reader.getLocalName());
                                for (int i = 0; i < reader.getAttributeCount(); i++) {
                                    writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                                }
                                break;
                            case XMLStreamReader.CHARACTERS:
                                String text = reader.getText().trim();
                                if (!text.isEmpty()) {
                                    writer.writeCharacters(text);
                                }
                                break;
                            case XMLStreamReader.END_ELEMENT:
                                if (nc_elements.contains(reader.getLocalName())){
                                    break;
                                }
                                writer.writeEndElement();
                                break;
                        }
                    }
                    reader.close();
                } else {
                    writer.writeCharacters(el);
                }
            }
        } catch (Exception e) {
            System.out.println("Error adding new element: " + e.getMessage());
        }
    }
    /**
     * Checks if the given element is a non-enclosing XML element.
     *
     * @param element The XML element to check.
     * @return true if the element is a non-enclosing XML element, false otherwise.
     */
    public static boolean isNonEnclosingElement(String element) {
        // Check if the element is a non-enclosing XML element
        return element.matches("<\\w+.*?/>");
    }
    /**
     * Cleans the input text by removing all new line characters and tabs.
     *
     * @param input The input text to be cleaned.
     * @return The cleaned text.
     */
    public static String cleanText(String input){

        String res = null;
        Pattern xmlPattern = Pattern.compile("\\R|\\t");
        Matcher matcher = xmlPattern.matcher(input);

        while(matcher.find()){
            res = matcher.replaceAll("");
        }
        if(res != null){

            return res;
        }
        return input;
    }
    /**
     * Splits the input text into a list of strings, separating text and XML elements.
     *
     * @param input The input text to be split.
     * @return A list of strings containing the separated text and XML elements.
     */
    public static List<String> splitTextAndXml(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return result;
        }

        Pattern xmlPattern = Pattern.compile("<(?<tag>\\w+).*>(.*?)<\\/(\\k<tag>)>");

        Matcher matcher = xmlPattern.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String text = input.substring(lastEnd, matcher.start());
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            String xml = matcher.group();
            result.add(xml);
            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            String text = input.substring(lastEnd);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }

        if (result.isEmpty() && !input.isEmpty()) {
            result.add(input);
        }

        return result;
    }
    /**
     * Checks if the given XML string is valid.
     *
     * @param xmlString The XML string to check.
     * @return true if the XML string is valid, false otherwise.
     */
    public static boolean isValidXml(String xmlString) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlString));
            while (reader.hasNext()) {
                reader.next();
            }
            reader.close();
            return true;
        } catch (Exception e) {
            return false;
        }
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
        if (node.hasProperty("is_lemma")){
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
                " WITH r.text as text, id(r) as nodeId, elementId(r) as nodeUuid, elementId(h) as hyperId, h.note as note, r.rank as rank, h.is_lemma as is_lemma, h.weight as weight\n" +
                " RETURN text, nodeId, nodeUuid, hyperId, note, rank, is_lemma, weight ORDER BY nodeId ASC", section_node.getElementId());
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
    public Result variant_locus(String section_id, Long rank, Transaction tx){
        String query = String.format("MATCH (n:READING {section_id: '%s', rank: %d})\n" +
                "WHERE NOT (n)-[:RELATED]->(:READING)\n" +
                "WITH n as nn\n" +
                "MATCH (p:READING {section_id: '%s', rank: %d})-[:RELATED]->*(nn)\n" +
                "RETURN p as node", section_id, rank, section_id, rank);
        return tx.execute(query);
    }
}
