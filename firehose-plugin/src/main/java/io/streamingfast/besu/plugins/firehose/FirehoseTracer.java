package io.streamingfast.besu.plugins.firehose;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.data.Withdrawal;

import pbeth.TypeOuterClass.Block;

/**
 * FirehoseTracer handles block tracing in Firehose protocol format to stdout. Adapted from the Go
 * Firehose implementation with OnBlockStart/OnBlockEnd flow.
 */
public class FirehoseTracer {

    private static final String FIREHOSE_PROTOCOL_VERSION = "3.0";
    // TODO: Add proper firehose logger as per GO (need to check the flags and make
    // sure it doesn't cost anything unless flags are set)
    private static final Logger LOG = LogManager.getLogger(FirehoseTracer.class);

    private final AtomicBoolean initSent = new AtomicBoolean(false);
    private final FirehoseConfig config = new FirehoseConfig();

    // Block state
    private Block protobufBlock;
    private Quantity blockBaseFee;
    // TODO: Need to validate this
    private final FinalityStatus blockFinality = new FinalityStatus(0L, new byte[0]);

    private String getVersion() {
        return "1.0.0";
    }

    /**
     * Firehose events
     */
    public void onBlockchainInit() {
        if (initSent.compareAndSet(false, true)) {
            printToFirehose("INIT", FIREHOSE_PROTOCOL_VERSION, "besu", getVersion());
        } else {
            throw new IllegalStateException(
                    "The OnBlockchainInit callback was called more than once");
        }
    }

    public void onBlockStart(final PropagatedBlockContext propagatedBlockContext) {
        ensureBlockchainInit();
        LOG.debug("onBlockStart called for block number: {}",
                propagatedBlockContext.getBlockHeader().getNumber());
        initializeBlockState(propagatedBlockContext.getBlockHeader(),
                propagatedBlockContext.getBlockBody());
    }

    public void onBlockEnd(final AddedBlockContext addedBlockContext) {
        // TODO Check some err conditions

        LOG.debug("onBlockEnd called for block number: {}",
                addedBlockContext.getBlockHeader().getNumber());

        // Initialize block state if not already done (onBlockPropagated might not have
        // been called)
        if (protobufBlock == null) {
            LOG.debug("Block not initialized in onBlockPropagated, initializing from onBlockAdded");
            initializeBlockState(addedBlockContext.getBlockHeader(),
                    addedBlockContext.getBlockBody());
        }

        if (protobufBlock == null) {
            LOG.error("protobufBlock is still null after initialization, cannot print to firehose");
            return;
        }

        printProtobufBlockToFirehose(protobufBlock);
        resetBlock();
    }

    /**
     * Block management
     */
    private void initializeBlockState(final BlockHeader header, final BlockBody body) {
        LOG.debug("block start (number={} hash={})", header.getNumber(), header.getBlockHash());

        try {
            blockBaseFee = header.getBaseFee().orElse(null);
            // Build protobuf Block using converter
            this.protobufBlock = BlockProtoConverter.convertToProto(header, body, blockBaseFee);
            blockFinality.populateFromHeader(header);
        } catch (Throwable t) {
            LOG.error("Failed to initialize block state for block {}: {}", header.getNumber(),
                    t.getMessage(), t);
            this.protobufBlock = null;
        }
    }

    private void resetBlock() {
        protobufBlock = null;
        blockBaseFee = null;
        blockFinality.reset();
    }

    /**
     * Print utils
     */
    public void printToFirehose(String... input) {
        String message = "FIRE " + String.join(" ", input) + "\n";
        flushToFirehose(message.getBytes());
    }

    private void flushToFirehose(byte[] data) {
        OutputStream writer = System.out;
        int written = 0;
        int loops = 10;

        for (int i = 0; i < loops; i++) {
            try {
                writer.write(data, written, data.length - written);
                writer.flush();
                return;
            } catch (IOException e) {
                written += data.length - (data.length - written);
                if (i == loops - 1) {
                    String errstr = String.format("\nFIREHOSE FAILED WRITING %dx: %s\n", loops,
                            e.getMessage());
                    System.err.println(errstr);
                    break;
                }
            }
        }
    }

    private void printProtobufBlockToFirehose(Block protobufBlock) {
        if (protobufBlock == null) {
            LOG.warn("Attempted to print null block to firehose");
            return;
        }

        LOG.debug("Printing block {} to firehose", protobufBlock.getNumber());

        try {

            byte[] blockBytes = protobufBlock.toByteArray();
            String base64Data = Base64.getEncoder().encodeToString(blockBytes);

            long blockNum = protobufBlock.getNumber();
            String blockHash = "0x" + bytesToHex(protobufBlock.getHash().toByteArray());
            long timestamp = protobufBlock.getHeader().getTimestamp().getSeconds();
            String previousHash =
                    "0x" + bytesToHex(protobufBlock.getHeader().getParentHash().toByteArray());
            long previousNum = blockNum > 0 ? blockNum - 1 : 0;

            long libNum = blockFinality.getLastIrreversibleBlockNumber();
            if (blockFinality.isEmpty()) {
                if (blockNum >= 200) {
                    libNum = blockNum - 200;
                } else {
                    libNum = 0;
                }
            }

            String header = String.format("FIRE BLOCK %d %s %d %s %d %d ", blockNum, blockHash,
                    previousNum, previousHash, libNum, timestamp);
            String fullMessage = header + base64Data + "\n";
            flushToFirehose(fullMessage.getBytes());
        } catch (Throwable t) {
            LOG.error("Failed to print protobuf block to firehose for block {}: {}",
                    protobufBlock.getNumber(), t.getMessage(), t);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private void ensureBlockchainInit() {
        if (this.config == null) {
            throw new IllegalStateException("Firehose config is not set");
        }
    }
}
