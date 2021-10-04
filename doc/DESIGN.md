# Overview
To track value of V2G interactions and enable a digital energy marketplace,
this CorDapp uses Corda Fungible Tokens. 
These tokens are issued by Parsedata to Grid nodes 
(perhaps in exchange for some transferred money)
who can then transfer these tokens to vehicles. 
These vehicles can then transfer the tokens to other vehicles (or perhaps other parties)
creating the aforementioned marketplace.
To note: since the value of energy can vary over time, 
the tokens represent some amount of *value* rather than *energy*.

We envision 4 kinds of nodes acting in this marketplace for now (as represented in the `build.gradle` file):
* Electric vehicle OEM nodes
* Grid operator nodes
* Parsedata nodes
* Notary nodes

In exchange for V2G, it is *vehicles* that are recorded as receiving tokens, not people.
These vehicles are represented in Corda as Accounts on their respective OEM nodes.
Any other marketplace actors could be represented as Accounts, 
including cases where there is only one account per node.

There are two distinct kinds of interactions that can be seen:
* V2G interactions, or transfers of energy
* pure peer-to-peer transactions, or transfers of value

Parsedata's mission, after all, is to unify these two.

V2G interactions are complex, and may yield a bunch of different data.
This data could be of interest and useful to store, but could be subject to legal restrictions
on storage (e.g. GDPR). This, and its size, make it ineligible for storage on the blockchain.
However, the blockchain could still be used to guarantee its immutability by storing the hash of all this data
on the blockchain. Specifically, every V2G transaction 
(initiated by an EnergyTransferFlow.SendEnergyTokens) produces an additional output state
of the type InteractionDataState which stores a provided data hash, sender and recipient parties, and an additional
`note` String that could be used for additional data.
This InteractionDataState is also a QueryableState adhering to the InteractionDataSchemaV1.InteractionDataModel,
which means that in addition to being stored on-chain it is stored off-chain in an H2 database for fast querying
using JDBC (InteractionDataSchemaV1.InteractionDataModel defines the column values stored in H2).
Additionally, V2G interactions currently require the signature of some sanctions body (e.g. Parsedata).

On the other hand, pure value transactions are not associated with interaction data whose hash must be stored on-chain.
Thus, they are performed with a different type of transaction associated with a different flow: A2ATokenTransferFlow.

Since a Grid operator doesn't have a reason to logically separate its funds between different accounts
(the alternative would require the Grid to juggle around tokens between its own accounts to pay for V2G),
EnergyTransferFlow currently just selects FungibleToken states from the Vault without concern for
whether the state belongs to an Account (or to which Account it belongs).
This allows for multiple different uses of this same CorDapp without modifying it:
a Grid could create one account (e.g. an admin account) to receive all A2ATokenTransferFlow transfers;
or the Grid could create an number of accounts (e.g. for each charge point) to receive tokens at 
(via A2ATokenTransferFlow) allowing it to (e.g.) figure out which Accounts are giving the most energy (via G2V)
while at the same time not having to worry about juggling funds between its own accounts.
The Grid could even operate without Accounts altogether 
(however, it would be unable to receive tokens via A2ATokenTransferFlow in this case)! 
**To require the specification of a sender account in this flow** (if that is desirable in the future)
simply add an additional QueryCriteria as a last argument to `addMoveFungibletokens` in `EnergyTransferFlow.SendEnergyTokens`
to select only states with externalId matching the UUID of the Account from which you are sending tokens. For example,
change the `addMoveFungibleTokens` to:
```java
// senderAccount is of type AccountInfo
// this is the additional query criterion
QueryCriteria heldByAccount = new QueryCriteria.VaultQueryCriteria()
        .withExternalIds(Collections.singletonList(senderAccount.getIdentifier().getId()));

// apply this criterion to the existing addMoveFungibleTokens by simply adding heldByAccount as the last argument
MoveTokensUtilities.addMoveFungibleTokens(
        transactionBuilder,
        getServiceHub(),
        ImmutableList.of(new PartyAndAmount<>(receiver, new Amount<>(amount, new EnergyTokenType()))),
        getOurIdentity(),
        heldByAccount
        );
```
