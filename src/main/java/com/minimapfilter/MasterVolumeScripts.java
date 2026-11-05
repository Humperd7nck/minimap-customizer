package com.minimapfilter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Read-only validation of native master-audio scripts. Never edits or injects scripts.
 * IDs/semantics: Joshua-F/osrs-dumps commit 43923717f65af1e5fbbe862e98a01d6e6f9b150a.
 * All reachable instructions must match. Only a narrow, unreachable return epilogue
 * is allowed after the first unconditional return; it cannot change audio semantics.
 */
final class MasterVolumeScripts
{
    static final int MASTER_VARP = 3796;
    static final int MASTER_OPTION = 19;
    static final int APPLY = 7109;
    static final int READ = 7456;
    private static final int RETURN = 21;
    private static final int MAX_BYTES = 4096;
    private static final int MAX_INSTRUCTIONS = 64;
    private MasterVolumeScripts() { }

    static final class Check
    {
        final boolean supported;
        final String detail;
        Check(boolean supported, String detail) { this.supported = supported; this.detail = detail; }
    }

    static boolean matchesApply(byte[] data) { return inspectApply(data).supported; }
    static boolean matchesRead(byte[] data) { return inspectRead(data).supported; }

    static Check inspectApply(byte[] data)
    {
        return inspect(data, 0, new int[]{0, 1, 3212, RETURN}, new int[]{MASTER_OPTION, MASTER_VARP, 0, 0}, false);
    }

    static Check inspectRead(byte[] data)
    {
        return inspect(data, 1, new int[]{33, 3214, RETURN}, new int[]{0, 0, 0}, true);
    }

    private static Check inspect(byte[] data, int args, int[] opcodes, int[] operands, boolean returnsInt)
    {
        if (data == null) { return new Check(false, "cache bytes not loaded"); }
        String identity = "bytes=" + data.length + "; sha256=" + digest(data);
        if (data.length < 16 || data.length > MAX_BYTES)
        {
            return new Check(false, identity + "; script length outside supported range");
        }
        StringBuilder rejected = new StringBuilder(identity);
        for (int footer : new int[]{16, 12})
        {
            try
            {
                Decoded d = decode(data, footer);
                String mismatch = mismatch(d, args, opcodes, operands, returnsInt);
                String detail = identity + "; " + d.summary();
                if (mismatch == null) { return new Check(true, "supported; " + detail); }
                rejected.append("\n").append(d.summary()).append("; rejected: ").append(mismatch);
            }
            catch (IllegalArgumentException invalid)
            {
                rejected.append("\nfooter=").append(footer).append(" rejected: ").append(invalid.getMessage());
            }
        }
        return new Check(false, rejected.toString());
    }

    private static String mismatch(Decoded d, int args, int[] opcodes, int[] operands, boolean returnsInt)
    {
        if (d.localInts != args || d.localObjects != 0 || d.localLongs != 0
            || d.intArgs != args || d.objectArgs != 0 || d.longArgs != 0)
        {
            return "local/argument counts do not match native audio contract";
        }
        if (d.opcodes.length < opcodes.length) { return "missing reachable instructions"; }
        for (int i = 0; i < opcodes.length; i++)
        {
            if (d.opcodes[i] != opcodes[i] || d.operands[i] != operands[i])
            {
                return "instruction " + i + " expected " + opcodes[i] + ":" + operands[i]
                    + " but received " + d.opcodes[i] + ":" + d.operands[i];
            }
        }
        int tail = d.opcodes.length - opcodes.length;
        if (tail == 0) { return null; }
        // The last matched opcode is an unconditional RETURN. No jumps or calls
        // exist in the matched prefix, so no accepted epilogue can be reached.
        if (tail == 1 && d.opcodes[opcodes.length] == RETURN && d.operands[opcodes.length] == 0)
        {
            return null;
        }
        // Some compilers append a default integer result before their final return.
        // Accept only ICONST <int>, RETURN, never arbitrary trailing instructions.
        if (returnsInt && tail == 2 && d.opcodes[opcodes.length] == 0
            && d.opcodes[opcodes.length + 1] == RETURN && d.operands[opcodes.length + 1] == 0)
        {
            return null;
        }
        return "unsupported instructions after unconditional return";
    }

    private static Decoded decode(byte[] data, int footer)
    {
        try
        {
            ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            int switchLength = Short.toUnsignedInt(in.getShort(data.length - 2));
            if (switchLength != 1) { throw new IllegalArgumentException("expected an empty switch table"); }
            int bodyEnd = data.length - 2 - switchLength - footer;
            if (bodyEnd < 1) { throw new IllegalArgumentException("metadata overlaps script body"); }
            in.position(bodyEnd);
            int instructions = in.getInt();
            if (instructions < 1 || instructions > MAX_INSTRUCTIONS)
            {
                throw new IllegalArgumentException("instruction count=" + instructions + " is outside supported range");
            }
            Decoded d = new Decoded(footer, instructions);
            d.localInts = Short.toUnsignedInt(in.getShort());
            d.localObjects = Short.toUnsignedInt(in.getShort());
            d.localLongs = footer == 16 ? Short.toUnsignedInt(in.getShort()) : 0;
            d.intArgs = Short.toUnsignedInt(in.getShort());
            d.objectArgs = Short.toUnsignedInt(in.getShort());
            d.longArgs = footer == 16 ? Short.toUnsignedInt(in.getShort()) : 0;
            if (in.get() != 0 || in.position() != data.length - 2)
            {
                throw new IllegalArgumentException("invalid empty-switch metadata");
            }
            in.position(0);
            boolean nameEnded = false;
            while (in.position() < bodyEnd)
            {
                if (in.get() == 0) { nameEnded = true; break; }
            }
            if (!nameEnded) { throw new IllegalArgumentException("missing script-name terminator"); }
            for (int i = 0; i < instructions; i++)
            {
                if (in.position() + 2 > bodyEnd) { throw new IllegalArgumentException("truncated opcode at " + i); }
                int opcode = Short.toUnsignedInt(in.getShort());
                // None of the supported routines contains strings, longs or other
                // variable-width instructions. Reject them, rather than misdecode.
                if (opcode != 0 && opcode != 1 && opcode != 33 && opcode != RETURN
                    && opcode != 3212 && opcode != 3214)
                {
                    throw new IllegalArgumentException("unsupported opcode=" + opcode + " at instruction=" + i);
                }
                int width = opcode < 100 && opcode != RETURN ? 4 : 1;
                if (in.position() + width > bodyEnd) { throw new IllegalArgumentException("truncated operand at " + i); }
                d.opcodes[i] = opcode;
                d.operands[i] = width == 4 ? in.getInt() : Byte.toUnsignedInt(in.get());
            }
            if (in.position() != bodyEnd) { throw new IllegalArgumentException("extra bytes in script body"); }
            return d;
        }
        catch (java.nio.BufferUnderflowException | IndexOutOfBoundsException invalid)
        {
            throw new IllegalArgumentException("truncated script metadata", invalid);
        }
    }

    private static String digest(byte[] data)
    {
        // Oversized inputs are rejected before hashing to keep the check bounded.
        if (data.length > MAX_BYTES) { return "not calculated (oversized)"; }
        try
        {
            StringBuilder hex = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data))
            {
                hex.append(Character.forDigit((b >>> 4) & 15, 16));
                hex.append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException unavailable) { return "unavailable"; }
    }

    private static final class Decoded
    {
        final int footer;
        final int[] opcodes, operands;
        int localInts, localObjects, localLongs, intArgs, objectArgs, longArgs;
        Decoded(int footer, int count)
        {
            this.footer = footer; opcodes = new int[count]; operands = new int[count];
        }
        String summary()
        {
            StringBuilder text = new StringBuilder("footer=").append(footer)
                .append("; locals=").append(localInts).append('/').append(localObjects).append('/').append(localLongs)
                .append("; args=").append(intArgs).append('/').append(objectArgs).append('/').append(longArgs)
                .append("; ops=[");
            for (int i = 0; i < opcodes.length; i++)
            {
                if (i != 0) { text.append(','); }
                text.append(opcodes[i]).append(':').append(operands[i]);
            }
            return text.append(']').toString();
        }
    }
}
