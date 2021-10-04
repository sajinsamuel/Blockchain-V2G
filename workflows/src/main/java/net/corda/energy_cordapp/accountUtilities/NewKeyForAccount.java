package net.corda.energy_cordapp.accountUtilities;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.flows.StartableByService;
import net.corda.core.identity.PartyAndCertificate;
import java.util.*;

/**
 * Creates a new key for a given account.
 * Useful if you want to interact with a new node.
 */
@StartableByRPC
@StartableByService
public class NewKeyForAccount extends FlowLogic<PartyAndCertificate>{

    private final UUID accountID;

    /**
     * Creates a new key for an account with a given UUID
     * @param accountID the UUID of the account we want to create a new key for
     */
    public NewKeyForAccount(UUID accountID) {
        this.accountID = accountID;
    }

    @Override
    @Suspendable
    public PartyAndCertificate call() throws FlowException {
        // gets the key management service and uses it to generate a new key and certificate
        return getServiceHub().getKeyManagementService().freshKeyAndCert(getOurIdentityAndCert(), false, accountID);
    }
}