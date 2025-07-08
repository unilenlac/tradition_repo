package net.stemmaweb;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.Response;
import javax.xml.stream.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility functions for anything that needs a Response
 * Created by tla on 14/02/2018.
 */

public class Util {

    // Return a JSONified version of an error message
    public static String jsonerror (String message) {
        return jsonresp("error", message);
    }
    public static String jsonresp (String key, String message) {
        return String.format("{\"%s\": \"%s\"}", key, escape(message));
    }
    public static String jsonresp (String key, Long value) {
        return String.format("{\"%s\": %d}", key, value);
    }

    private Config config = Config.getInstance();

    private static String escape(String raw) {
        String escaped = raw;
        escaped = escaped.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\b", "\\b");
        escaped = escaped.replace("\f", "\\f");
        escaped = escaped.replace("\n", "\\n");
        escaped = escaped.replace("\r", "\\r");
        escaped = escaped.replace("\t", "\\t");
        // TODO: escape other non-printing characters using uXXXX notation
        return escaped;
    }
    public Map<String, Object> target_top_hn(List<Map<String, Object>> hn_list, List<Map<String, Object>>stats){
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
                        String string = node.getProperty("text").toString();
                        if(string!=null && string.length() > 0)
                            addRdgContent(string+" ", writer);
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
                        if(n!=null && n.length() > 0)
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
        writer.writeCharacters(" ");
    }
    public void addRdgContent(String rdgTextContent, XMLStreamWriter writer){

        List<String> nc_elements = new ArrayList<>(List.of(config.getExporterNcs().split(", ")));
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

    /**
     * Checks if the input string matches the given regex pattern.
     *
     * @param input The input string to check.
     * @param regex The regex pattern to match against.
     * @return true if the input matches the regex, false otherwise.
     */
    public static Boolean isElementId(String input, String regex){
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.matches();
    }
    @FunctionalInterface
    public interface GetTraditionFunction<T, R> {
        R apply(T arg1) throws Exception;
    }
    /**
     * Returns a function that retrieves a tradition node by its ID.
     * If the ID matches the regex for element IDs, it uses the transaction's getNodeByElementId method.
     * Otherwise, it uses the VariantGraphService to get the tradition node.
     *
     * @param tradId The tradition ID to retrieve.
     * @return A function that retrieves the tradition node.
     */
    public static GetTraditionFunction<Transaction, Node> getTraditionNode(String tradId){
        String re = "\\d+:.*:\\d+";
        boolean res = isElementId(tradId, re);
        if (res){
            return (tx) -> tx.getNodeByElementId(tradId);
        }else{
            return (tx) -> VariantGraphService.getTraditionNode(tradId, tx);
        }
    }
}
