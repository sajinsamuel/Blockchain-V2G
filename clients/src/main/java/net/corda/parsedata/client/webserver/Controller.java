package net.corda.parsedata.client.webserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.*;
import net.corda.core.transactions.SignedTransaction;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import net.corda.energy_cordapp.accountUtilities.CreateNewAccount;
import net.corda.energy_cordapp.accountUtilities.GetAccountBalance;
import net.corda.energy_cordapp.accountUtilities.ShareAccountTo;
import net.corda.energy_cordapp.flows.A2ATokenTransferFlow;
import net.corda.energy_cordapp.flows.EnergyTransferFlow;
import net.corda.energy_cordapp.flows.IssueTokenFlow;
import net.corda.energy_cordapp.states.InteractionDataSchemaV1;
import net.corda.energy_cordapp.states.InteractionDataState;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    // the database connection, if one is provided
    private Connection dbConnection;

    // declaring a Jackson ObjecMapper
    ObjectMapper objectMapper;

    public Controller(NodeRPCConnection rpc) {
        this.proxy = rpc.getProxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();

        // instantiating the Jackson ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /** Helpers for filtering the network map cache. */
    public String toDisplayString(X500Name name){
        return BCStyle.INSTANCE.toString(name);
    }

    private boolean isNotary(NodeInfo nodeInfo) {
        return !proxy.notaryIdentities()
                .stream().filter(el -> nodeInfo.isLegalIdentity(el))
                .collect(Collectors.toList()).isEmpty();
    }

    private boolean isMe(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().equals(me);
    }

    private boolean isNetworkMap(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().getOrganisation().equals("Network Map Service");
    }

    @Autowired(required = false)
    public void setDbConnection(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @Configuration
    class Plugin {
        @Bean
        public ObjectMapper registerModule() {
            return JacksonSupport.createNonRpcMapper();
        }
    }

    @GetMapping(value = "/status", produces = TEXT_PLAIN_VALUE)
    private String status() {
        return "200";
    }

    @GetMapping(value = "/servertime", produces = TEXT_PLAIN_VALUE)
    private String serverTime() {
        return (LocalDateTime.ofInstant(proxy.currentNodeTime(), ZoneId.of("UTC"))).toString();
    }

    @GetMapping(value = "/addresses", produces = TEXT_PLAIN_VALUE)
    private String addresses() {
        return proxy.nodeInfo().getAddresses().toString();
    }

    @GetMapping(value = "/identities", produces = TEXT_PLAIN_VALUE)
    private String identities() {
        return proxy.nodeInfo().getLegalIdentities().toString();
    }

    @GetMapping(value = "/platformversion", produces = TEXT_PLAIN_VALUE)
    private String platformVersion() {
        return Integer.toString(proxy.nodeInfo().getPlatformVersion());
    }

    @GetMapping(value = "/peers", produces = APPLICATION_JSON_VALUE)
    public HashMap<String, List<String>> getPeers() {
        HashMap<String, List<String>> myMap = new HashMap<>();

        // Find all nodes that are not notaries, ourself, or the network map.
        Stream<NodeInfo> filteredNodes = proxy.networkMapSnapshot().stream()
                .filter(el -> !isNotary(el) && !isMe(el) && !isNetworkMap(el));
        // Get their names as strings
        List<String> nodeNames = filteredNodes.map(el -> el.getLegalIdentities().get(0).getName().toString())
                .collect(Collectors.toList());

        myMap.put("peerNodes", nodeNames);
        return myMap;
    }

    @GetMapping(value = "/notaries", produces = TEXT_PLAIN_VALUE)
    private String notaries() {
        return proxy.notaryIdentities().toString();
    }

    @GetMapping(value = "/flows", produces = TEXT_PLAIN_VALUE)
    private String flows() {
        return proxy.registeredFlows().toString();
    }

    @GetMapping(value = "/states", produces = TEXT_PLAIN_VALUE)
    private String states() {
        return proxy.vaultQuery(ContractState.class).getStates().toString();
    }

    @GetMapping(value = "/me",produces = APPLICATION_JSON_VALUE)
    private HashMap<String, String> whoami(){
        HashMap<String, String> myMap = new HashMap<>();
        myMap.put("me", me.toString());
        return myMap;
    }



    @PostMapping (value = "/issueTokens" , produces =  APPLICATION_JSON_VALUE , headers =  "Content-Type=application/json" )
    public ResponseEntity<String> issueTokens(@RequestBody String payload) throws IllegalArgumentException, IOException, ExecutionException, InterruptedException {
        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        long amount = jsonObject.get("numberOfTokens").asLong();
        String recipient = jsonObject.get("nodeName").textValue();

        CordaX500Name partyX500Name = CordaX500Name.Companion.parse(recipient);
        Party recipientParty = proxy.wellKnownPartyFromX500Name(partyX500Name);

        try {
            SignedTransaction result = proxy.startTrackedFlowDynamic(IssueTokenFlow.class,amount,recipientParty).getReturnValue().get();
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(objectMapper.writeValueAsString(new Object(){
                        public String transactionHash = result.getId().toHexString();
                    }));
                    /*
                    .body("{\"transactionHash\":\""+
                            result.getId().toHexString() +
                            "\"}");
                     */
                    //.body("issued " + result.toString());
        } catch (Exception ee){
            // thrown exceptions create a JSON-formatted 500 response
            throw ee;
        }

    }

    @PostMapping (value = "/createAccount" , produces =  APPLICATION_JSON_VALUE, headers =  "Content-Type=application/json" )
    public ResponseEntity<String> createNewAccount(@RequestBody String payload) throws IllegalArgumentException, IOException, ExecutionException, InterruptedException {

        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        String name = jsonObject.get("acctName").textValue();

        try {
            String result = proxy.startTrackedFlowDynamic(CreateNewAccount.class, name).getReturnValue().get();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(objectMapper.writeValueAsString(new Object(){
                        public String UUID = result;
                    }));
        } catch (Exception ee) {
            // thrown exceptions create a JSON-formatted 500 response
            throw ee;
        }
    }

    @PostMapping (value = "shareAccountInfo" , produces =  APPLICATION_JSON_VALUE, headers =  "Content-Type=application/json" )
    public ResponseEntity<String> shareAccountTo(@RequestBody String payload) throws IllegalArgumentException, IOException, ExecutionException, InterruptedException {

        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);

        String acctNameShared = jsonObject.get("accountName").textValue();
        String shareTo = jsonObject.get("nodeName").textValue();

        CordaX500Name partyX500Name = CordaX500Name.parse(shareTo);
        Party shareToParty = proxy.wellKnownPartyFromX500Name(partyX500Name);

        try {
            String result = proxy.startTrackedFlowDynamic(ShareAccountTo.class, acctNameShared, shareToParty).getReturnValue().get();
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("{}");
        } catch (Exception ee) {
            // thrown exceptions create a JSON-formatted 500 response
            throw ee;
        }
    }

    @PostMapping (value = "sendEnergyTokens" , produces =  APPLICATION_JSON_VALUE, headers =  "Content-Type=application/json")
    public ResponseEntity<String> sendEnergyTokens(@RequestBody String payload) throws IllegalArgumentException, IOException, ExecutionException, InterruptedException {
        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);

        long amount = jsonObject.get("numberOfTokens").asLong();

        String whereTo = jsonObject.get("sendToAccountName").textValue();

        String sanctionsBody = jsonObject.get("sanctionsBody").textValue();
        String hash = jsonObject.get("dataHash").textValue();
        String note = jsonObject.get("note").textValue();

        CordaX500Name sanctionsBodyX500Name = CordaX500Name.parse(sanctionsBody);
        Party sanctionsBodyParty = proxy.wellKnownPartyFromX500Name(sanctionsBodyX500Name);
        System.out.println(sanctionsBodyParty.toString());

        try {
            SignedTransaction result = proxy.startTrackedFlowDynamic(
                    EnergyTransferFlow.SendEnergyTokens.class,
                    amount,
                    whereTo,
                    sanctionsBodyParty,
                    Hex.decode(hash),
                    note
            ).getReturnValue().get();
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(objectMapper.writeValueAsString(new Object(){
                        public String transactionHash = result.getId().toHexString();
                    }));

        } catch (Exception ee) {
            // thrown exceptions create a JSON-formatted 500 response
            throw ee;
        }
    }

    @GetMapping(path="/accountTokenBalance", produces = APPLICATION_JSON_VALUE, headers = "Content-Type=application/json")
    public ResponseEntity<String> getAccountBalance(@RequestBody String payload) throws IOException, ExecutionException, InterruptedException {
        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        String account = jsonObject.get("account").textValue();
        try {
            Long balance = proxy.startTrackedFlowDynamic(GetAccountBalance.class, account)
                    .getReturnValue()
                    .get();
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(objectMapper.writeValueAsString(new Object(){
                        public String accountName = account;
                        public long tokenBalance = balance;
                    }));

        } catch (ExecutionException e) {
            // thrown exceptions create a JSON-formatted 500 response
            throw e;
        } catch (InterruptedException e) {
            // thrown exceptions create a JSON-formatted 500 response
            throw e;
        }
    }

    @PostMapping(path = "/sendfromaccount", produces = TEXT_PLAIN_VALUE, headers = "Content-Type=application/json")
    public ResponseEntity<String> a2aTokenTransferFlow(@RequestBody String payload) throws IOException, InterruptedException, ExecutionException {
        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        String whereTo = jsonObject.get("sendToAccountName").textValue();
        String whereFrom = jsonObject.get("sendFromAccountName").textValue();
        long amount = jsonObject.get("numberOfTokens").asLong();

        try {
            SignedTransaction transaction = proxy.startTrackedFlowDynamic(
                    A2ATokenTransferFlow.SendEnergyTokens.class,
                    whereTo,
                    whereFrom,
                    amount
            ).getReturnValue().get();
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(objectMapper.writeValueAsString(new Object(){
                        public String transactionHash = transaction.getId().toHexString();
                    }));
        } catch (InterruptedException e) {
            // thrown exceptions create a JSON-formatted 500 response
            throw e;
        } catch (ExecutionException e) {
            // thrown exceptions create a JSON-formatted 500 response
            throw e;
        }


    }

    @GetMapping(value = "/networkmap", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getNodeList() {
        List<NodeInfo> nodes = proxy.networkMapSnapshot();
        System.out.println("number of nodes: " + nodes.size());
        List<String> nodeNames = new ArrayList<>(nodes.size());
        System.out.println("number of node names: " + nodeNames.size());
        for (int i=0; i<nodes.size(); i++) {
            if (nodes.get(i).getLegalIdentities().size() == 0) {
                System.out.println("#" + i + " node has no legal identities!");
            }
            nodeNames.add(i, nodes.get(i).getLegalIdentities().get(0).getName().toString());
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(nodeNames);
    }

    @GetMapping(value = "/nodeTokenBalance", produces = APPLICATION_JSON_VALUE, headers = "Content-Type=application/json")
    public ResponseEntity<String> getNodeBalance() {
        Vault.Page<FungibleToken> statesPage = proxy.vaultQuery(FungibleToken.class);
        List<StateAndRef<FungibleToken>> states = statesPage.getStates();
        if (states.size()==0) {
            return ResponseEntity.status(HttpStatus.OK).body("{\"tokenBalance\":0}");
        }
        long size = 0;
        for (StateAndRef<FungibleToken> state : states) {
            size += state.getState().getData().getAmount().getQuantity();
        }
        return ResponseEntity.status(HttpStatus.OK).body("{\"tokenBalance\":"+size+"}");
    }

    @GetMapping(path = "/transactionDetails", produces = APPLICATION_JSON_VALUE, headers = "Content-Type=application/json")
    public ResponseEntity<String> getTransactionDetails(@RequestBody String payload) throws IOException, NoSuchFieldException, SQLException, IllegalAccessException {
        if (dbConnection == null) {
            throw new IllegalAccessException("No database connection exists. Consider calling /queryByDataHash");
        }

        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        String transactionHash = jsonObject.get("transactionHash").textValue();

        Statement statement = dbConnection.createStatement();
        // TRANSACTION_ID is of type VARCHAR(144)
        ResultSet resultSet = statement.executeQuery("SELECT * FROM " +
                "INTERACTION_DATA_STATE_MODEL WHERE TRANSACTION_ID='"+transactionHash+"';");

        // TODO: make the response an object or array of objects, add all instead of just one

        List<Object> objectList = new LinkedList<>();
        while(resultSet.next()) {
            objectList.add(new Object(){
                public final String sender = resultSet.getString("grid");
                public final String receiver = resultSet.getString("oem");
                public final String dataHash = new String(Hex.encode(resultSet.getBytes("hash")));
                public final String linearId = new String(Hex.encode(resultSet.getBytes("linear_id")));
                public final long numberOfTokens = resultSet.getLong("amount");
                public final String note = resultSet.getString("note");
            });
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(objectMapper.writeValueAsString(objectList));
    }

    @GetMapping(path = "/queryByDataHash", produces = APPLICATION_JSON_VALUE, headers = "Content-Type=application/json")
    public ResponseEntity<String> queryByDataHash(@RequestBody String payload) throws NoSuchFieldException, IOException {
        ObjectNode jsonObject = objectMapper.readValue(payload, ObjectNode.class);
        String targetDataHash = jsonObject.get("dataHash").textValue();
        FieldInfo dataHash = QueryCriteriaUtils.getField("hash", InteractionDataSchemaV1.InteractionDataModel.class);
        CriteriaExpression<Object, Boolean> dataHashExpression = Builder.equal(dataHash, Hex.decode(targetDataHash));
        QueryCriteria withDataHash = new QueryCriteria.VaultCustomQueryCriteria(dataHashExpression);
        Vault.Page<ContractState> resultPage = proxy.vaultQueryByCriteria(withDataHash, InteractionDataState.class);

        ArrayList<Object> objectList = new ArrayList<>(resultPage.getStates().size());
        for (int i = 0; i < resultPage.getStates().size(); i++) {
            Vault.StateMetadata metadata = resultPage.getStatesMetadata().get(0);
            InteractionDataState state = (InteractionDataState) resultPage.getStates().get(0).getState().getData();
            //StateRef ref = resultPage.getStates().get(0).getRef();
            objectList.add(i, new Object(){
                public final Instant timeStamp = metadata.getRecordedTime();
                public final String sender = state.getGrid().getName().getX500Principal().getName();
                public final String receiver = state.getOem().getName().getX500Principal().getName();
                public final long numberOfTokensTransferred = state.getAmount();
                public final String note = state.getNote();
            });
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(objectMapper.writeValueAsString(objectList));
    }
}