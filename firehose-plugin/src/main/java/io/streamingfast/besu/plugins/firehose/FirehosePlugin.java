package io.streamingfast.besu.plugins.firehose;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;

import org.apache.tuweni.units.bigints.UInt256;
import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The AutoService annotation (when paired with the corresponding annotation processor) will
// automatically handle adding the relevant META-INF files so Besu will load this plugin.
@AutoService(BesuPlugin.class)
public class FirehosePlugin implements BesuPlugin {

  private static Logger LOG = LogManager.getLogger();
  private static String PLUGIN_NAME = "Firehose";

  private ServiceManager serviceManager;
  private FirehoseTracer firehose;
  private BlockchainService blockchainService;

  // Event listeners
  private long blockPropagatedListener;
  private long blockAddedListener;

  @Override
  public Optional<String> getName() {
    return Optional.of(PLUGIN_NAME);
  }

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering Firehose Plugin");
    this.serviceManager = context;
    this.firehose = new FirehoseTracer();
  }

  @Override
  public void beforeExternalServices() {
    LOG.info("Preparing plugin before external services start");
  }

  @Override
  public java.util.concurrent.CompletableFuture<Void> reloadConfiguration() {
    LOG.info("Reloading plugin configuration");
    return java.util.concurrent.CompletableFuture.completedFuture(null);
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public void start() {
    LOG.info("Firehose plugin initialized");
    firehose.onBlockchainInit();

    // Get BlockchainService for blockchain access
    // TODO: This is a temporary solution to send all existing blocks to firehose. We need to find a
    // better way to do this.
    serviceManager.getService(BlockchainService.class).ifPresentOrElse(blockchainService -> {
      this.blockchainService = blockchainService;
      sendAllExistingBlocks(blockchainService);
    }, () -> LOG.error("Could not obtain BlockchainService"));

    serviceManager.getService(BesuEvents.class).ifPresentOrElse(this::startEvents,
        () -> LOG.error("Could not obtain BesuEvents"));
  }

  @Override
  public void stop() {
    LOG.info("Stopping Firehose Plugin");
    serviceManager.getService(BesuEvents.class).ifPresentOrElse(this::stopEvents,
        () -> LOG.error("Could not obtain BesuEvents"));
  }

  //
  // Events
  //
  private void startEvents(final BesuEvents events) {
    blockPropagatedListener = events.addBlockPropagatedListener(this::onBlockPropagated);
    blockAddedListener = events.addBlockAddedListener(this::onBlockAdded);
  }

  private void stopEvents(final BesuEvents events) {
    events.removeBlockPropagatedListener(blockPropagatedListener);
    events.removeBlockAddedListener(blockAddedListener);
  }

  private void onBlockAdded(final AddedBlockContext context) {
    firehose.onBlockEnd(context);
  }


  private void onBlockPropagated(final PropagatedBlockContext context) {
    firehose.onBlockStart(context);
  }


  /**
   * TODO: This is a temporary solution to send all existing blocks to firehose. We need to find a
   * better way to do this.
   */

  private void sendAllExistingBlocks(final BlockchainService blockchainService) {
    try {
      // Get the current chain head
      BlockHeader headHeader = blockchainService.getChainHeadHeader();
      if (headHeader == null) {
        LOG.error("No chain head found, blockchain may be empty");
        return;
      }

      long headBlockNumber = headHeader.getNumber();

      // Send all blocks from genesis to head
      for (long blockNum = 0; blockNum <= headBlockNumber; blockNum++) {
        var blockContext = blockchainService.getBlockByNumber(blockNum);
        if (blockContext.isPresent()) {
          sendBlock(blockContext.get());
        } else {
          LOG.warn("Block {} not found in blockchain, stopping at block {}", blockNum,
              blockNum - 1);
          break;
        }
      }

    } catch (Exception e) {
      LOG.error("Failed to send existing blocks to firehose", e);
    }
  }

  private void sendBlock(final BlockContext blockContext) {
    try {
      // Get transaction receipts for this block
      var receipts =
          blockchainService.getReceiptsByBlockHash(blockContext.getBlockHeader().getBlockHash())
              .orElse(new ArrayList<>());

      // Create context implementations
      HistoricalPropagatedBlockContext propagatedContext =
          new HistoricalPropagatedBlockContext(blockContext);
      HistoricalAddedBlockContext addedContext =
          new HistoricalAddedBlockContext(blockContext, receipts);

      firehose.onBlockStart(propagatedContext);
      firehose.onBlockEnd(addedContext);
    } catch (Exception e) {
      LOG.error("Failed to send block {} to firehose", blockContext.getBlockHeader().getNumber(),
          e);
    }
  }

  private static class HistoricalPropagatedBlockContext implements PropagatedBlockContext {
    private final BlockContext blockContext;

    public HistoricalPropagatedBlockContext(BlockContext blockContext) {
      this.blockContext = blockContext;
    }

    @Override
    public BlockHeader getBlockHeader() {
      return blockContext.getBlockHeader();
    }

    @Override
    public BlockBody getBlockBody() {
      return blockContext.getBlockBody();
    }

    @Override
    public UInt256 getTotalDifficulty() {
      // For historical blocks, we don't have total difficulty available
      // Return zero as a placeholder
      return UInt256.ZERO;
    }
  }

  private static class HistoricalAddedBlockContext implements AddedBlockContext {
    private final BlockContext blockContext;
    private final List<? extends TransactionReceipt> receipts;

    public HistoricalAddedBlockContext(BlockContext blockContext,
        List<? extends TransactionReceipt> receipts) {
      this.blockContext = blockContext;
      this.receipts = receipts;
    }

    @Override
    public BlockHeader getBlockHeader() {
      return blockContext.getBlockHeader();
    }

    @Override
    public BlockBody getBlockBody() {
      return blockContext.getBlockBody();
    }

    @Override
    public List<? extends TransactionReceipt> getTransactionReceipts() {
      return receipts;
    }
  }

}
