package net.corda.energy_cordapp.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.energy_cordapp.states.EnergyTokenType;

import java.util.Collections;

public class A2ATokenTransferFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class SendEnergyTokens extends FlowLogic<SignedTransaction> {

        private final String whereTo;
        private final String whereFrom;
        private final long amount;

        public SendEnergyTokens(String whereTo, String whereFrom, long amount) {
            this.whereTo = whereTo;
            this.whereFrom = whereFrom;
            this.amount = amount;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            // TODO: query only for the tokens owned by the sender
            Party notary = NotaryUtilities.getPreferredNotary(getServiceHub());

            AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);

            AccountInfo receiverAccount = null;
            AccountInfo senderAccount = null;
            try {
                receiverAccount = accountService.accountInfo(whereTo).get(0).getState().getData();
            } catch (IndexOutOfBoundsException e) {
                throw new FlowException("Receiver account with name " + whereTo + " not found.");
            }

            try {
                senderAccount = accountService.accountInfo(whereFrom).get(0).getState().getData();
            } catch (IndexOutOfBoundsException e) {
                throw new FlowException("Sender account with name " + whereFrom + " not found.");
            }

            AnonymousParty receiverParty = subFlow(new RequestKeyForAccount(receiverAccount));
            //AnonymousParty senderParty = subFlow(new RequestKeyForAccount(senderAccount));

            QueryCriteria heldByAccount = new QueryCriteria.VaultQueryCriteria()
                    .withExternalIds(Collections.singletonList(senderAccount.getIdentifier().getId()));

            PartyAndAmount partyAndAmount = new PartyAndAmount<>(receiverParty, new Amount<>(amount, new EnergyTokenType()));

            // perhaps shouldn't use subFlow?
            return subFlow(new MoveFungibleTokens(partyAndAmount, Collections.emptyList(), heldByAccount));
        }
    }
}
