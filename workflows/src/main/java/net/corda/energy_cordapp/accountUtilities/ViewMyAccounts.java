package net.corda.energy_cordapp.accountUtilities;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByName;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.flows.StartableByService;
import net.corda.core.identity.Party;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple flow that returns a list of the names of all our accounts (as Strings).
 */
@StartableByRPC
@StartableByService
public class ViewMyAccounts extends FlowLogic<List<String>>{

    /**
     * Empty constructor, since we don't have any fields to initialize
     */
    public ViewMyAccounts() {
    }

    @Override
    @Suspendable
    public List<String> call() throws FlowException {
        // retrieve the account service
        AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);

        // create a list of the String names of the accounts from the list of account StateAndRefs
        List<String> aAccountsQuery = accountService.ourAccounts().stream().map(it -> it.getState().getData().getName()).collect(Collectors.toList());

        // return our newly generated list of Strings
        return aAccountsQuery;
    }
}