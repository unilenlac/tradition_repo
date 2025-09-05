package net.stemmaweb.rest;

import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
// import com.qmino.miredot.annotations.MireDotIgnore;
// import com.qmino.miredot.annotations.ReturnType;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.stemmaweb.Util;
import net.stemmaweb.exporter.TeiExporter;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.Database;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.apache.tika.Tika;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.*;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.stemmaweb.Util.*;
import static net.stemmaweb.exporter.TeiExporter.addRdgContent;

/**
 * The root of the REST hierarchy. Deals with system-wide collections of
 * objects.
 *
 * @author tla
 */
@Path("/")
public class Root {
    @Context ServletContext context;
    @Context UriInfo uri;
    // private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = Database.getInstance().session;
    /*
     * Delegated API calls
     */

    private static final String CLICHED_MESSAGE = "Hello World!";
    
    @GET
    @Produces("text/plain")
    @Operation(summary = "Get example data", description = "Returns a sample JSON response")
    @ApiResponse(responseCode = "200", description = "Successful operation")
    public String getHello() {
        return CLICHED_MESSAGE;
    }
   /*
    @GET
    @Path("{path: docs.*}")
    @MireDotIgnore
    public Response getDocs(@PathParam("path") String path) {
        if (path.equals("docs") || path.equals("docs/")) path = "docs/index.html";
        final String target = String.format("/WEB-INF/%s", path);
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(new File(context.getRealPath(target)));
            InputStream resource = context.getResourceAsStream(target);
            return Response.status(Response.Status.OK).type(mimeType).entity(resource).build();
        } catch (IOException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
    */

    /**
     * @param tradId - the ID of the tradition being queried
     */
    @Path("/tradition/{tradId}")
    public Tradition getTradition(@PathParam("tradId") String tradId) {
        GetTraditionFunction<Transaction, Node> getTraditionFunction = Util.getTraditionNode(tradId);
        return new Tradition(tradId, getTraditionFunction);
    }
    @Path("/novus/{tradId}")
    public Novus getNovusTradition(@PathParam("tradId") String tradId) throws Exception {
        GetTraditionFunction<Transaction, Node> getTraditionFunction = Util.getTraditionNode(tradId);
        Node node = getTraditionFunction.apply(db.beginTx());
        return new Novus(tradId, node);

    }
    /**
     * @param userId - The ID of a stemmarest user; this is usually either an email address or a Google ID token.
     */
    @Path("/user/{userId}")
    public User getUser(@PathParam("userId") String userId) {
        return new User(userId);
    }
    /**
     * @param readingId - the ID of the reading being queried
     */
    @Path("/reading/{readingId}")
    public Reading getReading(@PathParam("readingId") String readingId) {
        return new Reading(readingId);
    }
    /**
     * @param readingId - the ID of the complex reading being queried
     */
    @Path("/complexreading/{readingId}")
    public ComplexReading getComplexReading(@PathParam("readingId") String readingId) {
        return new ComplexReading(readingId);
    }

    /*
     * Resource creation calls
     */

    /**
     * Imports a new tradition from file data of various forms, and creates at least one section
     * in doing so. Returns the ID of the given tradition, in the form {@code {"tradId": <ID>}}.
     *
     * @title Upload new tradition
     *
     * @param name      the name of the tradition. Default is the empty string.
     * @param language  the language of the tradition text (e.g. Latin, Syriac).
     * @param direction the direction in which the text should be read. Possible values
     *                  are {@code LR} (left to right), {@code RL} (right to left), or {@code BI} (bidirectional).
     *                  Default is LR.
     * @param userId    the ID of the user to whom this tradition belongs. Required.
     * @param is_public If true, the tradition will be marked as publicly viewable.
     * @param filetype  the type of file being uploaded. Possible values are {@code collatex},
     *                  {@code cxjson}, {@code csv}, {@code tsv}, {@code xls}, {@code xlsx},
     *                  {@code graphml}, {@code stemmaweb}, or {@code teips}.
     *                  Required if 'file' is present.
     * @param empty     Should be set to some non-null value if the tradition is being created without any data file.
     *                  Required if 'file' is not present.
     * @param uploadedInputStream The file data to upload.
     * @param fileDetail The file data to upload.
     *
     * @statuscode 201 - The tradition was created successfully.
     * @statuscode 400 - No file was specified, and the 'empty' flag was not set.
     * @statuscode 409 - The requested owner does not exist in the database.
     * @statuscode 500 - Something went wrong. An error message will be returned.
     *
     */
    @Tag(name = "Tradition", description = "Tradition endpoints allow to operate over groups of text")
    @POST
    @Path("/tradition")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json; charset=utf-8")
    // @ReturnType("java.util.Map<String,String>")
    public Response importGraphMl(@DefaultValue("") @FormDataParam("name") String name,
                                  @FormDataParam("userId") String userId,
                                  @FormDataParam("public") String is_public,
                                  @FormDataParam("language") String language,
                                  @DefaultValue("LR") @FormDataParam("direction") String direction,
                                  @FormDataParam("empty") String empty,
                                  @FormDataParam("filetype") String filetype,
                                  @FormDataParam("file") InputStream uploadedInputStream,
                                  @FormDataParam("file") FormDataMultiPart fileDetail) throws KernelException {



        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(jsonerror("No user with this id exists"))
                .build();
        }

        if (fileDetail == null && uploadedInputStream == null && empty == null) {
            // No file to parse
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror("No file found")).build();
        }

        String tradId;
        try {
            tradId = this.createTradition(name, direction, language, is_public);
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        // Link the given user to the created tradition.
        try {
            this.linkUserToTradition(userId, tradId);
        } catch (Exception e) {
            // try (Transaction tx = db.beginTx()) {
            //     Tradition.delete_tradition(tradId, tx);
            //     tx.commit();
            // } catch (Exception e2) {
            // }
            System.out.println("Error deleting tradition: " + e.getMessage());
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        // If we got file contents, we should send them off for parsing.
        if (empty == null) {

            try(Transaction tx = db.beginTx()){
                // Node trad_node = tx.findNode(Nodes.TRADITION, "id", tradId);
                GetTraditionFunction<Transaction, Node> getTraditionFunction = Util.getTraditionNode(tradId);
                Node tradNode = getTraditionFunction.apply(tx);
                Response dataResult = Tradition.parseDispatcher(tradNode,
                        filetype, uploadedInputStream, false, tx);
                if (dataResult.getStatus() != Response.Status.CREATED.getStatusCode()) {
                    // If something went wrong, delete the new tradition immediately and return the error.
                    Tradition.delete_tradition(tradNode, tx);
                    return dataResult;
                }
                // If we just parsed GraphML (the only format that can preserve prior tradition IDs),
                // get the actual tradition ID in case it was preserved from a prior export.
                if (filetype.equals("graphml")) {
                    try {
                        JSONObject dataValues = new JSONObject(dataResult.getEntity().toString());
                        tradId = dataValues.get("parentId").toString();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return Response.serverError().entity(jsonerror("Bad file parse response")).build();
                    }
                }
            tx.commit();
            }catch (Throwable t){
                System.out.println("Error parsing file: " + t.getMessage());
                t.printStackTrace();
            }
        }

        // Handle direct non-Jersey calls from our test suite
        if (uri == null)
            return Response.status(Response.Status.CREATED).entity(jsonresp("tradId", tradId)).build();
        else
            return Response.created(uri.getRequestUriBuilder().path(tradId).build())
                .entity(jsonresp("tradId", tradId)).build();
    }

    /*
     * Collection calls
     */

    /**
     * Gets a list of all the complete traditions in the database.
     *
     * @title List traditions
     * @param publiconly    Returns only the traditions marked as being public.
     *                      Default is false.
     *
     * @return A list, one item per tradition, of tradition metadata.
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Path("/traditions")
    @Produces("application/json; charset=utf-8")
    // @ReturnType("java.util.List<net.stemmaweb.model.TraditionModel>")
    public Response getAllTraditions(@DefaultValue("false") @QueryParam("public") Boolean publiconly) {
        List<TraditionModel> traditionList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> nodeList;
            if (publiconly)
                nodeList = tx.findNodes(Nodes.TRADITION, "is_public", true);
            else
                nodeList = tx.findNodes(Nodes.TRADITION);
            nodeList.forEachRemaining(t -> traditionList.add(new TraditionModel(t, tx)));
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(traditionList).build();
    }

    /**
     * Gets a list of all the users in the database.
     *
     * @title List users
     *
     * @return A list, one item per user, of user metadata.
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Path("/users")
    @Produces("application/json; charset=utf-8")
    // @ReturnType("java.util.List<net.stemmaweb.model.UserModel>")
    public Response getAllUsers() {
        List<UserModel> userList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {

            tx.findNodes(Nodes.USER)
                    .forEachRemaining(t -> userList.add(new UserModel(t, tx)));
            tx.close();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(userList).build();
    }

    private String createTradition(String name, String direction, String language, String isPublic) {
        String tradId = UUID.randomUUID().toString();
        try (Transaction tx = db.beginTx()) {
            // Make the tradition node
            Node traditionNode = tx.createNode(Nodes.TRADITION);
            traditionNode.setProperty("id", tradId);
            // This has a default value
            traditionNode.setProperty("direction", direction);
            // The rest of them don't have defaults
            if (name != null)
                traditionNode.setProperty("name", name);
            if (language != null)
                traditionNode.setProperty("language", language);
            if (isPublic != null)
                traditionNode.setProperty("is_public", isPublic.equals("true"));
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return tradId;
    }

    private void linkUserToTradition(String userId, String tradId) throws Exception {
        try (Transaction tx = db.beginTx()) {
            Node userNode = tx.findNode(Nodes.USER, "id", userId);
            if (userNode == null) {
                tx.rollback();
                throw new Exception("There is no user with ID " + userId + "!");
            }
            Node traditionNode = tx.findNode(Nodes.TRADITION, "id", tradId);
            if (traditionNode == null) {
                tx.rollback();
                throw new Exception("There is no tradition with ID " + tradId + "!");
            }
            userNode.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
            tx.commit();
        }
    }
    @GET
    @Path("/sandbox")
    @Produces(MediaType.APPLICATION_XML)
    public Response sandox() throws XMLStreamException {

        String s_1 = "<element>1</element>";
        String s_2 = "test<element>1</element>.";
        String s_3 = "<element><bar>hello</bar></element>";
        String s_4 = "<element>foo string<bar>hello</bar>blob<bar>hello</bar></element>";
        String s_5 = "test<element>1</element>.<element>foo string<bar>hello</bar></element>";
        String s_6 = "test<element/>follow";
        String s_7 = "<element>foo string<bar/>hello</element>";
        String s_8 = "foo<element/>string<bar type='foo' param='bar'/>hello<baz/>world";
        String s_9 = "foo</nc>string<element>foo string<bar>hello</bar>blob<bar>hello</bar></element>foo<element>qux</element></nc>string";


        StringWriter result = new StringWriter();
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter writer;
        try {
            writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(result));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        writer.writeStartDocument();
        writer.writeStartElement("body");

        // return new TeiExporter(db).SimpleHnExporter(tradition, section);
        addRdgContent(s_8, writer);

        writer.writeEndElement();
        return Response.ok().entity(result.toString()).build();
    }
    @GET
    @Path("/tei_exporter")
    @Produces(MediaType.APPLICATION_XML)
    public Response teiExporter(@QueryParam("tradition") String tradition,
                                @QueryParam("section") String section) throws XMLStreamException {
        return new TeiExporter(db).SimpleHnExporter(tradition, section);
    }
    @GET
    @Path("/log_file")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLogFile() {
        File logFile = new File("logs/xml_export_download.log");
        if (!logFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("No log file found.").build();
        }
        try {
            InputStream resource = new FileInputStream(logFile);
            return Response.status(Response.Status.OK).type("text/plain").entity(resource)
                    .header("Content-Disposition", "attachment; filename=\"xml_export_download.log\"")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error reading log file: " + e.getMessage()).build();
        }
    }
}
