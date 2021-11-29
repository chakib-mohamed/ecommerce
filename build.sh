#!/bin/bash


buildFront ()
{
 # Build front end
 npm --prefix ./frontend  run build
 docker-compose build ecommerce-front
}

buildApi ()
{
 # Build api
 mvn package -f ./backend/products-service/pom.xml
 mvn package -f ./backend/authenticate-service/pom.xml
mvn package -f ./backend/orders-service/pom.xml
 docker-compose build products-service orders-service authenticate-service
 mvn install -PbuildDocker -f ./backend/ecommerce-api-gateway/pom.xml
 mvn install -PbuildDocker -f ./backend/ecommerce-eureka-server/pom.xml
}

if [ "$1" = "front" ]
then
 buildFront
elif [ "$1" = "api" ]
then
 buildApi
elif [ -z $1  ]
then
 buildFront
 buildApi
fi
