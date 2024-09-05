package net.stemmaweb.services;


import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// import org.neo4j.common.DependencyResolver.SelectionStrategy;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
//import org.neo4j.gds.wcc.WccStreamProc;
//import org.neo4j.gds.metrics.MetricsFacade;
//import org.neo4j.gds.ml.pipeline.node.regression.configure.NodeRegressionPipelineConfigureSplitProc;
import org.neo4j.graphdb.GraphDatabaseService;
//import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
//import org.neo4j.test.TestDatabaseManagementServiceBuilder;
//import scala.reflect.runtime.Settings;

/**
 * Creates a global DatabaseService provider, which holds a reference to the
 * database in use.
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {

    private static GraphDatabaseService db;
    private static DatabaseManagementService dbService;

    // Get the database that has been initialized for the app
    public GraphDatabaseServiceProvider() {
    }

	public GraphDatabaseServiceProvider(Boolean test){
		if (test){
			List<String> unrestricted_list = new ArrayList<>();
			unrestricted_list.add("gds.*");
			dbService = new DatabaseManagementServiceBuilder(Path.of("/somewhere/on/disk"))
					.setConfig(GraphDatabaseSettings.plugin_dir, Path.of("/location/of/plugins"))
					.setConfig(GraphDatabaseSettings.procedure_unrestricted, unrestricted_list)
					.build();
			db = dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
		}
	}

    // Connect to a DB at a particular path
    public GraphDatabaseServiceProvider(String db_location) throws KernelException {

		File config = new File(db_location + "/conf/neo4j.conf");
		if (config.exists()){
			dbService = new DatabaseManagementServiceBuilder(Path.of(db_location + "/"))
					.setConfig(GraphDatabaseSettings.max_concurrent_transactions, 0)
					.loadPropertiesFromFile( Path.of( db_location + "/conf/neo4j.conf" ) )
					.build();
		}else{
			dbService = new DatabaseManagementServiceBuilder(Path.of(db_location + "/")).build();
		}
		db = dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);

    	registerShutdownHook(dbService);
    	// registerExtensions();

    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) throws KernelException {
        db = existingdb;
        // registerExtensions();
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

	public DatabaseManagementService getManagementService() { return dbService; }


    // Register any extensions we need in the database
    private static void registerExtensions() throws KernelException {
        GraphDatabaseAPI api = (GraphDatabaseAPI) db;
        //See if our procedure is already registered
        api.getDependencyResolver();
				//.resolveDependency(NodeRegressionPipelineConfigureSplitProc.class).metricsFacade.projectionMetrics();
    }


    private static void registerShutdownHook( final DatabaseManagementService managementService ) {
    	Runtime.getRuntime().addShutdownHook( new Thread()
    	{
    		@Override
    		public void run()
    		{
    			managementService.shutdown();
    		}
    	} );
    }
}
