package net.corda.energy_cordapp.accountUtilities;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.*;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import net.corda.core.flows.FlowLogic;;
import net.corda.core.flows.StartableByRPC;

import java.util.UUID;

/**
 * Creates a new account from a String of the account name.
 * If that account already exists, prints the stacktrace
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
public class CreateNewAccount extends FlowLogic<String>{

    private String acctName;

    /**
     * Constructor. Creates an account with this name
     * @param acctName account name (local, NOT UUID)
     */
    public CreateNewAccount(String acctName) {
        this.acctName = acctName;
    }


    @Override
    @Suspendable
    public String call() throws FlowException {
        StateAndRef<AccountInfo> newAccount = null;
        try {
            // get an AccountService from the ServiceHub and use it to create the desired account.
            // Try to then get that account, save the returned StateAndRef to newAccount
            newAccount = getServiceHub().cordaService(KeyManagementBackedAccountService.class).createAccount(acctName).get();
        } catch (Exception e) {
            // if the above fails (account could not be created) print the stacktrace
            e.printStackTrace();
        }

        // get the AccountInfo from the StateAndRef returned before
        AccountInfo acct = newAccount.getState().getData();

        // return the account name and the UUID
        //return "" + acct.getName() + " team's account was created. UUID is : " + acct.getIdentifier();
        return acct.getIdentifier().toString();
    }
}
