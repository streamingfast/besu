package io.streamingfast.besu.plugins.firehose;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The AutoService annotation (when paired with the corresponding annotation processor) will
// automatically handle adding the relevant META-INF files so Besu will load this plugin.
@AutoService(BesuPlugin.class)
public class FirehosePlugin implements BesuPlugin {

  private static Logger LOG = LogManager.getLogger();

  private static String PLUGIN_NAME = "firehose";

  @Override
  public Optional<String> getName() {
    return Optional.of("Firehose");
  }

  private BesuContext context;

  @Override
  public void register(final BesuContext context) {
    LOG.info("Registering Firehose Plugin");
    this.context = context;
  }

  @Override
  public void start() {
    LOG.info("Firehose plugin initialized");
    printToFirehose("INIT", "Firehose plugin initialized");
    context
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::startEvents, () -> LOG.error("Could not obtain BesuEvents"));
  }

  @Override
  public void stop() {
    LOG.info("Stopping Firehose Plugin");
    context
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::stopEvents, () -> LOG.error("Could not obtain BesuEvents"));
  }

  //
  // Events
  //

  private long listenerIdentifier;

  private void startEvents(final BesuEvents events) {
    listenerIdentifier = events.addBlockPropagatedListener(this::onBlockPropagated);
  }

  private void stopEvents(final BesuEvents events) {
    events.removeBlockPropagatedListener(listenerIdentifier);
  }

  private void onBlockPropagated(final PropagatedBlockContext propagatedBlockContext) {
    // Output in Firehose format
    printBlockToFirehose(propagatedBlockContext);
  }

  // Firehose output methods (adapted from Go implementation)

  /**
   * printToFirehose is an easy way to print to Firehose format, it essentially
   * adds the "FIRE" prefix to the input and joins the input with spaces as well
   * as adding a newline at the end.
   */
  private void printToFirehose(String... input) {
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
   * printBlockToFirehose formats and prints a block in Firehose protocol format.
   * Adapted for Besu plugin context - you'll need to fill in the protobuf marshaling.
   */
  private void printBlockToFirehose(PropagatedBlockContext propagatedBlockContext) {
    long blockNum = propagatedBlockContext.getBlockHeader().getNumber();
    String blockHash = propagatedBlockContext.getBlockHeader().getHash().toString();
    long timestamp = propagatedBlockContext.getBlockHeader().getTimestamp();

    // Previous block info
    String previousHash = "0x" + "0".repeat(64); // Default for genesis block
    long previousNum = 0;

    if (blockNum > 0) {
      previousNum = blockNum - 1;
      // You'll need to get the parent hash from the block header
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
