package net.stemmaweb.rest;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.*;
import net.stemmaweb.services.*;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.rest.Util.jsonerror;

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
    private final Long readId;

    public ComplexReading(String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        // The requested ID might have an 'n' prepended, if it was taken from the SVG output.
        readId = Long.valueOf(requestedId.replaceAll("n", ""));
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
            reading = new ComplexReadingModel(db.getNodeById(readId));
            tx.success();
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
          Node removableNode = db.getNodeById(readId);
          removableNode.getRelationships().forEach(Relationship::delete);
          removableNode.delete();
          tx.success();
      } catch (Exception e) {
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
