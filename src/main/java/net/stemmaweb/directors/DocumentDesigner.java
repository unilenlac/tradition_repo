package net.stemmaweb.directors;

import net.stemmaweb.builders.DocumentBuilder;
import net.stemmaweb.builders.XmlBuilder;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;

public class DocumentDesigner {

    public XmlBuilder builder;
    private final GraphDatabaseService db;
    public DocumentDesigner(XmlBuilder builder, GraphDatabaseService db){
        this.db = db;
        this.builder = builder;
    }
    public void designSection(String tradition_id, String section_id) throws XMLStreamException {
        XMLStreamWriter writer = builder.document.getWriter();
        writer.writeStartDocument();
        writer.writeStartElement("div");
        try(Transaction tx = db.beginTx()){
            builder.addSectionToWriter(tradition_id, section_id, writer, tx);
        }
        writer.writeEndElement();
        writer.writeEndDocument();
    }
    public void designTradition(String tradition_id, String start_section_id, String end_section_id) throws XMLStreamException {
        XMLStreamWriter writer = builder.document.getWriter();
        writer.writeStartDocument();
        writer.writeStartElement("TEI");
        writer.writeAttribute("xmlns", "http://www.tei-c.org/ns/1.0");

        writer.writeStartElement("text");
        writer.writeStartElement("body");
        writer.writeStartElement("div");
        writer.writeStartElement("p");
        try(Transaction tx = db.beginTx()){

            Node tradition_node = tx.findNode(Nodes.TRADITION, "id", tradition_id);

            ArrayList<SectionModel> sectionList = Tradition.produceSectionList(tradition_node, tx);
            ArrayList<SectionModel> sectionListFiltered = new ArrayList<>();

            boolean withinRange = false;

            for (SectionModel section : sectionList) {
                if (section.getId().equals(start_section_id)) {
                    withinRange = true;
                }
                if (withinRange) {
                    sectionListFiltered.add(section);
                }
                if (section.getId().equals(end_section_id)) {
                    break;
                }
            }
            for (SectionModel section : sectionListFiltered) {
                String section_id = section.getId();
                // Call the builder to add the section content
                builder.addSectionToWriter(tradition_id, section_id, writer, tx);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        writer.writeEndDocument();
    }
}
