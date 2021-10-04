package net.corda.energy_cordapp.states;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;

public class EnergyTokenType extends TokenType {

    public EnergyTokenType() {
        super("DLR", 0);
    }
}
