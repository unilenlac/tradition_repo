/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.stemmaweb.services;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

//import org.apache.log4j.Logger;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;

import java.util.Objects;

/**
 *
 * @author marijn
 */
public class ApplicationContextListener implements ServletContextListener {

    private static final String DB_ENV = System.getenv("STEMMAREST_HOME");
    private static final String DB_PATH = DB_ENV == null ? "/var/lib/stemmarest" : DB_ENV;
    // final static Logger logger = Logger.getLogger(ApplicationContextListener.class);
    @SuppressWarnings("unused")
    private ServletContext context = null;

    private final GraphService graph_service = new GraphService();

    public void contextDestroyed(ServletContextEvent event) {
        //Output a simple message to the server's console
        try {
            GraphDatabaseServiceProvider serviceProvider = new GraphDatabaseServiceProvider();
            DatabaseManagementService managementService = serviceProvider.getManagementService();
            managementService.shutdown();
            System.out.println("SHUTDOWN PROCESS : neo4j database closed");
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.context = null;

    }

    public void contextInitialized(ServletContextEvent event) {
        this.context = event.getServletContext();
        //Output a simple message to the server's console
        // logger.debug("This is debug - Listener: context initialized");
        try {
            GraphDatabaseService db = new GraphDatabaseServiceProvider(DB_PATH).getDatabase();
            DatabaseService.createRootNode(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}