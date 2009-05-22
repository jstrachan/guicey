#!/bin/sh

VERSION=2.0
mvn deploy:deploy-file -DgroupId=org.guiceyfruit \
        -DartifactId=guice-all \
        -Dversion=$VERSION \
        -DuniqueVersion=false \
        -Dpackaging=jar \
        -DrepositoryId=guiceyfruit-release \
        -Durl=file:///java-workspace/guiceyfruit-releases \
                  -Dfile=build/dist/guice-2.0.jar

mvn deploy:deploy-file -DgroupId=org.guiceyfruit \
  -DartifactId=guice-servlet \
  -Dversion=$VERSION \
  -DuniqueVersion=false \
  -Dpackaging=jar \
  -DrepositoryId=guiceyfruit-release \
  -Durl=file:///java-workspace/guiceyfruit-releases \
  -Dfile=build/dist/guice-servlet-snapshot.jar        

#      -Durl=https://guiceyfruit.googlecode.com/svn/repo/releases \


