# Parsedata V2G CorDapp
This is a CorDapp forming the blockchain part of Parsedata's V2G Paid Pilot, tokenizing energy for V2G interactions.
This solves the problem of tracking the value transfer between EVs and electric grids due to V2G
and unlocks this value by representing it as tokens which can be transferred freely between parties.
To achieve this, this CorDapp allows for token transfer:
* in V2G transactions, from a grid to a vehicle (storing the hash of additional V2G data on chain);
* in peer-to-peer transactions, which do not store this additional information and allow for the creation of a
digital marketplace.
  
## Quickstart
First, ensure you are using Java version 8. Verify this by runnig:
`java -version`.

Then, clone the repository and deploy the nodes:
```shell
cd R3PaidPilotBlockchain/
./gradlew deployNodes
```

*Running the nodes now would give an error.*
Since we are using a custom database schema for fast JDBC queries, 
we need to migrate schemas to create necessary tables etc. 
The bootstrapper we ran with `deployNodes` does not seem to do this, so we have to do it ourselves.
This can be done by running:
```shell
./migrateschemas.sh
```

Now, you can run the nodes with:
```shell
./build/nodes/runnodes
```
This should create 4 XTerm windows with the respective CRaSH shells of the 4 nodes. 
If that doesn't work, ensure you have XTerm installed and are using the correct JRE.

Now, you could interact with the nodes over CRaSH, RPC with Node Explorer, or by our Spring servers.
For this quickstart, we will use the Spring servers.

There must be a separate Spring server running for each Corda node, but since we don't need to interact
with the Notary we will only start 3 (for the vechicle OEM, the grid, and Parsedata). 
Start them as follows (each command in a separate terminal window, or run the equivalent from your IDE):

```shell
./gradlew runVWServer
./gradlew runGridServer
./gradlew runParsedataAuthServer
```

Now we can interact with the nodes over HTTP. 
A sample bunch of requests to be made are contained within `R3PaidPilotBlockchain/clients/springtest.sh`.
Inspect this script to get a feel for what kinds of requests can/will be made and in what order.
Keep in mind that the ports used by each server are as follows:
* 10070 is used by VW
* 10080 is used by the Grid
* 10090 is used by Parsedata

Ensure you have `curl` and `jq` installed, then run 
```shell
./clients/springtest.sh
```

The list of stuff that happens here is:
1. Parsedata issues tokens to the Grid
2. VW creates an account called "Batmobile"
3. VW shares this account to the Grid
4. The Grid sends some of its tokens (from step 1.) to the "Batmobile" account, demonstrating a V2G transaction.
   The transaction hash is extracted from the response with jq and saved
5. The Grid creates an account called "Bluesmobile"
6. The Grid shares the "Bluesmobile" account to VW
7. VW sends tokens from "Batmobile" to "Bluesmobile", demonstrating an account-to-account transaction

I have glossed over a bunch of queries that were performed in the script, 
including querying by transaction hash, querying by data hash (the hash of V2G data stored on chain),
and more. Look at the script itself for more details.

## More stuff:
High level design/architecture/overview with reasoning: [doc/DESIGN.md](doc/DESIGN.md)
