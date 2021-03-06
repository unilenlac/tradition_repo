package net.stemmaweb.model;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * This is a model for the duplicateReadings method.
 * It consists two lists, one of reading ids to be duplicated 
 * and the witnesses which will be used for the new path
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class DuplicateModel {
    /**
     * A list of reading IDs as strings
     */
    private List<String> readings;
    /**
     * A list of witness sigla as strings
     */
    private List<String> witnesses;

    public List<String> getReadings() {
        return readings;
    }

    public void setReadings(List<String> readings) {
        this.readings = readings;
    }

    public List<String> getWitnesses() {
        return witnesses;
    }

    public void setWitnesses(List<String> witnesses) {
        this.witnesses = witnesses;
    }

}
