package joshie.progression.asm;

import joshie.progression.asm.helpers.ASMHelper;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProgressionTransformer implements IFMLLoadingPlugin, IClassTransformer {
    public static boolean isObfuscated = false;
    public static List<AbstractASM> asm = new ArrayList();

    static {
        ASMConfig c = ASMHelper.getASMConfig();
        if (c.furnace) asm.add(new ASMFurnace());
        if (c.transfer) asm.add(new ASMTransferCrafting());
        if (c.player) asm.add(new ASMContainerPlayer());
        if (c.workbench) asm.add(new ASMContainerWorkbench());
        if (c.tinkers) asm.add(new ASMTinkers());
        if (c.thaumcraft) asm.add(new ASMThaumcraft());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] data) {
        byte[] modified = data;
        for (AbstractASM a : asm) {
            if (a.isClass(name)) {
                if (a.isVisitor()) {
                    ClassReader cr = new ClassReader(modified);
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor cv = a.newInstance(name, cw);
                    cr.accept(cv, ClassReader.EXPAND_FRAMES);
                    modified = cw.toByteArray();
                }

                modified = a.transform(modified);
            }
        }

        return modified;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { ProgressionTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        isObfuscated = ((Boolean) data.get("runtimeDeobfuscationEnabled"));
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
