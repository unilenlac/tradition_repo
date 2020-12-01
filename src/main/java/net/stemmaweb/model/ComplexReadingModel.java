package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ComplexReadingModel {
    /**
     * Expresses a complex reading, which is defined reccursively as either a simple reading or a list of complex readings.
     */

    /**
    * ID of the complex reading
    */
    private String id;

    /**
    * Basic data: Simple reading.
    */
    private ReadingModel reading;

    /**
    * Components of complex reading.
    */
    private List<ComplexReadingModel> components;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReadingModel getReading() {
        return reading;
    }

    public void setReading(ReadingModel reading) {
        this.reading = reading;
    }

    public void setComponents(List<ComplexReadingModel> components) {
        this.components = components;
    }

    public List<ComplexReadingModel> getComponents() {
        return components;
    }

    @SuppressWarnings("unused")
    public ComplexReadingModel() {
        this.id = "";
        this.components = new ArrayList<>();
        this.reading = null;
    }

    /**
    * Initialize using the the basic data (of reading type).
    */
    public ComplexReadingModel(ReadingModel reading) {
        this.id = "";
        this.components = null;
        this.reading = reading;
    }

    /**
    * Initialize reccursively using the HAS_HYPERNODE relations of the current node.
    */
    public ComplexReadingModel(Node node) {
        this.id = "";
        this.reading = null;
        this.components = null;
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(Long.toString(node.getId()));
            List<ComplexReadingModel> compReadings = new ArrayList<>();
              for (Relationship r: node.getRelationships(ERelations.HAS_HYPERNODE)) {
                  Node otherNode = r.getOtherNode(node);
                  if (otherNode.hasLabel(Nodes.HYPERREADING)) {
                    // if complex node: initialize reccursively with the component node
                    compReadings.add(new ComplexReadingModel(otherNode));
                  } else {
                    // if simple nodes: initialize with Reading
                    compReadings.add(new ComplexReadingModel(new ReadingModel(otherNode)));
                  }
              }
            this.setComponents(compReadings);
            tx.success();
        }
    }
}
