package net.stemmaweb;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Properties;

public class Config {
    private static final Config INSTANCE = new Config();
    private final Properties props;

    // Private constructor for singleton
    private Config() {
        props = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IllegalStateException("application.properties not found");
            }
            props.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading properties: " + e.getMessage());
        }
    }

    // Get singleton instance
    public static Config getInstance() {
        return INSTANCE;
    }

    public String getTraditionPublisher() {
        return props.getProperty("pub.publisher", "IRSB - Projet FNS « Éditer numériquement la littérature apocryphe chrétienne » (ENLAC) ");
    }

    public String getTraditionPubPlace() {
        return props.getProperty("pub.pubplace", "Lausanne");
    }

    public String getTraditionPudDate() {
        return props.getProperty("pub.date", String.valueOf(LocalDate.now()));
    }

    public String getExporterNcs() {
        return props.getProperty("exporter.ncs", "gap, unclear, space, c");
    }
    public Boolean getNeo4jTest() {
        return props.getProperty("neo4j.test", "false").equalsIgnoreCase("true");
    }
    public String getNeo4jPath() {
        return props.getProperty("neo4j.path", "");
    }
    public String getNeo4jUtilsPath () {
        return props.getProperty("neo4j.utils.path", "/var/lib/stemmarest");
    }
}