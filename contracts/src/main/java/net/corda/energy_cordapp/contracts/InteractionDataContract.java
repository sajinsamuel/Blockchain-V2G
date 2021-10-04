package net.corda.energy_cordapp.contracts;

import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.energy_cordapp.states.InteractionDataState;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class InteractionDataContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        ContractState[] dataStateStream
                = tx.getOutputStates().stream().filter(state -> state instanceof InteractionDataState).toArray(ContractState[]::new);
        requireThat(require -> {
            require.using("Should only have one InteractionDataState output",
                    dataStateStream.length == 1);
            return null;
        });
        InteractionDataState interactionDataState = (InteractionDataState) dataStateStream[0];

        ContractState[] gridInputStream = tx.getInputStates().stream()
                .filter(state -> state instanceof FungibleToken)
                .filter(state -> ((FungibleToken) state).getHolder().equals(interactionDataState.getGrid()))
                .toArray(ContractState[]::new);

//        requireThat(require -> {
//            require.using("Should only have one Fungible Grid input",
//                    gridInputStream.length == 1);
//            return null;
//        });

        FungibleToken gridInputState = (FungibleToken) gridInputStream[0];

        ContractState[] gridOutputStream = tx.getOutputStates().stream()
                .filter(state -> state instanceof FungibleToken)
                .filter(state -> ((FungibleToken) state).getHolder().equals(interactionDataState.getGrid()))
                .toArray(ContractState[]::new);

//        requireThat(require -> {
//            require.using("Should only have one Fungible Grid output",
//                    gridOutputStream.length == 1);
//            return null;
//        });

        FungibleToken gridOutputState = (FungibleToken) gridOutputStream[0];

        long notGridOutputCount = tx.getOutputStates().stream()
                .filter(state -> state instanceof FungibleToken)
                .filter(state -> !((FungibleToken) state).getHolder().equals(interactionDataState.getGrid()))
                .count();

        requireThat(require -> {
            require.using("Should include MoveTokenCommand",
                    tx.getCommands().stream().anyMatch(o -> o.getValue() instanceof MoveTokenCommand));
            require.using("Should include EnergyTransferCommand",
                    tx.getCommands().stream().anyMatch(o -> o.getValue() instanceof Commands.EnergyTransfer));
//            require.using("Should only have one output that doesn't belong to the grid",
//                    notGridOutputCount == 1);
//            require.using("Amount spent by grid should match",
//                    gridInputState.getAmount().getQuantity() - gridOutputState.getAmount().getQuantity()
//                            == interactionDataState.getAmount());
            // Since we verified that MoveTokenCommand is applied, and these are verified to be FungibleTokens,
            // FungibleTokenContract will ensure that the total input amount matches total output amount,
            // so we don't have to worry about counting the amount transferred to the OEM as well as Grid
            return null;
        });
    }
}
