package io.github.tt432.dokimobs;

import java.util.Collection;
import java.util.List;

/**
 * @author TT432
 */
public interface CosPackFile {
    void export(String path);

    void onLoad();

    /**
     * 本包注册到 eyelib ClientEntityManager 的实体 identifier。
     * 用于限定 doki 短路开关（useBuiltInRenderSystem）的写入范围，
     * 避免旁路其他 eyelib mod 注册的模型替换。
     */
    default Collection<String> identifiers() {
        return List.of();
    }
}
