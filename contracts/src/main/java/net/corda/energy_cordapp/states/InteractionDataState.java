package net.corda.energy_cordapp.states;

import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import net.corda.energy_cordapp.contracts.InteractionDataContract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(InteractionDataContract.class)
public class InteractionDataState implements ContractState, LinearState, QueryableState {
    private final Party grid;
    private final Party oem;
    private final Party sanctionsBody;
    private final byte[] hash;
    private final UniqueIdentifier linearId = new UniqueIdentifier();

    private final long amount;
    private final String note;

    public InteractionDataState(Party grid, Party oem, Party sanctionsBody, byte[] hash, long amount, String note) {
        this.grid = grid;
        this.oem = oem;
        this.sanctionsBody = sanctionsBody;
        this.hash = hash;
        this.amount = amount;
        this.note = note;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(grid, oem, sanctionsBody);
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    public byte[] getHash() {
        return hash;
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(@NotNull MappedSchema schema) {
        if (schema instanceof InteractionDataSchemaV1) {
            return new InteractionDataSchemaV1.InteractionDataModel(hash, linearId.getId(), grid, oem, amount, note);
        } else {
            throw new IllegalArgumentException("No supported schema found");
        }
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return Arrays.asList(new InteractionDataSchemaV1());
    }
    public Party getGrid() {
        return grid;
    }

    public Party getOem() {
        return oem;
    }

    public Party getSanctionsBody() {
        return sanctionsBody;
    }

    public long getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }
}
