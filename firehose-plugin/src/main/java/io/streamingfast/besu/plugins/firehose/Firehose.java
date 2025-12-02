package io.streamingfast.besu.plugins.firehose;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Firehose handles output in Firehose protocol format to stdout.
 * Adapted from the Go Firehose implementation.
 */
public class Firehose {

    private static final String FIREHOSE_PROTOCOL_VERSION = "3.0";

    // Atomic boolean to ensure init is only sent once
    private final AtomicBoolean initSent = new AtomicBoolean(false);

    /**
     * Initialize the Firehose with blockchain configuration.
     * Mirrors the Go OnBlockchainInit method.
     */
    public void onBlockchainInit() {
        if (initSent.compareAndSet(false, true)) {
            printToFirehose("INIT", FIREHOSE_PROTOCOL_VERSION, "besu", getVersion());
        } else {
            throw new IllegalStateException("The OnBlockchainInit callback was called more than once");
        }
    }

    /**
     * Get the version. For now, using a placeholder - you might want to get this from Besu.
     */
    private String getVersion() {
        // TODO: Get actual Besu version
        return "1.0.0";
    }

    /**
     * printToFirehose is an easy way to print to Firehose format, it essentially
     * adds the "FIRE" prefix to the input and joins the input with spaces as well
     * as adding a newline at the end.
     */
    public void printToFirehose(String... input) {
        String message = "FIRE " + String.join(" ", input) + "\n";
        flushToFirehose(message.getBytes());
    }

    /**
     * flushToFirehose sends data to Firehose via stdout with error handling and retrying.
     */
    private void flushToFirehose(byte[] data) {
        OutputStream writer = System.out;
        int written = 0;
        int loops = 10;

        for (int i = 0; i < loops; i++) {
            try {
                writer.write(data, written, data.length - written);
                writer.flush();
                return; // Success
            } catch (IOException e) {
                written += data.length - (data.length - written); // This is simplified
                if (i == loops - 1) {
                    String errstr = String.format("\nFIREHOSE FAILED WRITING %dx: %s\n", loops, e.getMessage());
                    System.err.println(errstr);
                    // In Go version, it writes to /tmp/firehose_writer_failed_print.log
                    // You might want to add file logging here if needed
                    break;
                }
            }
        }
    }

    /**
     * Print a block in Firehose protocol format.
     * TODO: Implement with proper protobuf marshaling
     */
    public void printBlockToFirehose(long blockNum, String blockHash, long timestamp) {
        // Previous block info
        String previousHash = "0x" + "0".repeat(64); // Default for genesis block
        long previousNum = 0;

        if (blockNum > 0) {
            previousNum = blockNum - 1;
            // TODO: You'll need to get the parent hash from the block header
            // previousHash = propagatedBlockContext.getBlockHeader().getParentHash().toString();
        }

        // LIB (Last Irreversible Block) - simplified logic
        long libNum = 0;
        if (blockNum >= 200) {
            libNum = blockNum - 200;
        }

        // TODO: Marshal block to protobuf and base64 encode
        // For now, just outputting the basic format
        String header = String.format("FIRE BLOCK %d %s %d %s %d %d ",
                                      blockNum, blockHash, previousNum, previousHash, libNum, timestamp);

        // TODO: Add base64 encoded protobuf block data here
        // String base64Block = Base64.getEncoder().encodeToString(marshalledBlock);
        // header += base64Block;

        header += "\n";

        flushToFirehose(header.getBytes());
    }
}
