package net.corda.energy_cordapp.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import net.corda.core.contracts.Amount;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

import com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilities;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilities;
import net.corda.core.utilities.ProgressTracker;
import net.corda.energy_cordapp.contracts.Commands;
import net.corda.energy_cordapp.states.EnergyTokenType;
import net.corda.energy_cordapp.states.InteractionDataState;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Contains the classes SendEnergyTokens and ReceiveEnergyTokens.
 * SendEnergyTokens is the initiating flow that is used
 * to send energy tokens from e.g. a grid node to an EV.
 * ReceiveEnergyTokens is the responding flow that signs the transaction,
 * and can be subclassed or overridden for specific checks that are to be performed
 * by the EV manufacturer or the MEC node
 */
public class EnergyTransferFlow {

    /**
     * Flow for sending energy tokens from e.g. the grid to an EV.
     * Sends EnergyTokenType FungibleTokens from a node to an account.
     * The account to send to must have been shared with the node in question.
     * The flow requires the signature of a third party called the sanctions body,
     * which will likely be a MEC node that can verify the vehicle's identity
     * and that its charge is dropping.
     */
    @InitiatingFlow
    @StartableByRPC
    public static class SendEnergyTokens extends FlowLogic<SignedTransaction> {
        private final long amount;

        // For suspendability, we will simply store String account names rather than actual Parties or AccountInfo's
        // The latter two things would require starting up services and communicating with other nodes,
        // which we do not want to do in the constructor.

        // However, if we make this into a flow in the future, the AccountInfos or AbstractParties can be passed
        // directly, so this modification is to be done in the future.
        // TODO: when making this a subflow, store either AbstractParty's or AccountInfo's as fields,
        //  and pass them directly in the constructor rather than figuring them out in call()

        // the String name of the account to send the tokens to
        private final String whereTo;

        // the sanctions body, probably Parsedata
        private final Party sanctionsBody;

        private final byte[] hash;

        private final String note;

        private final ProgressTracker.Step RETRIEVING_DATA
                = new ProgressTracker.Step("Retrieving account data");
        private final ProgressTracker.Step GENERATING_TRANSACTION
                = new ProgressTracker.Step("Generating transaction from retrieved data");
        private final ProgressTracker.Step VERIFYING_TRANSACTION
                = new ProgressTracker.Step("Verifying contract constraints");
        private final ProgressTracker.Step SIGNING_TRANSACTION
                = new ProgressTracker.Step("Signing the transaction");
        private final ProgressTracker.Step GATHERING_SIGS
                = new ProgressTracker.Step("Gathering the counterparties' signatures") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION
                = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                RETRIEVING_DATA,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        /**
         * The constructor for the flow used to send energy tokens (e.g. for charge).
         * Only allows for token transfer from a node to an account,
         * in the case of grid (node) to vehicle (account on EV manufacturer node).
         * General transfer of tokens from account to account etc (without sanctions body)
         * will be done by a different flow.
         * The node initiating this flow must have sufficient EnergyTokenType FungibleTokens to send,
         * and the account to send to must already have been shared with the sending node.
         * @param amount the amount of EnergyTokenType FungibleTokens to send
         * @param whereTo the String name of the account to which to issue the tokens
         * @param sanctionsBody the node verifying the car is discharging
         * @param hash the interaction hash
         */
        public SendEnergyTokens(long amount, String whereTo, Party sanctionsBody, byte[] hash, String note) {
            this.amount = amount;
            this.whereTo = whereTo;
            this.sanctionsBody = sanctionsBody;
            this.hash = hash;
            this.note = note;
        }

        public SendEnergyTokens(long amount, String whereTo, Party sanctionsBody, String hash, String note) {
            this.amount = amount;
            this.whereTo = whereTo;
            this.sanctionsBody = sanctionsBody;
            this.hash = hash.getBytes(StandardCharsets.UTF_8);
            this.note = note;
        }
        public SendEnergyTokens(long amount, String whereTo, Party sanctionsBody, byte[] hash) {
            this.amount = amount;
            this.whereTo = whereTo;
            this.sanctionsBody = sanctionsBody;
            this.hash = hash;
            this.note = "";
        }

        public SendEnergyTokens(long amount, String whereTo, Party sanctionsBody, String hash) {
            this.amount = amount;
            this.whereTo = whereTo;
            this.sanctionsBody = sanctionsBody;
            this.hash = hash.getBytes(StandardCharsets.UTF_8);
            this.note = "";
        }


        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(RETRIEVING_DATA);

            Party notary = NotaryUtilities.getPreferredNotary(getServiceHub());

            // getting the account service, which will then give us the account
            AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);

            // fetching the first element; this may be a bad way of doing things
            // TODO: find a preferred notary, rather than the first notary
            AccountInfo receiverAccount = accountService.accountInfo(whereTo).get(0).getState().getData();

            // the account to which to send tokens
            AnonymousParty receiver = subFlow(new RequestKeyForAccount(receiverAccount));

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // instantiate the builder object that will build the transaction,
            // pass it the notary we retrieved above
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            // add the transaction and command involved in moving fungible tokens to the transaction builder
            // this command will be used for all token transfers
            MoveTokensUtilities.addMoveFungibleTokens(
                    transactionBuilder,
                    getServiceHub(),
                    ImmutableList.of(new PartyAndAmount<>(receiver, new Amount<>(amount, new EnergyTokenType()))),
                    getOurIdentity()
                    //me
                    );

            // create the InteractionDataState that will store interaction data
            // and make this beholden to our custom contract
            InteractionDataState interactionDataState = new InteractionDataState(
                    getOurIdentity(), receiverAccount.getHost(), sanctionsBody, hash,
                    amount, note);
            transactionBuilder.addOutputState(interactionDataState);
            // add another command that mandates the signature of the sanctions body
            // along with other participants
            // this command is only used for energy transfer
            transactionBuilder.addCommand(
                    new Commands.EnergyTransfer(),
                    getOurIdentity().getOwningKey(),
                    receiver.getOwningKey(),
                    sanctionsBody.getOwningKey()
            );

            // VERIFYING_TRANSACTION
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            transactionBuilder.verify(getServiceHub());

            // SIGNING_TRANSACTION
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction meSignedTx = getServiceHub().signInitialTransaction(transactionBuilder);

            // GATHERING_SIGNATURES
            progressTracker.setCurrentStep(GATHERING_SIGS);
            FlowSession sanctionsBodySession = initiateFlow(sanctionsBody);
            FlowSession receiverSession = initiateFlow(receiverAccount.getHost());

            // send the transaction to sign to the receiver and sanctions body
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(meSignedTx,
                            ImmutableList.of(sanctionsBodySession, receiverSession))
            );

            // FINALISING_TRANSACTION
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(fullySignedTx, ImmutableList.of(receiverSession, sanctionsBodySession)));
        }
    }

    /**
     * The responding flow to the SendEnergyTokens flow.
     * Must be hosted by the EV manufacturer in a V2G scenario
     * (for the car to receive tokens from the grid),
     * and by the MEC node (to bless the transaction)
     */
    @InitiatedBy(SendEnergyTokens.class)
    public static class ReceiveEnergyTokens extends FlowLogic<SignedTransaction> {

        // store the initiating session (the session with the grid)
        private final FlowSession initiatingSession;

        /**
         * Constructor. called when this node receives the transaction to sign from the grid.
         * @param initiatingSession the session with the grid node that initiated the flow
         */
        public ReceiveEnergyTokens(FlowSession initiatingSession) {
            this.initiatingSession = initiatingSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SignTransactionFlow signTransactionFlow = new SignTransactionFlow(initiatingSession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // this will be overridden by both the vehicle manufacturer and the MEC node
                    // to implement the checks they need
                }
            };
            // actually sign the transaction and keep its ID to receive the notarized transaction
            SecureHash txId = subFlow(signTransactionFlow).getId();

            // receive the notarized transaction
            SignedTransaction recordedTx = subFlow(
                    new ReceiveFinalityFlow(
                            initiatingSession,
                            txId,
                            StatesToRecord.ALL_VISIBLE
                    )
            );
            return recordedTx;
        }
    }
}
