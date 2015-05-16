package joshie.crafting.asm;

import joshie.crafting.lib.CraftingInfo;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ASMTinkers extends AbstractASM {
    @Override
    public boolean isClass(String name) {
        return name.equals("tconstruct.tools.inventory.CraftingStationContainer");
    }

    @Override
    public ClassVisitor newInstance(ClassWriter writer) {
        return new ASMVisitor(writer);
    }

    public class ASMVisitor extends ClassVisitor {
        public ASMVisitor(ClassWriter writer) {
            super(Opcodes.ASM4, writer);
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (desc.equals("(Lnet/minecraft/inventory/IInventory;)V") || desc.equals("(Lrb;)V")) {
                if (name.equals("onCraftMatrixChanged") || name.equals("a") || name.equals("func_75130_a")) {
                    return new MethodVisitor(Opcodes.ASM4, visitor) {
                        @Override
                        public void visitCode() {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, "tconstruct/tools/inventory/CraftingStationContainer", "craftMatrix", "Lnet/minecraft/inventory/InventoryCrafting;");
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, "tconstruct/tools/inventory/CraftingStationContainer", "craftResult", "Lnet/minecraft/inventory/IInventory;");
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, "tconstruct/tools/inventory/CraftingStationContainer", "worldObj", "Lnet/minecraft/world/World;");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, CraftingInfo.ASMPATH + "asm/helpers/TConstructHelper", "onContainerChanged", "(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/world/World;)V", false);
                            mv.visitInsn(Opcodes.RETURN);
                        }
                    };
                }
            }

            return visitor;
        }
    }
}
