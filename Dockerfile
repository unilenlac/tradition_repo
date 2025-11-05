FROM tomcat:9-jdk17
LABEL vendor=DHUniWien
ENV APOC_VERSION=5.22.0
ENV GDS_VERSION=2.8.0

# Update packages, install Graphviz
RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get install -y graphviz \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Make the data directories
RUN mkdir -p /var/lib/stemmarest/conf \
    && mkdir -p /var/lib/stemmarest/plugins \
    && chmod -R g+w /var/lib/stemmarest \
    && chmod -R +2000 /var/lib/stemmarest

# Copy the software and config
COPY target/stemmarest.war build/server.xml build/tomcat-users.xml build/web.xml /usr/local/tomcat/webapps/
COPY build/*.xml /usr/local/tomcat/conf/

# download latest apoc jar and place it in the plugins folder
RUN wget -O /var/lib/stemmarest/plugins/apoc-${APOC_VERSION}-core.jar \ 
    https://github.com/neo4j/apoc/releases/download/${APOC_VERSION}/apoc-${APOC_VERSION}-core.jar

# download latest graph data science jar and place it in the plugins folder
RUN wget -O /var/lib/stemmarest/plugins/neo4j-graph-data-science-${GDS_VERSION}.jar \
    https://github.com/neo4j/graph-data-science/releases/download/${GDS_VERSION}/neo4j-graph-data-science-${GDS_VERSION}.jar

# create an external anonymous volume for the database files
VOLUME /var/lib/stemmarest/data

# copy default config files into the conf directory
COPY misc/conf/ /var/lib/stemmarest/conf/

# Set the appropriate environment variable
ENV STEMMAREST_HOME=/var/lib/stemmarest

# Run the server
EXPOSE 8080
ENTRYPOINT ["catalina.sh"]
CMD ["run"]
