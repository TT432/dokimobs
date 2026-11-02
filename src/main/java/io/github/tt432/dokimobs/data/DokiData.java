package io.github.tt432.dokimobs.data;

import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.adapter.DataAttachmentTypeRegistry;
import io.github.tt432.eyelib.util.dataattach.DataAttachmentType;

import java.util.function.Supplier;

/**
 * dokimobs 的实体附属数据类型注册。
 * 统一走 eyelib 的 DataAttachment 体系（三版本同一 API），
 * DeferredRegister 由 eyelib 在 mod 构造时挂到事件总线，这里只需在 registry 事件前完成类加载。
 * <p>
 * 两个类型都用「附件存在」表达「已掷骰」：默认值工厂只是为了满足注册 API，
 * 读取请一律走 {@code getOrNull}。
 *
 * @author TT432
 */
public class DokiData {
    public static final Supplier<DataAttachmentType<EntityDokiData>> DOKI =
            DataAttachmentTypeRegistry.DATA_ATTACHMENTS.register("doki_data",
                    () -> new DataAttachmentType<>("eyelib:doki_data",
                            () -> new EntityDokiData(false),
                            EntityDokiData.CODEC,
                            EntityDokiData.STREAM_CODEC));

    public static final Supplier<DataAttachmentType<EntityDokiVariant>> VARIANT =
            DataAttachmentTypeRegistry.DATA_ATTACHMENTS.register("doki_variant",
                    () -> new DataAttachmentType<>("eyelib:doki_variant",
                            () -> new EntityDokiVariant(0, 0),
                            EntityDokiVariant.CODEC,
                            EntityDokiVariant.STREAM_CODEC));

    private DokiData() {
    }

    /**
     * 触发本类静态初始化以完成注册（no-op）。
     */
    public static void init() {
    }
}
