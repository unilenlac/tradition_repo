package net.stemmaweb.builders;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import net.stemmaweb.Config;
import org.neo4j.graphdb.Transaction;

public interface DocumentBuilder {
    /**
     * The XML document to be built
     */
    void addHeaderToWriter(String tradition_id, XMLStreamWriter writer, Transaction tx, Config config) throws XMLStreamException;
    void addSectionToWriter(String tradition_id, String section_id, XMLStreamWriter writer, Transaction tx) throws XMLStreamException;
}
