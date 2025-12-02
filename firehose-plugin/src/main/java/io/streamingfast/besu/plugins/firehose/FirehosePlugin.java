package io.streamingfast.besu.plugins.firehose;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
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

  private ServiceManager serviceManager;
  private Firehose firehose;

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering Firehose Plugin");
    this.serviceManager = context;
    this.firehose = new Firehose();
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
    serviceManager
        .getService(BesuEvents.class)
        .ifPresentOrElse(this::startEvents, () -> LOG.error("Could not obtain BesuEvents"));
  }

  @Override
  public void stop() {
    LOG.info("Stopping Firehose Plugin");
    serviceManager
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
    firehose.printBlockToFirehose(propagatedBlockContext);
  }

}
