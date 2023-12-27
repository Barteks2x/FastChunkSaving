package io.github.barteks2x.fastchunksaving.mixinconfig;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class FastChunkSavingTransformerMixinConfigPlugin implements IMixinConfigPlugin {

    @Override public void onLoad(String mixinPackage) {

    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        for (MethodNode method : targetClass.methods) {
            if (method.invisibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode ann : method.invisibleAnnotations) {
                if (ann.desc.equals("Lio/github/barteks2x/fastchunksaving/mixinconfig/NotSynchronized;")) {
                    method.access &= ~Opcodes.ACC_SYNCHRONIZED;
                }
            }
        }
    }
}
