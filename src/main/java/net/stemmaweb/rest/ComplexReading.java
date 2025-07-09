package net.stemmaweb.rest;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.*;
import net.stemmaweb.services.*;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.Util.jsonerror;

/**
 * Comprises all Rest API calls related to a complex reading. Can be called via
 * http://BASE_URL/complexreading
 *
 * @author VS, IRSB
 */

public class ComplexReading {

    private String errorMessage; // global error message used for sub-method calls

    private final GraphDatabaseService db;
    /**
     * The ID of the reading to query
     */
    private final String readId;

    public ComplexReading(String requestedId) {

        db = Database.getInstance().session;
        readId = requestedId;
    }

    /**
     * Returns the metadata for a single complex reading.
     *
     * @summary Get a complex reading
     * @return The complex reading information as a JSON structure.
     * @statuscode 200 - on success
     * @statuscode 204 - if the reading doesn't exist
     * @statuscode 500 - on error, with an error message
    */
    @GET
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = ComplexReadingModel.class)
    public Response getComplexReading() {
        ComplexReadingModel reading;
        try (Transaction tx = db.beginTx()) {
            reading = new ComplexReadingModel(tx.getNodeByElementId(String.valueOf(readId)), tx);
        } catch (NotFoundException e) {
            return Response.noContent().build();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(reading).build();
    }


    /**
     * Deletes a complex reading.
     *
     * @summary Delete a complex reading
     */
    @DELETE
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response deleteUserComplexReading() {
      try (Transaction tx = db.beginTx()) {
          Node removableNode = tx.getNodeByElementId(readId.toString());
          removableNode.getRelationships().forEach(Relationship::delete);
          removableNode.delete();
          tx.commit();
      } catch (Exception e) {
          e.printStackTrace();
          return Response.serverError().entity(jsonerror(e.getMessage())).build();
      }
      return Response.ok().build();
    }

    // POST request json object parser
    public static class ComplexReadingUpdateModel{

        @JsonProperty("id")
        String id;

        @JsonProperty("islemma")
        Boolean islemma;

        @JsonProperty("source")
        String source;


        @JsonProperty("note")
        String note;

        @JsonProperty("weight")
        int weight;

        public ComplexReadingUpdateModel(){

        }
        public ComplexReadingUpdateModel(String id,
                                         Boolean islemma,
                                         String source,
                                         String note,
                                         int weight){
            this.id = id;
            this.islemma = islemma;
            this.source = source;
            this.note = note;
            this.weight = weight;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    public Response saveComplex(ComplexReadingUpdateModel cr){

        try(Transaction tx = db.beginTx()){
            Node complex_reading = tx.getNodeByElementId(cr.id);
            complex_reading.setProperty("is_lemma", cr.islemma);
            complex_reading.setProperty("weight", cr.weight);
            complex_reading.setProperty("note", cr.note);
            complex_reading.setProperty("source", cr.source);
            tx.commit();
        }catch(Exception e){
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok().build();
    }

    // Class-level utility function to encapsulate the instance-wide error message
    private Response errorResponse (Status status) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(jsonerror(errorMessage)).build();
    }

}
