package net.stemmaweb.services;

import net.stemmaweb.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.stemmaweb.services.GraphDatabaseServiceProvider.registerShutdownHook;

public final class Database {
    private static volatile Database instance;

    public GraphDatabaseService session;
    public DatabaseManagementService dbService;

    private Database() {
        // Initialize the database service with the configuration

        List<String> unrestricted_list = new ArrayList<>();
        Config config = Config.getInstance();
        String db_location = config.getNeo4jPath();
        File n4j_config = new File(db_location + "/conf/neo4j.conf");
        Path of = Path.of(db_location + "/");
        unrestricted_list.add("gds.*");

        DatabaseManagementServiceBuilder dbServiceBuilder = new DatabaseManagementServiceBuilder(of);

        if (n4j_config.exists()) {
            dbServiceBuilder.setConfig(GraphDatabaseSettings.max_concurrent_transactions, 0)
                            .loadPropertiesFromFile(Path.of(of + "/conf/neo4j.conf"));
        }

        dbServiceBuilder.setConfig(GraphDatabaseSettings.plugin_dir, Path.of(of + "/plugins"))
                        .setConfig(GraphDatabaseSettings.logs_directory, Path.of(of+ "/logs"))
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
        // if (instance == null) {
        //     instance = new Database();
        // }
        // return instance;
    }
}