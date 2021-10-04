package net.corda.energy_cordapp;

import com.google.common.collect.ImmutableList;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class DriverBasedTest {
    private final CordaX500Name gridName = new CordaX500Name("Grid", "London", "GB");
    private final CordaX500Name volkswagenName = new CordaX500Name("Volkswagen", "Munich", "DE");
    private final CordaX500Name parsedataName = new CordaX500Name("Parsedata", "Toronto", "CA");

    @Test
    public void nodeTest() {
        TestCordapp accountUtilities = TestCordapp.findCordapp("net.corda.energy_cordapp.accountUtilities");
        TestCordapp flows = TestCordapp.findCordapp("net.corda.energy_cordapp.flows");
        driver(new DriverParameters()
                .withIsDebug(true)
                .withStartNodesInProcess(true)
                .withCordappsForAllNodes(ImmutableList.of(accountUtilities, flows)), dsl -> {
            // Start a pair of nodes and wait for them both to be ready.

            List<CordaFuture<NodeHandle>> handleFutures = ImmutableList.of(
                    dsl.startNode(new NodeParameters().withProvidedName(gridName)),
                    dsl.startNode(new NodeParameters().withProvidedName(volkswagenName)),
                    dsl.startNode(new NodeParameters().withProvidedName(parsedataName))
            );
            try {
                NodeHandle partyAHandle = handleFutures.get(0).get();
                NodeHandle partyBHandle = handleFutures.get(1).get();
                NodeHandle partyCHandle = handleFutures.get(2).get();

                // From each node, make an RPC call to retrieve another node's name from the network map, to verify that the
                // nodes have started and can communicate.

                // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
                // and other important metrics to ensure that your CorDapp is working as intended.
                assertEquals(partyAHandle.getRpc().wellKnownPartyFromX500Name(gridName).getName(), gridName);
                assertEquals(partyBHandle.getRpc().wellKnownPartyFromX500Name(volkswagenName).getName(), volkswagenName);
                assertEquals(partyCHandle.getRpc().wellKnownPartyFromX500Name(parsedataName).getName(), parsedataName);

            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test: ", e);
            }

            return null;
        });
    }
}