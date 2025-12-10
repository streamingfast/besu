package io.streamingfast.besu.plugins.firehose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.Withdrawal;

import com.google.protobuf.ByteString;
import pbeth.TypeOuterClass;

/**
 * Converts Besu block data to Firehose protobuf Block format.
 */
public class BlockProtoConverter {

    public static TypeOuterClass.Block convertToProto(BlockHeader header, BlockBody body, Quantity baseFee) {
        TypeOuterClass.Block.Builder blockBuilder = TypeOuterClass.Block.newBuilder()
                .setHash(ByteString.copyFrom(header.getBlockHash().toArrayUnsafe()))
                .setNumber(header.getNumber())
                .setSize(calculateBlockSize(body))
                .setVer(4)
                .setHeader(convertBlockHeader(header, baseFee))
                .setDetailLevel(TypeOuterClass.Block.DetailLevel.DETAILLEVEL_BASE); // TODO: Update to EXTENDED when
                                                                                    // traces are added

        // Add uncles
        for (BlockHeader uncle : body.getOmmers()) {
            blockBuilder.addUncles(convertBlockHeader(uncle, null));
        }

        // TODO: Add transactions when transaction tracing is implemented
        // For now, we leave transaction_traces empty

        return blockBuilder.build();
    }

    private static TypeOuterClass.BlockHeader convertBlockHeader(BlockHeader besuHeader, Quantity baseFee) {
        TypeOuterClass.BlockHeader.Builder builder = TypeOuterClass.BlockHeader.newBuilder()
                .setParentHash(ByteString.copyFrom(besuHeader.getParentHash().toArrayUnsafe()))
                .setUncleHash(ByteString.copyFrom(besuHeader.getOmmersHash().toArrayUnsafe()))
                .setCoinbase(ByteString.copyFrom(besuHeader.getCoinbase().toArrayUnsafe()))
                .setStateRoot(ByteString.copyFrom(besuHeader.getStateRoot().toArrayUnsafe()))
                .setTransactionsRoot(ByteString.copyFrom(besuHeader.getTransactionsRoot().toArrayUnsafe()))
                .setReceiptRoot(ByteString.copyFrom(besuHeader.getReceiptsRoot().toArrayUnsafe()))
                .setLogsBloom(ByteString.copyFrom(besuHeader.getLogsBloom().toArrayUnsafe()))
                .setNumber(besuHeader.getNumber())
                .setGasLimit(besuHeader.getGasLimit())
                .setGasUsed(besuHeader.getGasUsed())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(besuHeader.getTimestamp())
                        .build())
                .setExtraData(ByteString.copyFrom(besuHeader.getExtraData().toArrayUnsafe()))
                .setMixHash(ByteString.copyFrom(besuHeader.getMixHash().toArrayUnsafe()))
                .setNonce(besuHeader.getNonce())
                .setHash(ByteString.copyFrom(besuHeader.getBlockHash().toArrayUnsafe()));

        // Set difficulty
        builder.setDifficulty(TypeOuterClass.BigInt.newBuilder()
                .setBytes(ByteString.copyFrom(besuHeader.getDifficulty().getAsBigInteger().toByteArray()))
                .build());

        // Set base fee if present
        if (baseFee != null) {
            builder.setBaseFeePerGas(TypeOuterClass.BigInt.newBuilder()
                    .setBytes(ByteString.copyFrom(baseFee.getAsBigInteger().toByteArray()))
                    .build());
        }

        return builder.build();
    }

    private static long calculateBlockSize(BlockBody body) {
        // TODO: Implement proper RLP size calculation
        // For now, return transaction count as approximation
        return body.getTransactions().size();
    }
}