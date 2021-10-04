package net.corda.energy_cordapp.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.energy_cordapp.states.EnergyTokenType;

// EVENTUALLY: this flow belongs only to Parsedata's node

/**
 * The flow for issuing tokens.
 * Tokens must be issued to a node,
 * cannot be issued to an account yet.
 * The conceived process is that Parsedata will issue tokens to the Grid this way.
 * Eventually, this flow will be housed only on Parsedata's node.
 * Therefore, it will eventually be a different Cordapp.
 */
@StartableByRPC
public class IssueTokenFlow extends FlowLogic<SignedTransaction> {
    //the amount of tokens to issue
    private final Long amount;

    // the node to issue the tokens to
    private final Party recipient;

    /**
     * The constructor.
     * @param amount the number of tokens to issue
     * @param recipient the recipient party to issue the tokens to (must be a node)
     */
    public IssueTokenFlow(Long amount, Party recipient) {
        this.amount = amount;
        this.recipient = recipient;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        // building the energy token which to issue
        // specifies: amount, of EnergyTokenType, from this node, to the recipient node
        FungibleToken energyToken =
                new FungibleTokenBuilder()
                    .ofTokenType(new EnergyTokenType())
                    .withAmount(amount)
                    .issuedBy(getOurIdentity())
                    .heldBy(recipient)
                    .buildFungibleToken();

        // actually issue the tokens created above
        return subFlow(new IssueTokens(ImmutableList.of(energyToken)));
    }
}
