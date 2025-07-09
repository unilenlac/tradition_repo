package net.stemmaweb.builders;

import net.stemmaweb.Config;
import net.stemmaweb.Util;
import net.stemmaweb.documents.XmlDocument;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

public class XmlBuilder implements DocumentBuilder{
    /**
     * The XML document to be built
     */

    private final Util util;
    public final XmlDocument document;

    private final GraphService graphService = new GraphService();
    public XmlBuilder() throws XMLStreamException {
        this.document = new XmlDocument();
        this.util = new Util();
    }
    public void addHeaderToWriter(String tradition_id, XMLStreamWriter writer, Transaction tx, Config config) throws XMLStreamException {
        Node tradition_node = tx.findNode(Nodes.TRADITION, "id", tradition_id);

        TraditionModel tradition = new TraditionModel(tradition_node, tx);
        ArrayList<String> witnesses = tradition.getWitnesses();

        //title description
        writer.writeStartElement("fileDesc");

            writer.writeStartElement("titleStmt");
            writer.writeStartElement("title");
            writer.writeCharacters(tradition.getName());
            writer.writeEndElement();
            writer.writeEndElement();

            // publication description
            writer.writeStartElement("publicationStmt");

            writer.writeStartElement("publisher");
            writer.writeCharacters(config.getTraditionPublisher());
            writer.writeEndElement();
            writer.writeStartElement("pubPlace");
            writer.writeCharacters(config.getTraditionPubPlace());
            writer.writeEndElement();
            writer.writeStartElement("date");
            writer.writeCharacters(config.getTraditionPudDate());
            writer.writeEndElement();

            writer.writeEndElement();

            // witnesses description
            writer.writeStartElement("sourceDesc");

            writer.writeStartElement("listWit");
            for (String witness : witnesses) {
                writer.writeStartElement("witness");
                writer.writeAttribute("xml:id", witness);
                writer.writeEndElement();
            }
            writer.writeEndElement();

            writer.writeEndElement();

        writer.writeEndElement();

    }
    public void addSectionToWriter(String tradition_id, String section_id, XMLStreamWriter writer, Transaction tx) {

        try {

            Node tradition_node = tx.findNode(Nodes.TRADITION, "id", tradition_id);
            Node section_node = tx.getNodeByElementId(section_id);

            writer.writeEmptyElement("milestone");
            writer.writeAttribute("n", section_node.getProperty("name").toString());

            Result variants = util.getVariants(section_node, tx);
            Result hypernodes = util.getHypernodes(section_node, tx);
            Result stats = util.hn_stats(section_node, tx);

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
                    Result variant_locus = util.variant_locus((String) node.getProperty("section_id"), (Long) node.getProperty("rank"), tx);

                    // later we gonna need to count all the variants without exhausting the variant locus iterator, this a basic list of all reading that share the same rank in the section
                    List<Map<String, Object>> filtered_variants = variant_table.stream().filter(x -> x.get("rank") == node.getProperty("rank")).collect(Collectors.toList());

                    // populate hyper-readings

                    // get main hn, the main hn should be the hn that has the most nodes and that has the traversed node as a member

                    Optional<Map<String, Object>> top_hn = node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).findAny();

                    if(!top_hn.isEmpty()){

                        List<Map<String, Object>> top_hn_list = node_hns.stream().filter(x -> x.get("is_lemma").equals(true)).collect(Collectors.toList());
                        // hn_object fields are : text, nodeId, nodeUuid, hyperId, note
                        Map<String, Object> top_hn_object = util.target_top_hn(top_hn_list, hn_stats);
                        writer.writeStartElement("app");
                        node_skip = util.count_nodes(top_hn_object.get("hyperId").toString(), tx)-1;
                        util.populateHypernodes(top_hn_object, hn_table, hn_stats, variant_table, writer, tx);
                        writer.writeEndElement();
                    }
                    ReadingModel rdg = new ReadingModel(node, tx);
                    TraditionModel tradition = new TraditionModel(tradition_node, tx);
                    int trad_w_count = tradition.getWitnesses().size();
                    int rdg_w_count = rdg.getWitnesses().size();
                    boolean is_app = trad_w_count != rdg_w_count;
                    // populate variants outside hyper-readings
                    if(node_hns.isEmpty() && node_section.size() >= 1 && is_app){
                        util.populateVariants(node, variant_locus, writer, tx);
                    }
                    // populate non variating text on the main element
                    if(node_hns.isEmpty() && node_section.size() >= 1 && !is_app) {
                        // todo replace with start/endnode
                        if(!Objects.equals(node.getProperty("text").toString(), "#START#") && !Objects.equals(node.getProperty("text").toString(), "#END#")){
                            if(node_section.size() == 1){
                                util.addRdgContent(node.getProperty("text").toString(), writer);
                            } else {
                                util.addRdgContent(node.getProperty("text").toString() + " ", writer);
                            }
                        }
                    }
                } else {
                    node_skip--;
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }
    public String getDocument() {
        return this.document.getDocument();
    }
}
