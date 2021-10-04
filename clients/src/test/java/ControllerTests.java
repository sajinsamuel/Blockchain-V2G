import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import net.corda.energy_cordapp.accountUtilities.CreateNewAccount;
import net.corda.energy_cordapp.accountUtilities.GetAccountBalance;
import net.corda.energy_cordapp.accountUtilities.ShareAccountTo;
import net.corda.energy_cordapp.flows.A2ATokenTransferFlow;
import net.corda.energy_cordapp.flows.EnergyTransferFlow;
import net.corda.energy_cordapp.flows.IssueTokenFlow;
import net.corda.parsedata.client.webserver.Controller;
import net.corda.parsedata.client.webserver.NodeRPCConnection;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ControllerTests {

    private NodeRPCConnection rpc = mock(NodeRPCConnection.class);
    private CordaRPCOps proxy = mock(CordaRPCOps.class);
    private NodeInfo myInfo = mock(NodeInfo.class);
    private Party myIdentity = mock(Party.class);

    private FlowProgressHandle mockHandle;
    private CordaFuture mockFuture;

    private Controller controller ;

    @Before
    public void setup() {
        when(rpc.getProxy()).thenReturn(proxy);
        when(proxy.nodeInfo()).thenReturn(myInfo);
        when(myInfo.getLegalIdentities()).thenReturn(Arrays.asList(myIdentity));
        when(myIdentity.getName()).thenReturn(CordaX500Name.parse("O=Truman, L=Fake Town, C=US"));
        controller = new Controller(rpc);

        // for the tests, to make startTrackedFlowDynamic run without NPEs
        mockHandle = mock(FlowProgressHandle.class);
        mockFuture = mock(CordaFuture.class);
        when(mockHandle.getReturnValue()).thenReturn(mockFuture);
    }

    @Test
    public void testAccountBalance() throws IOException, ExecutionException, InterruptedException {
        // set up how this flow would behave (mocking it)
        when(mockFuture.get()).thenReturn(100L);
        when(proxy.startTrackedFlowDynamic(eq(GetAccountBalance.class), any())).thenReturn(mockHandle);

        // perform the action
        ResponseEntity<String> response
                = controller.getAccountBalance("{\"account\":\"Batmobile\"}");

        // verify that the expected action would have been performed on blockchain
        verify(proxy).startTrackedFlowDynamic(GetAccountBalance.class, "Batmobile");

        // assert that the response is as we expect
        assertEquals("{\"accountName\":\"Batmobile\", \"tokenBalance\":100}", response.getBody());

    }

    @Test
    public void testAccountCreation() throws ExecutionException, InterruptedException, IOException {
        // set up how this flow would behave (mocking it)
        when(mockFuture.get()).thenReturn("Account UUID");
        when(proxy.startTrackedFlowDynamic(eq(CreateNewAccount.class), any())).thenReturn(mockHandle);

        // perform the action
        ResponseEntity<String> response
                = controller.createNewAccount("{\"acctName\":\"Batmobile\"}");
        verify(proxy).startTrackedFlowDynamic(CreateNewAccount.class, "Batmobile");

        // assert that the response is as we expect
        assertEquals("{\"UUID\":\"Account UUID\"}", response.getBody());
    }

    @Test
    public void testIssueTokens() throws ExecutionException, InterruptedException, IOException {
        // a test TxID (transaction hash) that we will use below
        SecureHash txid = SecureHash.Companion.sha256("transaction hash");
        String txidString = txid.toHexString();

        // set up how this flow would behave (mocking it)
        SignedTransaction mockTransaction = mock(SignedTransaction.class);
        //when(mockTransaction.toString()).thenReturn("Mock transaction stringification");
        when(mockTransaction.getId()).thenReturn(txid);
        when(mockFuture.get()).thenReturn(mockTransaction);
        when(proxy.startTrackedFlowDynamic(eq(IssueTokenFlow.class), any(), any()))
                .thenReturn(mockHandle);

        // mock the retrieval of a Party from the Corda network
        Party mockParty = mock(Party.class);
        when(proxy.wellKnownPartyFromX500Name(
                CordaX500Name.parse("O=VW,L=Wolfsburg,C=GB")
        )).thenReturn(mockParty);

        // perform the action
        ResponseEntity<String> response
                = controller.issueTokens(
                "{\"nodeName\":\"O=VW,L=Wolfsburg,C=GB\", \"numberOfTokens\":100}"
        );

        verify(proxy).startTrackedFlowDynamic(IssueTokenFlow.class, 100L, mockParty);

        // assert that the response is as we expect
        //assertEquals("issued Mock transaction stringification", response.getBody());
        assertEquals("{\"transactionHash\":\"" + txidString + "\"}",
                response.getBody());
    }

    @Test
    public void testSendEnergyTokens() throws ExecutionException, InterruptedException, IOException {

        // a test TxID (transaction hash) that we will use below
        SecureHash txid = SecureHash.Companion.sha256("transaction hash");
        String txidString = txid.toHexString();

        // mock the transaction we are going to return from the Corda flow
        SignedTransaction mockTransaction = mock(SignedTransaction.class);
        //when(mockTransaction.toString()).thenReturn("Mock transaction stringification");
        when(mockTransaction.getId()).thenReturn(txid);
        when(mockFuture.get()).thenReturn(mockTransaction);
        when(proxy.startTrackedFlowDynamic(
                eq(EnergyTransferFlow.SendEnergyTokens.class),
                any(), any(), any(), any()))
                .thenReturn(mockHandle);

        // encode the hash we will send to the controller
        String hashToSend = new String(Hex.encode("sample hash".getBytes(StandardCharsets.UTF_8)));

        // mock the retrieval of a Party from the Corda network
        Party mockParty = mock(Party.class);
        when(proxy.wellKnownPartyFromX500Name(
                CordaX500Name.parse("O=Parsedata,L=Toronto,C=CA")
        )).thenReturn(mockParty);

        // perform the action
        ResponseEntity<String> response
                = controller.sendEnergyTokens(
                        "{\"sendToAccountName\":\"Batmobile\", " +
                                "\"dataHash\":\"" + hashToSend + "\"," +
                                "\"numberOfTokens\":100," +
                                "\"sanctionsBody\":\"O=Parsedata,L=Toronto,C=CA\"" +
                                "}"
        );

        // verify the appropriate message is sent to the Corda node
        verify(proxy).startTrackedFlowDynamic(
                EnergyTransferFlow.SendEnergyTokens.class,
                100L,
                "Batmobile",
                mockParty,
                "sample hash".getBytes(StandardCharsets.UTF_8)
        );

        // assert the correct format of response
        assertEquals("{\"transactionHash\":\"" + txidString + "\"}",
                response.getBody());
    }

    @Test
    public void testShareAccount() throws ExecutionException, InterruptedException, IOException {
        when(mockFuture.get()).thenReturn("No string at all");
        when(proxy.startTrackedFlowDynamic(eq(ShareAccountTo.class), any(), any())).thenReturn(mockHandle);

        // mock the retrieval of a Party from the Corda network
        Party mockParty = mock(Party.class);
        when(proxy.wellKnownPartyFromX500Name(
                CordaX500Name.parse("O=Grid,L=London,C=GB")
        )).thenReturn(mockParty);

        // perform the action
        ResponseEntity<String> response
                = controller.shareAccountTo("{\"accountName\":\"Batmobile\"," +
                "\"nodeName\":\"O=Grid,L=London,C=GB\"}");

        verify(proxy).startTrackedFlowDynamic(ShareAccountTo.class, "Batmobile", mockParty);

        assertEquals("{}", response.getBody());
    }

    @Test
    public void testSendFromAccount() throws ExecutionException, InterruptedException, IOException {
        // a test TxID (transaction hash) that we will use below
        SecureHash txid = SecureHash.Companion.sha256("transaction hash");
        String txidString = txid.toHexString();

        SignedTransaction mockTransaction = mock(SignedTransaction.class);
        when(mockTransaction.getId()).thenReturn(txid);
        when(mockFuture.get()).thenReturn(mockTransaction);
        when(proxy.startTrackedFlowDynamic(
                eq(A2ATokenTransferFlow.SendEnergyTokens.class),
                any(),
                any(),
                any()
        )).thenReturn(mockHandle);

        ResponseEntity<String> response
                = controller.a2aTokenTransferFlow("{\"sendToAccountName\":\"Bluesmobile\"," +
                "\"sendFromAccountName\":\"Batmobile\"," +
                "\"numberOfTokens\":100}");

        verify(proxy).startTrackedFlowDynamic(A2ATokenTransferFlow.SendEnergyTokens.class,
                "Bluesmobile", "Batmobile", 100L);

        assertEquals("{\"transactionHash\":\""+txidString+"\"}", response.getBody());
    }

    @Test
    public void testGetPeers() {
        // the necessary mocks that we will return
        Party mockParty = mock(Party.class);
        NodeInfo mockNodeInfo = mock(NodeInfo.class);

        // the legal identity returned by the mock party
        when(mockParty.getName()).thenReturn(CordaX500Name.parse("O=Peer,L=Los Angeles,C=US"));
        // return a list containing the above party from the nodeInfo
        when(mockNodeInfo.getLegalIdentities()).thenReturn(Arrays.asList(mockParty));

        // return a list containing the above nodeInfo when networkMapSnapshot is requested
        when(proxy.networkMapSnapshot()).thenReturn(Arrays.asList(mockNodeInfo));

        // perform the action itself
        HashMap<String, List<String>> response = controller.getPeers();

        // generate the expected response
        HashMap<String, List<String>> expectedResponse = new HashMap<>();
        expectedResponse.put("peerNodes", Arrays.asList("O=Peer, L=Los Angeles, C=US"));

        // verify that the real response matches what is expected
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testNodeTokenBalance() {
        Vault.Page mockPage = mock(Vault.Page.class);
        StateAndRef mockStateAndRef = mock(StateAndRef.class);
        TransactionState mockState = mock(TransactionState.class);
        FungibleToken mockToken = mock(FungibleToken.class);
        Amount mockAmount = mock(Amount.class);

        when(mockAmount.getQuantity()).thenReturn(100L);
        when(mockToken.getAmount()).thenReturn(mockAmount);
        when(mockState.getData()).thenReturn(mockToken);
        when(mockStateAndRef.getState()).thenReturn(mockState);
        when(mockPage.getStates()).thenReturn(Arrays.asList(mockStateAndRef));

        when(proxy.vaultQuery(FungibleToken.class)).thenReturn(mockPage);

        ResponseEntity<String> response
                = controller.getNodeBalance();

        verify(proxy).vaultQuery(FungibleToken.class);

        assertEquals("{\"tokenBalance\":100}", response.getBody());
    }
}
