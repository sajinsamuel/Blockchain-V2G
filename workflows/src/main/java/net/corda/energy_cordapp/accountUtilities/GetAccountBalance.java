package net.corda.energy_cordapp.accountUtilities;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.node.services.vault.QueryCriteria;

import java.util.Collections;
import java.util.List;

@StartableByRPC
@StartableByService
@InitiatingFlow
public class GetAccountBalance extends FlowLogic<Long> {
    private final String account;

    public GetAccountBalance(String account) {
        this.account = account;
    }

    @Override
    public Long call() throws FlowException {
        AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);
        AccountInfo accountInfo = null;
        try {
            accountInfo = accountService.accountInfo(account).get(0).getState().getData();
        } catch (IndexOutOfBoundsException e) {
            throw new FlowException("No account found with name: " + account);
        }

        QueryCriteria heldByAccount = new QueryCriteria.VaultQueryCriteria()
                .withExternalIds(Collections.singletonList(accountInfo.getIdentifier().getId()));

        List<StateAndRef<FungibleToken>> tokenStates
                = getServiceHub().getVaultService().queryBy(FungibleToken.class, heldByAccount).getStates();
        if (tokenStates.size() == 0) {
            return (long) 0;
        }
        long size = 0;
        for (StateAndRef<FungibleToken>  state : tokenStates) {
            size += state.getState().getData().getAmount().getQuantity();
        }

        return size;
    }
}

