// Patch the alphaTab v1.8.4 count-in completion threshold in AlphaSynthBase.
// The upstream TypeScript source, which generates the Kotlin class, is MPL-2.0:
// https://github.com/CoderLine/alphaTab/blob/v1.8.4/packages/alphatab/src/synth/AlphaSynth.ts
// Only the count-in branch receives an 80 ms early transition. Other playback
// completion paths retain the original end tick and zero-sample thresholds.

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class PatchAlphaSynthCountIn {
    private static final String OWNER = "alphaTab/synth/AlphaSynthBase";
    private static final String SEQUENCER = "alphaTab/synth/MidiFileSequencer";
    private static final double EARLY_MILLIS = 80.0;
    private static final double EARLY_SAMPLES = 44_100 * EARLY_MILLIS / 1000 * 2;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected AlphaSynthBase.class path");
        Path path = Path.of(args[0]);
        ClassReader reader = new ClassReader(Files.readAllBytes(path));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] sampleReplacements = {0};
        int[] tickReplacements = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("checkForFinish") || !descriptor.equals("()V")) return downstream;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    private boolean afterNotPlayedSamples;
                    private boolean afterTickPosition;

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        super.visitFieldInsn(opcode, owner, field, desc);
                        afterNotPlayedSamples = opcode == Opcodes.GETFIELD && owner.equals(OWNER)
                            && field.equals("_notPlayedSamples") && desc.equals("D");
                        afterTickPosition = opcode == Opcodes.GETFIELD && owner.equals(OWNER)
                            && field.equals("_tickPosition") && desc.equals("D");
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex) {
                        super.visitVarInsn(opcode, varIndex);
                        if (afterTickPosition && opcode == Opcodes.DLOAD && varIndex == 3) {
                            // endTick - (isCountIn ? endTick - tickAt(endTime - 80 ms) : 0)
                            super.visitInsn(Opcodes.DUP2);
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "sequencer", "L" + SEQUENCER + ";");
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "sequencer", "L" + SEQUENCER + ";");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SEQUENCER,
                                "getCurrentEndTime", "()D", false);
                            super.visitLdcInsn(EARLY_MILLIS);
                            super.visitInsn(Opcodes.DSUB);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SEQUENCER,
                                "currentTimePositionToTickPosition", "(D)D", false);
                            super.visitInsn(Opcodes.DSUB);
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "sequencer", "L" + SEQUENCER + ";");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SEQUENCER,
                                "isPlayingCountIn", "()Z", false);
                            super.visitInsn(Opcodes.I2D);
                            super.visitInsn(Opcodes.DMUL);
                            super.visitInsn(Opcodes.DSUB);
                            tickReplacements[0]++;
                        }
                        afterTickPosition = false;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (afterNotPlayedSamples && opcode == Opcodes.DCONST_0) {
                            // isPlayingCountIn() ? 7056.0 floating-point samples : 0.0
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "sequencer", "L" + SEQUENCER + ";");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SEQUENCER,
                                "isPlayingCountIn", "()Z", false);
                            super.visitInsn(Opcodes.I2D);
                            super.visitLdcInsn(EARLY_SAMPLES);
                            super.visitInsn(Opcodes.DMUL);
                            sampleReplacements[0]++;
                        } else {
                            super.visitInsn(opcode);
                        }
                        afterNotPlayedSamples = false;
                    }
                };
            }
        }, 0);
        if (sampleReplacements[0] != 1 || tickReplacements[0] != 1) {
            throw new IllegalStateException("Expected one count-in threshold pair, found "
                + sampleReplacements[0] + "/" + tickReplacements[0]);
        }
        Files.write(path, writer.toByteArray());
    }
}
