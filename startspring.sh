#!/bin/bash

trap "kill 0" EXIT

./gradlew runParsedataAuthServer &
./gradlew runOntarioHydroGridServer &
./gradlew runHyundaiServer &
./gradlew runQuebecHydroGridServer &
./gradlew runTeslaServer &
./gradlew runOtherGridsServer &
./gradlew runOtherCarsServer &

echo "Done starting servers!"

wait