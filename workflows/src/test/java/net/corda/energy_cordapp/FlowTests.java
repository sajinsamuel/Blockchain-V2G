package net.corda.energy_cordapp;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.NetworkParameters;
import net.corda.energy_cordapp.accountUtilities.CreateNewAccount;
import net.corda.energy_cordapp.accountUtilities.GetAccountBalance;
import net.corda.energy_cordapp.accountUtilities.ShareAccountTo;
import net.corda.energy_cordapp.flows.A2ATokenTransferFlow;
import net.corda.energy_cordapp.flows.EnergyTransferFlow;
import net.corda.energy_cordapp.flows.IssueTokenFlow;
import net.corda.testing.node.*;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;

// WHAT WE ARE TESTING:
//  * Accounts can be created on BMW's node
//  * Account can be shared to Grid's node
//  * Grid's node can be issued tokens
//  * Grid's node can send its tokens by EnergyTransferFlow.SendEnergyTokens to BMW's node
//      * VW's node sees an increased balance of tokens
//      * Grid's node sees a decreased balance of tokens
//  * Account-to-account transaction cannot be performed from a non-existent account (appropriate error is thrown)
//      (this ensures vault is properly queried)
//  * Account-to-account transaction cannot be performed with an insufficient balance (appropriate error)
//  * Account-to-account transaction CAN be performed in proper conditions between two accounts on the same node
//  * Account-to-account transaction CAN be performed in proper conditions between two accounts on different nodes

public class FlowTests {
    // we need 4 nodes, as below
    private MockNetwork mockNetwork;
    private StartedMockNode volkswagen;
    private StartedMockNode grid;
    private StartedMockNode parsedata;

    // network parameters used for most Corda tests
    private NetworkParameters testNetworkParameters =
            new NetworkParameters(4,
                    Arrays.asList(),
                    10485760,
                    (10485760 * 5),
                    Instant.now(),
                    1,
                    new LinkedHashMap<>()
            );


    @Before
    public void setup() {
        // need to include all these components of the cordapp explicitly to be loaded for testing
        mockNetwork = new MockNetwork(new MockNetworkParameters().withCordappsForAllNodes(ImmutableList.of(
                TestCordapp.findCordapp("net.corda.energy_cordapp.contracts"),
                TestCordapp.findCordapp("net.corda.energy_cordapp.flows"),
                TestCordapp.findCordapp("net.corda.energy_cordapp.accountUtilities"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        )).withNetworkParameters(testNetworkParameters));

        // creating fresh nodes each time, so that the test don't interfere with one another
        volkswagen = mockNetwork.createPartyNode(new CordaX500Name("Volkswagen", "Wolfsburg", "DE"));
        grid = mockNetwork.createPartyNode(new CordaX500Name("Hydro One", "Toronto", "CA"));
        parsedata = mockNetwork.createPartyNode(new CordaX500Name("Parsedata", "Toronto", "CA"));

        // register the responding flow for each node we need to send tokens to an account on
        volkswagen.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        parsedata.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        grid.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        mockNetwork.runNetwork();
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    /**
     * Test the ability to create an account using our CreateNewAccount flow,
     * by attempting to create an account and then querying for that account's AccountInfo state
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void AccountCreation() throws ExecutionException, InterruptedException {
        volkswagen.startFlow(new CreateNewAccount("Batmobile"));
        mockNetwork.runNetwork();

        // query for the AccountInfo state in the vault
        AccountService accountService = volkswagen.getServices().cordaService(KeyManagementBackedAccountService.class);

        // assert that the account exists in the vault
        assert (accountService.accountInfo("Batmobile").size() != 0);
    }

    /**
     * Test the ability to share an account from one node to another using our ShareAccountTo flow
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void AccountSharing() throws ExecutionException, InterruptedException {
        final String batmobile = "Batmobile";

        // create the account
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        mockNetwork.runNetwork();

        // share the account with the grid
        volkswagen.startFlow(new ShareAccountTo(batmobile,
                grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // query the grid's vault for all accounts, ensure that the list is not empty
        // (since we haven't created an account on the grid node, the presence of an account indicates sucessful sharing)
        AccountService accountService = grid.getServices().cordaService(KeyManagementBackedAccountService.class);
        assert (accountService.accountsForHost(volkswagen.getInfo().getLegalIdentities().get(0)).size() != 0);
    }

    /**
     * Test the ability to issue tokens to a node using IssueTokenFlow
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void IssueTokensTest() throws ExecutionException, InterruptedException {
        // perform the issuance from parsedata to the grid node
        parsedata.startFlow(
                new IssueTokenFlow((long) 100, grid.getInfo().getLegalIdentities().get(0))
        );
        mockNetwork.runNetwork();

        // query for any FungibleTokens states
        StateAndRef<FungibleToken> gridTokenStateAndRef = grid.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates().stream()
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("FungibleTokenState not found from vault"));

        // assert that balance matches what we issued
        assertEquals(100, gridTokenStateAndRef.getState().getData().getAmount().getQuantity());
    }

    /**
     * Test EnergyTransferFlow from a node to an account on a different node
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void TokenSendTest() throws ExecutionException, InterruptedException {
        // the name of the account to which we will transfer energy tokens
        final String batmobile = "Batmobile";

        // create the account in question
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        mockNetwork.runNetwork();

        // share the account to the grid so we can transfer tokens to it
        volkswagen.startFlow(new ShareAccountTo(batmobile,
                grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // parsedata issues tokens to the grid
        parsedata.startFlow(new IssueTokenFlow((long) 100, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // the grid sends some of the issued tokens to the account using EnergyTransferFlow
        // passing parsedata as the sanctions body
        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                20,
                batmobile,
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));
        mockNetwork.runNetwork();

        // ensure that the account balance matches what is expected
        assertEquals(20, volkswagen.startFlow(new GetAccountBalance(batmobile)).get().intValue());
    }

    /**
     * Test that it is impossible to make an account-to-account transaction from a non-existent account.
     * That is, ensure that the node actually selects for tokens belonging to the sender,
     * rather than just sending tokens from its own balance (that would yield an InsufficientBalanceException)
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void sendFromNonexistentAccount() throws ExecutionException, InterruptedException {
        final String batmobile = "Batmobile";
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        mockNetwork.runNetwork();

        CordaFuture future = volkswagen.startFlow(new A2ATokenTransferFlow.SendEnergyTokens(
                batmobile,
                "nonexistent account",
                100
        ));
        mockNetwork.runNetwork();

        exception.expectCause(instanceOf(FlowException.class));
        future.get();
    }

    /**
     * Test that sending from an insufficiently funded (existing) account throws an InsufficientBalanceException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void sendFromInsufficientBalance() throws ExecutionException, InterruptedException {
        // parsedata will issue to the grid, grid will send tokens to the batmobile account on the volkswagen node,
        // and then volkswagen will attempt a transaction to another account on its own node from the batmobile
        // with a value greater than the batmobile balance.
        // should throw an InsufficientBalanceException

        // two account names
        final String batmobile = "Batmobile";
        final String gordon = "GordonsCar";

        // create both accounts on the same node
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        volkswagen.startFlow(new CreateNewAccount(gordon));
        mockNetwork.runNetwork();

        // issue tokens from parsedata to the grid
        parsedata.startFlow(new IssueTokenFlow((long) 50, grid.getInfo().getLegalIdentities().get(0)));

        // share the batmobile account to the grid so we can send the batmobile tokens
        volkswagen.startFlow(new ShareAccountTo(batmobile, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // send tokens to the batmobile
        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                (long) 100,
                "Batmobile",
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));
        mockNetwork.runNetwork();

        // now that we have tokens on the batmobile, we can attempt to overspend them to the other account
        CordaFuture future = volkswagen.startFlow(new A2ATokenTransferFlow.SendEnergyTokens(gordon, batmobile, 50));
        // TODO: add assert statement, for which I need a flow to check balance
        mockNetwork.runNetwork();

        // we expect an InsufficientBalanceException (since the sender exists, but is underfunded)
        exception.expectCause(instanceOf(InsufficientBalanceException.class));
        future.get();
    }

    @Test
    public void moveTokensSameNodeTest() throws ExecutionException, InterruptedException {
        // As before, parsedata will issue to the grid and grid will send to the account on volkswagen.
        // This time, the sender account exists and is sufficiently funded.

        // the names of the two accounts
        final String batmobile = "Batmobile";
        final String gordon = "GordonsCar";

        // create the accounts on the same node
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        volkswagen.startFlow(new CreateNewAccount(gordon));
        mockNetwork.runNetwork();

        // issue tokens to the grid from parsedata
        parsedata.startFlow(new IssueTokenFlow((long) 50, grid.getInfo().getLegalIdentities().get(0)));
        // share an account with the grid, so that the grid can send tokens to it
        volkswagen.startFlow(new ShareAccountTo(batmobile, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // send tokens to the batmobile account
        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                (long) 50,
                "Batmobile",
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));
        mockNetwork.runNetwork();

        // send tokens from one account to the other
        // This constitutes a transaction between two accounts on the same node
        volkswagen.startFlow(new A2ATokenTransferFlow.SendEnergyTokens(gordon, batmobile, 50));
        mockNetwork.runNetwork();

        // ensure the balance of the receiving account matches expectations
        assertEquals(50, volkswagen.startFlow(new GetAccountBalance(gordon)).get().intValue());
    }

    /**
     * Test the successful case of account-to-account transaction between two different nodes
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void moveTokensDiffNodeTest() throws ExecutionException, InterruptedException {
        // just like the last test, but the accounts are on two different nodes

        final String batmobile = "Batmobile";
        final String gordon = "GordonsCar";

        // notice, the accounts are created on different nodes
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        grid.startFlow(new CreateNewAccount(gordon));
        mockNetwork.runNetwork();

        // the receiving account must be shared with the volkswagen node (which houses the sending account)
        grid.startFlow(new ShareAccountTo(gordon, volkswagen.getInfo().getLegalIdentities().get(0)));
        // the sending account must be shared with the grid, so that the grid can give the sending account tokens
        volkswagen.startFlow(new ShareAccountTo(batmobile, grid.getInfo().getLegalIdentities().get(0)));
        // parsedata issues to grid
        parsedata.startFlow(new IssueTokenFlow((long) 50, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // grid sends its balance to Batmobile
        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                (long) 50,
                batmobile,
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));
        mockNetwork.runNetwork();

        // batmobile sends its tokens to the account on the grid node
        volkswagen.startFlow(new A2ATokenTransferFlow.SendEnergyTokens(gordon, batmobile, 50));
        mockNetwork.runNetwork();

        // assert that the receiving account receives the A2A transaction
        assertEquals(50, grid.startFlow(new GetAccountBalance(gordon)).get().intValue());
    }

    @Test
    public void sendFromNodeTwice() throws ExecutionException, InterruptedException {
        // the name of the account to which we will transfer energy tokens
        final String batmobile = "Batmobile";

        // create the account in question
        volkswagen.startFlow(new CreateNewAccount(batmobile));
        mockNetwork.runNetwork();

        // share the account to the grid so we can transfer tokens to it
        volkswagen.startFlow(new ShareAccountTo(batmobile,
                grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // parsedata issues tokens to the grid
        parsedata.startFlow(new IssueTokenFlow((long) 100, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // the grid sends some of the issued tokens to the account using EnergyTransferFlow
        // passing parsedata as the sanctions body
        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                20,
                batmobile,
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));
        mockNetwork.runNetwork();

        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(
                20,
                batmobile,
                parsedata.getInfo().getLegalIdentities().get(0),
                "sample hash".getBytes(StandardCharsets.UTF_8),
                ""
        ));

        mockNetwork.runNetwork();
        // ensure that the account balance matches what is expected
        assertEquals(40, volkswagen.startFlow(new GetAccountBalance(batmobile)).get().intValue());
    }
}