#!/bin/bash


shutdownDocker ()
{
 docker-compose stop
}

shutdownKubernetes ()
{
 kubectl scale deploy -n default --replicas=0 --all
}

if [ "$1" = "docker" ]
then
 shutdownDocker
elif [ "$1" = "kubernetes" ]
then
 shutdownKubernetes
elif [ -z $1  ]
then
 shutdownDocker
fi
