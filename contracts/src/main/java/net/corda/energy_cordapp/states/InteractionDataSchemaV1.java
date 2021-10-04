package net.corda.energy_cordapp.states;

import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.UUID;

public class InteractionDataSchemaV1 extends MappedSchema {
    public InteractionDataSchemaV1() {
        super(InteractionDataSchema.class, 1, Arrays.asList(InteractionDataModel.class));
    }

    @Entity
    @Table(name="interaction_data_state_model")
    public static class InteractionDataModel extends PersistentState {

        @Column(name="hash")
        private final byte[] hash;
        @Column(name="linear_id", columnDefinition = "varbinary not null")
        public final UUID linearId;
        @Column(name="grid")
        private final Party grid;
        @Column(name="oem")
        private final Party oem;
        @Column(name="amount")
        private final long amount;
        @Column(name="note")
        private final String note;


        public InteractionDataModel(byte[] hash, UUID linearId, Party grid, Party oem, long amount, String note) {
            this.hash = hash;
            this.linearId = linearId;
            this.grid = grid;
            this.oem = oem;
            this.amount = amount;
            this.note = note;
        }

        public InteractionDataModel() {
            this.hash = new byte[]{};
            this.linearId = UUID.randomUUID();
            this.grid = null;
            this.oem = null;
            this.amount = 0;
            this.note = "";

        }

        public byte[] getHash() {
            return hash;
        }

        public Party getGrid() {
            return grid;
        }

        public Party getOem() {
            return oem;
        }

        public long getAmount() {
            return amount;
        }

        public String getNote() {
            return note;
        }
    }
}
