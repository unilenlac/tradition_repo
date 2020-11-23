package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.*;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ComplexReadingModel {
    /**
     * Expresses a complex reading, which is made up of a several readings.
     */

    /**
    * ID of the complex reading
    */
    private String id;

    /**
    * Components of complex reading.
    */
    private List<ReadingModel> readings;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setReadings(List<ReadingModel> readings) {
        this.readings = readings;
    }

    public List<ReadingModel> getReadings() {
        return readings;
    }

    @SuppressWarnings("unused")
    public ComplexReadingModel() {
        readings = new ArrayList<>();
    }

    /**
    * Set readings as the nodes in a HAS_HYPERNODE relation with the current node.
    */
    public ComplexReadingModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(Long.toString(node.getId()));
            List<ReadingModel> compReadings = new ArrayList<>();
            for (Relationship r: node.getRelationships(ERelations.HAS_HYPERNODE)) {
                compReadings.add(new ReadingModel(r.getOtherNode(node)));
            }
            this.setReadings(compReadings);
            tx.success();
        }
    }
}
