package net.stemmaweb.documents;

import java.io.StringWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

public class XmlDocument {

    StringWriter result;
    XMLStreamWriter writer;

    public XmlDocument() throws XMLStreamException {

        this.result = new StringWriter();
        XMLOutputFactory output = XMLOutputFactory.newInstance();

        try {
            this.writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(this.result));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            throw new XMLStreamException("Error creating XML stream writer", e);
        }
    }
    public XMLStreamWriter getWriter() {
        return this.writer;
    }
    public String getDocument() {
        return this.result.toString();
    }
}
