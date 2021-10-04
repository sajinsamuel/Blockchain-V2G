package net.corda.energy_cordapp.accountUtilities;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByName;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.flows.StartableByService;
import net.corda.core.identity.Party;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import java.util.*;

/**
 * Shares an account with a given name (as a String) to a given node.
 * Useful to call as you don't need to fiddle with retrieval of the account from the vault etc.
 * Must be called on the EV manufacturer's node to share with grid before V2G interaction.
 */
@StartableByRPC
@StartableByService
public class ShareAccountTo extends FlowLogic<String>{

    // the node to share the account to
    private final Party shareTo;

    // the (local) name of the account to be shared
    private final String acctNameShared;

    /**
     * The constructor of this flow
     * @param acctNameShared the (local) name of the account to share
     * @param shareTo the party of the node to share the account with
     */
    public ShareAccountTo(String acctNameShared, Party shareTo) {
        this.acctNameShared = acctNameShared;
        this.shareTo = shareTo;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        // gets the list of all of this node's accounts
        List<StateAndRef<AccountInfo>> allmyAccounts = getServiceHub().cordaService(KeyManagementBackedAccountService.class).ourAccounts();

        // streams and filters all our accounts to find the one with the matching name
        // could implement an .orElse() to handle a case where the appropriate account is not found (doesn't exist)
        StateAndRef<AccountInfo> SharedAccount = allmyAccounts.stream()
                .filter(it -> it.getState().getData().getName().equals(acctNameShared))
                .findAny().get();

        // actually share the account from the StateAndRef we retrieved from all the accounts from the AccountService
        subFlow(new ShareAccountInfo(SharedAccount, Arrays.asList(shareTo)));
        // return a string, including the name shared and the name of the organization with which it is shared
        return "Shared " + acctNameShared + " with " + shareTo.getName().getOrganisation();
    }
}