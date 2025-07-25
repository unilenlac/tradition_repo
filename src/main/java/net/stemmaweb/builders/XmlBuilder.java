package net.stemmaweb.builders;

import net.stemmaweb.Config;
import net.stemmaweb.Util;
import net.stemmaweb.documents.XmlDocument;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
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
    /**
     * Adds a section to the XML writer by processing the tradition and section nodes,
     * retrieving hypernodes, and populating the XML with relevant data.
     *
     * @param tradition_id The ID of the tradition being processed.
     * @param section_id The ID of the section being processed.
     * @param writer The XMLStreamWriter used to write the XML content.
     * @param tx The Neo4j transaction used for database operations.
     */
    public void addSectionToWriter(String tradition_id, String section_id, XMLStreamWriter writer, Transaction tx) {

        try {

            Node tradition_node = tx.findNode(Nodes.TRADITION, "id", tradition_id);
            Node section_node = tx.getNodeByElementId(section_id);

            writer.writeEmptyElement("milestone");
            writer.writeAttribute("n", section_node.getProperty("name").toString());
            
            Result hypernodes = util.getHypernodes(section_node, tx); // provides text, nodeId, nodeUuid, hyperId, group, witness, rank, is_lemma, weight
            Result stats = util.hn_stats(section_node, tx); // provides hid (hypernode id), rCount (reading count), group (hypernode group), witness
            
            List<Map<String, Object>> hn_table = hypernodes.stream().collect(Collectors.toList());
            List<Map<String, Object>> hn_stats = stats.stream().collect(Collectors.toList());

            Result section = graphService.getSectionByRank(section_id, tx);
            Result witnesses = graphService.getTraditionWitnesses(tradition_id, tx);
            
            List<String> sigils = new ArrayList<>();

            while (witnesses.hasNext()){
                Map<String, Object> next = witnesses.next();
                if (next.get("w") != null) {
                    Node witness = (Node) next.get("w");
                    sigils.add(witness.getProperty("sigil").toString());
                }
            }
            LinkedList<Node> node_section = new LinkedList<>();
            section.stream().map(x -> tx.getNodeByElementId(x.get("id").toString())).forEach(node_section::add);
            
            // each rank adds 1, each hypernode adds n
            long node_skip = 0L;

            while (!node_section.isEmpty()){

                Node node = node_section.removeFirst();
                if(node_skip == 0){
                    
                    // get all hypernode nodes for the actual rank
                    List<Map<String, Object>> hn_nodes = hn_table.stream().filter(x -> x.get("rank").equals(node.getProperty("rank"))).collect(Collectors.toList());

                    // get all node that share the same ranking, get the variant locus of the traversed nodes
                    Result variant_locus = util.variant_locus((String) node.getProperty("section_id"), (Long) node.getProperty("rank"), tx);
                    
                    // populate hyper-readings

                    // get main hn, the main hn should be the hn that has the traversed node as a member. the hn is not necessarily the largest one.
                    Optional<Map<String, Object>> lemma_hn = hn_nodes.stream().filter(x -> x.get("is_lemma").equals(true)).findAny();

                    if(lemma_hn.isPresent()){
                        
                        // hn_table: all data about the nodes linked to a hypernode (cf. line 91)
                        // from now on, hn means hypernode's nodes information
                        List<Map<String, Object>> grouped_hn = hn_table.stream().filter(x -> x.get("group").equals(lemma_hn.get().get("group"))).collect(Collectors.toList());
                        List<Map<String, Object>> grouped_hn_stats = hn_stats.stream().filter(x -> x.get("group").equals(lemma_hn.get().get("group"))).collect(Collectors.toList());

                        // hn_mat: complete the node table

                        List<Map<String, Object>> hn_mat = util.completeHnsNodeTable(grouped_hn, (long) node.getProperty("rank"), grouped_hn_stats, section_id, tx);
                        
                        // select all hn that are lemmas
                        // todo: check if it can be multiple lemmas here. for instance if there is nested hypernodes
                        List<Map<String, Object>> lemma_hn_list = hn_nodes.stream().filter(x -> x.get("is_lemma").equals(true)).collect(Collectors.toList());

                        // because the hn matrix has been set (completeHnsNodeMatrix), we have now to
                        // collect again all the information about the hypernodes state in the database
                        // collectionSectionHypernodes returns fresh stats, hypernodes and the reading variant nodes
                        Map<String, List<Map<String, Object>>> hn_infos = util.collectSectionHypernodes(section_node, new ArrayList<>(), tx);

                        // hn_object fields are : text, nodeId, nodeUuid, hyperId, note
                        Map<String, Object> top_hn_object = util.target_top_hn(lemma_hn_list, hn_stats);

                        writer.writeStartElement("app");
                        // todo : improve counting with grouped_hn
                        node_skip = util.count_nodes(top_hn_object.get("hyperId").toString(), tx)-1;
                        util.populateHypernodes(top_hn_object, hn_infos.get("hn_table"), hn_infos.get("hn_stats"), writer, sigils, section_node, new ArrayList<>(), hn_mat, tx);

                        writer.writeEndElement();
                    }
                    ReadingModel rdg = new ReadingModel(node, tx);
                    TraditionModel tradition = new TraditionModel(tradition_node, tx);
                    int trad_w_count = tradition.getWitnesses().size();
                    int rdg_w_count = rdg.getWitnesses().size();

                    // if reading witnesses length != tradition witnesses length, then it has a variation
                    boolean is_app = trad_w_count != rdg_w_count;
                    // populate variants outside hyper-readings
                    if(hn_nodes.isEmpty() && node_section.size() >= 1 && is_app){
                        util.populateVariants(node, variant_locus, writer, sigils, tx);
                    }
                    // populate non variating text on the main element
                    if(hn_nodes.isEmpty() && node_section.size() >= 1 && !is_app) {
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
