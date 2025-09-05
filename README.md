# Stemmarest
### Stemmarest - a REST API for variant text traditions

Stemmarest is a [Neo4j](http://neo4j.com/)-based repository for variant text traditions (i.e. texts that have been transmitted in multiple manuscripts). The repository is accessed via a REST API, documentation for which [can be found here](https://dhuniwien.github.io/tradition_repo/).

This repository is a fork of the [stemmarest](https://dhuniwien.github.io/tradition_repo/) repository.

## Background

Development of Stemmarest was begun by a team of software engineering students at the [University of Bern](https://www.unibe.ch/), and since 2016 has been continued by the [Digital Humanities group at the University of Vienna](https://acdh.univie.ac.at/) with financial support from the [Swiss National Science Foundation](http://www.snf.ch/en/Pages/default.aspx).

This fork is an extension of the original Stemmarest project, incorporating updates and improvements to include lacking exportation features. This version is still under development by the [Faculty of Theology and Religion](https://www.unil.ch/ftsr/fr/home.html) at the university of Lausanne.

## Upgrading and features

This version includes the following upgrades and new features :

- code base upgrading to fit the latest version of Neo4j
- additional export formats for variant texts (XML)
- Improved database session initialization and configuration
- Improved database session management through the application
- Route : /tradition/complex added to retrieve complex variant texts or hypernode (groups of nodes)

## Docker

You can get a version of Stemmarest via Docker Hub:

    docker pull unilenlac/stemmarest:1.2-tomcat9-jdk17

Then you can run a basic version of the container with:

    docker run -it --rm --name stemmarest --network stemmaweb -p 8080:8080 unilenlac/stemmarest:1.2-tomcat9-jdk17

Please note that this version is compiled for an ARM architecture. AMD version will be soon provided.

Debugging while the container is running is possible by opening a port and running a debugging server over the application. You also must use the JAVA_TOOL_OPTIONS environment variable to configure the debugger.

```
docker run -itd --rm --name stemmarest --network stemmaweb -p 8080:8080 -p 5005:5005 -e JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n"
```

### Database

please find bellow the directories where the Neo4j embedded database stores his data and config files:

- Data: `/var/lib/stemmarest/data`
- Config (neo4j): `/var/lib/stemmarest/conf`
- Plugin (neo4j): `/var/lib/stemmarest/plugins`

These folders can be used to mount volumes and persist data.

## Building

Stemmarest needs to be built using [Maven](http://maven.apache.org/run-maven/index.html#Quick_Start). This can be done either in a suitable Java IDE, or at the command line after the Maven tools have been installed:

    mvn clean && mvn package -Dmaven.test.skip.exec=true

Please note, that this command will skip the tests as they are not fully upgraded on this version.

Make sure, that the package graphviz is installed on your computer. If not, some tests will fail.     

A WAR file will be produced, in `target/stemmarest.war`, that can then be deployed to the Tomcat server of your choice.

## Deployment

This version runs on Tomcat version 9 with JDK 17; to deploy it, copy the WAR file into the `webapps` directory of your Tomcat server.

Stemmarest requires a location for its data storage; by default this is `/var/lib/stemmarest`, but can be changed by setting the environment variable `STEMMAREST_HOME`. The directory specified must have its permissions set so that the Tomcat user can write to it.

Note that if, at any time, you wish to inspect the database visually, you may shut down the Stemmarest server and start an instance of Neo4J at the database directory location. **Make sure that your version of Neo4J matches the version specified in `pom.xml`!**
