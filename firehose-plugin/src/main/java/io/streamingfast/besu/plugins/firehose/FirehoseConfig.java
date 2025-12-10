package io.streamingfast.besu.plugins.firehose;

public class FirehoseConfig {
    private final boolean traceBlockWithdrawals;

    public FirehoseConfig() {
        this(true);
    }

    public FirehoseConfig(final boolean traceBlockWithdrawals) {
        this.traceBlockWithdrawals = traceBlockWithdrawals;
    }

    public boolean traceBlockWithdrawals() {
        return traceBlockWithdrawals;
    }
}
