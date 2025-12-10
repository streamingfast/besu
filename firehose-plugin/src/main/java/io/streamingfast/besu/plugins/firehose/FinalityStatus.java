package io.streamingfast.besu.plugins.firehose;

import org.hyperledger.besu.plugin.data.BlockHeader;

public class FinalityStatus {
    private long lastIrreversibleBlockNumber;
    private byte[] lastIrreversibleBlockHash;

    public FinalityStatus(long lastIrreversibleBlockNumber, byte[] lastIrreversibleBlockHash) {
        this.lastIrreversibleBlockNumber = lastIrreversibleBlockNumber;
        this.lastIrreversibleBlockHash = lastIrreversibleBlockHash;
    }

    public long getLastIrreversibleBlockNumber() {
        return lastIrreversibleBlockNumber;
    }

    public void setLastIrreversibleBlockNumber(long lastIrreversibleBlockNumber) {
        this.lastIrreversibleBlockNumber = lastIrreversibleBlockNumber;
    }

    public byte[] getLastIrreversibleBlockHash() {
        return lastIrreversibleBlockHash;
    }

    public void setLastIrreversibleBlockHash(byte[] lastIrreversibleBlockHash) {
        this.lastIrreversibleBlockHash = lastIrreversibleBlockHash;
    }

    /**
     * Resets the lastIrreversibleBlockNumber and lastIrreversibleBlockHash to their
     * default values.
     */
    public void reset() {
        this.lastIrreversibleBlockNumber = 0;
        this.lastIrreversibleBlockHash = null;
    }

    /** */
    public void populateFromHeader(BlockHeader header) {
        if (header == null) {
            reset();
            return;
        }
        this.lastIrreversibleBlockNumber = header.getNumber();
        if (header.getBlockHash() != null) {
            this.lastIrreversibleBlockHash = header.getBlockHash().toArray();
        } else {
            this.lastIrreversibleBlockHash = null;
        }
    }

    public boolean isEmpty() {
        return lastIrreversibleBlockHash == null;
    }
}
