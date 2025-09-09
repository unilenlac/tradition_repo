# Stemmarest
### Stemmarest - a REST API for variant text traditions

Stemmarest is a [Neo4j](http://neo4j.com/)-based repository for variant text traditions (i.e. texts that have been transmitted in multiple manuscripts). The repository is accessed via a REST API

This repository is a fork of the [Stemmarest](https://dhuniwien.github.io/tradition_repo/) repository, that features critical edition exportation capabilities in XML format.

### Documentation

The original API documentation [can be found here](https://dhuniwien.github.io/tradition_repo/).

As the code has been upgraded, a few routes have been added or modified. Please refer to the swagger generated documentation for the latest information. This documentation is available when the backend is running at the `/stemmarest/api/docs` endpoint.

### The Stemmaweb environment

Originally the Stemmarest backend has been built to work with the Stemmaweb platform, which provides a web-based interface for exploring and editing these traditions.

The Stemmaweb codebase has been adapted to work with this API. The fork can be found [here](https://github.com/unilenlac/stemmaweb).

If you need a script to collate texts and import them into the stemmarest backend, you can use this [collate and import script](https://github.com/unilenlac/enlac). Please note that this script handles only XML texts that follows the [ENLAC DTD](https://github.com/unilenlac/martyre-philippe/blob/PM-1_master/tei-irsb.dtd) specification, but can be modified to suit your needs.

## Upgrades and features

This version includes the following upgrades and new features :

- **XML exportation : support for exporting a critical edition in XML format. Exportation result includes the critical text and the critical apparatus.**
- Route : /tradition/complex added to retrieve/manage complex variant texts or hypernode (groups of nodes)
- code base upgrading to fit the latest version of Neo4j
- Improved database session initialization and configuration
- Improved database session management through the application
- OpenAPI documentation generation for all endpoints available at the /stemmarest/api/docs endpoint

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

Neo4j embedded database stores his data and config files in the following directories:

- Data: `/var/lib/stemmarest/data`
- Config (neo4j): `/var/lib/stemmarest/conf`
- Plugin (neo4j): `/var/lib/stemmarest/plugins`

These folders can be used to mount volumes and persist data.

This API use the version 5.26 [LTS] of Neo4j for Java ([embedded version](https://neo4j.com/docs/java-reference/5/java-embedded/)).

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

## Todo

- Fix remaining tests that haven't been updated: the code is actually compiled without tests.
- Refactor code hierarchy: this code needs Separation of Concerns to be properly organized. Part of the code mainly concerned => Route logic, business logic, and data access logic should be separated.

## Background

Development of Stemmarest was begun by a team of software engineering students at the [University of Bern](https://www.unibe.ch/), and since 2016 has been continued by the [Digital Humanities group at the University of Vienna](https://acdh.univie.ac.at/) with financial support from the [Swiss National Science Foundation](http://www.snf.ch/en/Pages/default.aspx).

This fork is an extension of the original Stemmarest project, incorporating updates and improvements to include lacking exportation features. This version is still under development by the [Faculty of Theology and Religion](https://www.unil.ch/ftsr/fr/home.html) at the university of Lausanne.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For any questions or issues, please open an issue on the GitHub repository or contact the maintainer at [renato.diaz@unil.ch]

Feel free to contribute to the project by submitting pull requests !
