package net.stemmaweb.services;

import net.stemmaweb.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.stemmaweb.services.GraphDatabaseServiceProvider.registerShutdownHook;

public final class Database {
    private static volatile Database instance;

    public GraphDatabaseService session;
    public DatabaseManagementService dbService;

    private Database() {
        // todo: allow to set the database path from outside
        // Initialize the database service with the configuration

        List<String> unrestricted_list = new ArrayList<>();
        Config config = Config.getInstance();
        boolean test_mode = config.getNeo4jTest();
        Path db_location = Path.of(config.getNeo4jPath());
        Path utils_path = Path.of(config.getNeo4jUtilsPath());
        File n4j_config = new File(db_location + "/conf/neo4j.conf");
        // Path of = Path.of(utils_path + "/");
        unrestricted_list.add("gds.*");
        Path tempDbPath;
        try{
            tempDbPath = Files.createTempDirectory("neo4j-debug-test");
        }catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory for Neo4j database", e);
        }
        // LogProvider logProvider = new CustomLogProvider();

        // Path db_path = Path.of("/Users/rdiaz/Documents/testdb");
        Path db_path = test_mode ? tempDbPath : db_location;
        DatabaseManagementServiceBuilder dbServiceBuilder = new DatabaseManagementServiceBuilder(db_path);

        if (n4j_config.exists()) {
            dbServiceBuilder.setConfig(GraphDatabaseSettings.max_concurrent_transactions, 0)
                            .loadPropertiesFromFile(n4j_config.toPath());
        }

        dbServiceBuilder.setConfig(GraphDatabaseSettings.plugin_dir, Path.of(utils_path + "/plugins"))
                        .setConfig(GraphDatabaseSettings.logs_directory, Path.of(utils_path+ "/logs"))
                        .setConfig(GraphDatabaseSettings.procedure_unrestricted, unrestricted_list);

        this.dbService = dbServiceBuilder.build();
        this.session = dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        registerShutdownHook(this.dbService);
    }

    public static Database getInstance() {

        Database result = instance;
        if (result != null) {
            return result;
        }
        synchronized(Database.class) {
            if (instance == null) {
                instance = new Database();
            }
            return instance;
        }
    }
}