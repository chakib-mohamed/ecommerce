#!/bin/bash


runDocker ()
{
 # Run using docker compose
 docker-compose stop
 docker-compose up -d
 docker-compose logs -f
}

runKubernetes ()
{
 # Run using kubernetes cluster
 kubectl scale deploy -n default --replicas=0 --all
 sleep 5
 kubectl scale deploy -n default --replicas=1 --all
}

if [ "$1" = "docker" ]
then
 runDocker
elif [ "$1" = "kubernetes" ]
then
 runKubernetes
elif [ -z $1  ]
then
 runDocker
fi