package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
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

    private String note;

    private String source;

    private Boolean is_lemma;

    private int weight;

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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getIs_lemma() {
        return is_lemma;
    }

    public void setIs_lemma(Boolean is_lemma) {
        this.is_lemma = is_lemma;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }



    @SuppressWarnings("unused")
    public ComplexReadingModel() {
        this.id = "";
        this.components = new ArrayList<>();
        this.reading = null;
    }

    /**
    * Initialize using the basic data (of reading type).
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
        this.note = node.hasProperty("note") ? node.getProperty("note").toString() : null;
        this.source = node.hasProperty("source") ? node.getProperty("source").toString() : null;
        this.is_lemma = node.hasProperty("is_lemma") ? (Boolean) node.getProperty("is_lemma") : false;
        this.weight = node.hasProperty("weight") ? (int) node.getProperty("weight") : 0;

        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();

        try (Transaction tx = db.beginTx()) {
            setId(node.getElementId());
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
            tx.commit();
        }
    }
}