#!/bin/bash


buildFront ()
{
 # Build front end
 npm --prefix ./frontend install &&  npm --prefix ./frontend  run build
 docker compose build ecommerce-front
}

buildApi ()
{
 # Build api: package all backend jars in one reactor build, then build every
 # service image. Tests are skipped here (run them per-service per
 # backend/CLAUDE.md — a parent-level test run saturates Docker via Testcontainers).
 mvn package -DskipTests -f ./backend/pom.xml
 docker compose build \
   products-service authenticate-service orders-service \
   featured-products-service price-service api-gateway
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
