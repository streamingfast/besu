package io.streamingfast.besu.plugins.firehose;

import pbeth.TypeOuterClass.Block;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.Withdrawal;

import java.util.List;

/**
 * FirehoseBlock wraps the protobuf pbeth.Block and provides the same interface
 * as the previous FirehoseBlock implementation.
 */
// TODO: Validate, this might not be needed anymore.
public class FirehoseBlock {
    private final Block protobufBlock;

    private final Quantity baseFee;
    private final BlockHeader besuHeader;
    private final List<BlockHeader> besuUncles;
    private final List<Withdrawal> besuWithdrawals;

    public FirehoseBlock(final Block protobufBlock, final Quantity baseFee, final BlockHeader besuHeader,
            final List<BlockHeader> besuUncles, final List<Withdrawal> besuWithdrawals) {
        this.protobufBlock = protobufBlock;
        this.baseFee = baseFee;
        this.besuHeader = besuHeader;
        this.besuUncles = besuUncles;
        this.besuWithdrawals = besuWithdrawals;
    }

    public Hash getHash() {
        return Hash.fromHexString("0x" + bytesToHex(protobufBlock.getHash().toByteArray()));
    }

    public long getNumber() {
        return protobufBlock.getNumber();
    }

    public BlockHeader getHeader() {
        return besuHeader;
    }

    public int getVersion() {
        return protobufBlock.getVer();
    }

    public long getSize() {
        return protobufBlock.getSize();
    }

    public List<BlockHeader> getUncles() {
        return besuUncles;
    }

    public List<Withdrawal> getWithdrawals() {
        return besuWithdrawals;
    }

    public Quantity getBaseFee() {
        return baseFee;
    }

    public Block getProtobufBlock() {
        return protobufBlock;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}