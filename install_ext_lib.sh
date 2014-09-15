#!/bin/bash

echo "### Installing alcatraz-lib to local mvn repo"
mvn install:install-file -Dfile=ext_lib/alcatraz-lib/alcatraz-lib.jar \
-DgroupId=at.falb.games.alcatraz -DartifactId=alcatraz-lib \
-Dversion=1.0 -Dpackaging=jar \
-Djavadoc=ext_lib/alcatraz-lib/alcatraz-doc.zip \
-DlocalRepositoryPath=mvn_rep

echo "### Installing spread-java-api to local mvn repo"
mvn install:install-file -Dfile=ext_lib/spread/spread-4.0.0.jar \
-DgroupId=org.spread -DartifactId=spread \
-Dversion=4.0.0 -Dpackaging=jar \
-DlocalRepositoryPath=mvn_rep
