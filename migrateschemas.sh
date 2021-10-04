#!/bin/bash

echo "Entering Grid folder"
cd build/nodes/OntarioHydroGrid
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../Notary
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../Hyundai
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../Parsedata
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema


cd ../Tesla
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../QuebecHydroGrid
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../OtherCars
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema

cd ../OtherGrids
java -jar corda.jar run-migration-scripts --core-schemas --app-schemas --allow-hibernate-to-manage-app-schema




