package io.github.tt432.dokimobs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.eyelib.util.streamcodec.EyelibStreamCodecs;
import io.github.tt432.eyelib.util.streamcodec.StreamCodec;

/**
 * dokimobs 为实体掷出的贴图变体（BE 语义的 variant / mark_variant）。
 * <p>
 * 为什么不直接读 {@code SyncedBehaviorState} 判断是否掷过骰：eyelib 在实体加入世界时
 * 也会写 {@code SyncedBehaviorState}（行为包 spawn 逻辑：zombie/chicken 的 scale、
 * fox/llama 的 variant），无法区分「行为包给的」与「dokimobs 掷的」。
 * 因此 dokimobs 自己持有一份掷骰结果，<b>附件是否存在 = 是否已掷骰</b>，
 * 区块重载只做镜像（{@code SyncedBehaviorState} 缺失时补写、值不同才写），不再重掷。
 *
 * @author TT432
 */
public record EntityDokiVariant(
        int variant,
        int markVariant
) {
    public static final Codec<EntityDokiVariant> CODEC = RecordCodecBuilder.create(ins -> ins.group(
            Codec.INT.fieldOf("variant").forGetter(EntityDokiVariant::variant),
            Codec.INT.fieldOf("mark_variant").forGetter(EntityDokiVariant::markVariant)
    ).apply(ins, EntityDokiVariant::new));

    public static final StreamCodec<EntityDokiVariant> STREAM_CODEC = EyelibStreamCodecs.fromCodec(CODEC);
}
