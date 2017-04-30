package ru.innopolis.app;

import com.android.dx.io.instructions.CodeOutput;
import com.android.dx.io.instructions.DecodedInstruction;
import com.android.dx.io.instructions.ShortArrayCodeInput;
import com.android.dx.io.instructions.ShortArrayCodeOutput;
import com.android.dx.util.ByteInput;
import com.android.dx.util.Leb128Utils;

import java.io.EOFException;
import java.util.Arrays;
import java.util.Map;

import static com.android.dx.io.Opcodes.*;

public class MethodBuffer implements ByteInput {
    private byte data[];
    private final int init_offset;
    private final int code_offset;
    private int position;
    short[] instructions;

    public MethodBuffer(byte[] data, int init_offset) {
        this.data = data;
        this.position = this.init_offset = init_offset;

        /*int registersSize = */readUnsignedShort();
        /*int insSize = */readUnsignedShort();
        /*int outsSize = */readUnsignedShort();
        /*int triesSize = */readUnsignedShort();
        /*int debugInfoOffset = */readInt();
        int instructionsSize = readInt();
        code_offset = position;
        instructions = readShortArray(instructionsSize);
    }

    void traverse(Map<Short, Short> toPatch) {
        ShortArrayCodeInput in = new ShortArrayCodeInput(instructions);
        ShortArrayCodeOutput out = new ShortArrayCodeOutput(instructions.length);
        while (in.hasMore()) {
            DecodedInstruction ins;
            try {
                ins = DecodedInstruction.decode(in);
            } catch (EOFException e) {
                e.printStackTrace();
                return;
            }
            processInstruction(out, ins, toPatch);
        }

        short[] patched = out.getArray();
        assert patched.length == instructions.length;

        for (int i = 0; i < patched.length; i++) {
            short p = patched[i];
            data[code_offset + 2 * i]     = (byte) (p & 0xff);
            data[code_offset + 2 * i + 1] = (byte) (p >> 8 & 0xff);
        }
    }

    private void processInstruction(ShortArrayCodeOutput out,
                                    DecodedInstruction ins,
                                    Map<Short, Short> toPatch) {
        switch (ins.getOpcode()) {
            case INVOKE_STATIC:
            case INVOKE_VIRTUAL:
            case INVOKE_DIRECT:
                short b = (short) ins.getIndex();
                if (toPatch.containsKey(b)) {
                    short idx = toPatch.get(b);
                    out.write(new byte[]{
                            // It's enough to use only static methods
                            INVOKE_STATIC,
//                                (byte) ins.getOpcodeUnit(),
                            // Don't get any arguments
                            (byte) ins.getENibble(),
//                                (byte) (ins.getRegisterCountUnit() << 4 | ins.getENibble()),
                            (byte) idx,
                            (byte) (idx >> 8),
                            (byte) (ins.getBNibble() << 4 | ins.getANibble()),
                            (byte) (ins.getDNibble() << 4 | ins.getCNibble())});
                    break;
                }
            default:
                ins.encode(out);
        }
    }

    private static void writeShortLE(CodeOutput out, short a, short b, short c, short d) {
        out.write((short) (
                (a & 0xf) << 0xc |
                (b & 0xf) << 0x8 |
                (c & 0xf) << 0x4 |
                (d & 0xf)));
    }

    private static void writeShortLE(CodeOutput out, short a, short b) {
        out.write((short) (a << 8 | b));
    }

    private static void writeShortLE(CodeOutput out, short s) {
        writeShortLE(out, (byte)s, (byte)(s >> 8));
    }

    // Code from com.android.dx.io.DexBuffer

    public int readInt() {
        int result = (data[position] & 0xff)
                | (data[position + 1] & 0xff) << 8
                | (data[position + 2] & 0xff) << 16
                | (data[position + 3] & 0xff) << 24;
        position += 4;
        return result;
    }

    public short readShort() {
        int result = (data[position] & 0xff)
                | (data[position + 1] & 0xff) << 8;
        position += 2;
        return (short) result;
    }

    public int readUnsignedShort() {
        return readShort() & 0xffff;
    }

    public byte readByte() {
        return (byte) (data[position++] & 0xff);
    }

    public byte[] readByteArray(int length) {
        byte[] result = Arrays.copyOfRange(data, position, position + length);
        position += length;
        return result;
    }

    public short[] readShortArray(int length) {
        short[] result = new short[length];
        for (int i = 0; i < length; i++) {
            result[i] = readShort();
        }
        return result;
    }

    public int readUleb128() {
        return Leb128Utils.readUnsignedLeb128(this);
    }

    public int readSleb128() {
        return Leb128Utils.readSignedLeb128(this);
    }

    private CatchHandler readCatchHandler() {
        int size = readSleb128();
        int handlersCount = Math.abs(size);
        int[] typeIndexes = new int[handlersCount];
        int[] addresses = new int[handlersCount];
        for (int i = 0; i < handlersCount; i++) {
            typeIndexes[i] = readUleb128();
            addresses[i] = readUleb128();
        }
        int catchAllAddress = size <= 0 ? readUleb128() : -1;
        return new CatchHandler(typeIndexes, addresses, catchAllAddress);
    }

    public static class Try {
        final int startAddress;
        final int instructionCount;
        final int handlerOffset;

        Try(int startAddress, int instructionCount, int handlerOffset) {
            this.startAddress = startAddress;
            this.instructionCount = instructionCount;
            this.handlerOffset = handlerOffset;
        }

        public int getStartAddress() {
            return startAddress;
        }

        public int getInstructionCount() {
            return instructionCount;
        }

        public int getHandlerOffset() {
            return handlerOffset;
        }
    }

    public static class CatchHandler {
        final int[] typeIndexes;
        final int[] addresses;
        final int catchAllAddress;

        public CatchHandler(int[] typeIndexes, int[] addresses, int catchAllAddress) {
            this.typeIndexes = typeIndexes;
            this.addresses = addresses;
            this.catchAllAddress = catchAllAddress;
        }

        public int[] getTypeIndexes() {
            return typeIndexes;
        }

        public int[] getAddresses() {
            return addresses;
        }

        public int getCatchAllAddress() {
            return catchAllAddress;
        }
    }
}
