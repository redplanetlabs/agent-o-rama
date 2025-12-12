#!/bin/sh

VERSION=$(cat VERSION)

lein pom
mvn deploy:deploy-file \
    -DgroupId=com.rpl \
    -DartifactId=agent-o-rama \
    -Dversion=$VERSION \
    -Dpackaging=jar \
    -Dfile=target/agent-o-rama-$VERSION.jar \
    -DpomFile=pom.xml \
    -DrepositoryId=nexus-public-releases \
    -Durl=https://nexus.redplanetlabs.com/repository/maven-public-releases
