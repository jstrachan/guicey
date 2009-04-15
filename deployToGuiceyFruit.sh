#!/bin/sh

mvn deploy:deploy-file -DgroupId=org.guiceyfruit \
        -DartifactId=guice-all \
        -Dversion=2.0-SNAPSHOT \
        -DuniqueVersion=false \
        -Dpackaging=jar \
        -DrepositoryId=guiceyfruit-release \
        -Durl=file:///java-workspace/guiceyfruit-releases \
        -Dfile=build/guice-with-deps.jar 
        

#      -Durl=https://guiceyfruit.googlecode.com/svn/repo/releases \


