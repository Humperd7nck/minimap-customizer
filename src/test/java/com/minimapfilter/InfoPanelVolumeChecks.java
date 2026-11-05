package com.minimapfilter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import javax.swing.SwingUtilities;

/** Independent behavior/bridge checks with synthetic script bytes and native audio doubles. */
public final class InfoPanelVolumeChecks
{
    private int checks;
    public static void main(String[] args) throws Exception
    {
        System.out.println("Info Panel volume checks passed: " + runAll());
    }
    public static int runAll() throws Exception
    {
        InfoPanelVolumeChecks test = new InfoPanelVolumeChecks();
        test.scripts(); test.defaultReturnRegression(); test.bridge(); test.diagnostics(); test.model();
        SwingUtilities.invokeAndWait(test::controls);
        return test.checks;
    }
    private void check(String name, boolean condition)
    {
        checks++; if (!condition) { throw new AssertionError(name); }
    }
    private void rejects(String name, Runnable call)
    {
        boolean failed = false;
        try { call.run(); } catch (RuntimeException expected) { failed = true; }
        check(name, failed);
    }

    static byte[] script(boolean apply, boolean modern, boolean extraReturn)
    {
        try
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(0);
            if (apply)
            {
                out.writeShort(0); out.writeInt(19);
                out.writeShort(1); out.writeInt(3796);
                out.writeShort(3212); out.writeByte(0);
            }
            else
            {
                out.writeShort(33); out.writeInt(0);
                out.writeShort(3214); out.writeByte(0);
            }
            out.writeShort(21); out.writeByte(0);
            if (extraReturn) { out.writeShort(21); out.writeByte(0); }
            out.writeInt((apply ? 4 : 3) + (extraReturn ? 1 : 0));
            out.writeShort(apply ? 0 : 1); out.writeShort(0);
            if (modern) { out.writeShort(0); }
            out.writeShort(apply ? 0 : 1); out.writeShort(0);
            if (modern) { out.writeShort(0); }
            out.writeByte(0); out.writeShort(1);
            return bytes.toByteArray();
        }
        catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
    }
    /** Synthetic fixture: normal int-returning procedure with an unreachable default return. */
    static byte[] readWithDefault(boolean modern, int value)
    {
        return rawScript(modern, 1, new int[]{33, 3214, 21, 0, 21}, new int[]{0, 0, 0, value, 0});
    }

    private static byte[] rawScript(boolean modern, int args, int[] ops, int[] operands)
    {
        try
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(0);
            for (int i = 0; i < ops.length; i++)
            {
                out.writeShort(ops[i]);
                if (ops[i] < 100 && ops[i] != 21) { out.writeInt(operands[i]); }
                else { out.writeByte(operands[i]); }
            }
            out.writeInt(ops.length);
            out.writeShort(args); out.writeShort(0);
            if (modern) { out.writeShort(0); }
            out.writeShort(args); out.writeShort(0);
            if (modern) { out.writeShort(0); }
            out.writeByte(0); out.writeShort(1);
            return bytes.toByteArray();
        }
        catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
    }

    private void defaultReturnRegression()
    {
        for (boolean modern : new boolean[]{false, true})
        {
            for (int value : new int[]{0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE})
            {
                byte[] bytes = readWithDefault(modern, value);
                MasterVolumeScripts.Check result = MasterVolumeScripts.inspectRead(bytes);
                check("unreachable integer default accepted " + modern + ":" + value, result.supported);
                check("diagnostic includes observed epilogue", result.detail.contains("21:0,0:" + value + ",21:0"));
                check("read script cannot become apply script", !MasterVolumeScripts.matchesApply(bytes));
                NativeAccess a = new NativeAccess(); a.readBytes = bytes;
                InfoPanelNativeVolume b = new InfoPanelNativeVolume(a);
                check("bridge uses accepted int-return script", b.read() == 65 && a.applies == 0);
                b.write(25);
                check("verified epilogue doesn't change mute semantics", a.actual == 25 && a.vars[3796] == 25);
                check("verified epilogue doesn't change independent channels", a.vars[168] == 23 && a.vars[169] == 77 && a.vars[872] == 39);
                // Every truncated form must remain unsupported, not just the footer cuts.
                for (int n = 0; n < bytes.length; n++)
                {
                    check("truncated default-return body rejected " + modern + ":" + value + ":" + n,
                        !MasterVolumeScripts.matchesRead(Arrays.copyOf(bytes, n)));
                }
            }
            check("reachable wrong local remains blocked", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3214, 21, 0, 21}, new int[]{1, 0, 0, 0, 0})));
            check("reachable writer never accepted for reader", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3212, 21, 0, 21}, new int[]{0, 0, 0, 0, 0})));
            check("return before required read is not accepted", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 21, 3214, 0, 21}, new int[]{0, 0, 0, 0, 0})));
            check("default result inserted BEFORE return is blocked", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3214, 0, 21}, new int[]{0, 0, 0, 0})));
            check("arbitrary trailing read is not accepted", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3214, 21, 1, 21}, new int[]{0, 0, 0, 3796, 0})));
            check("arbitrary trailing write is not accepted", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3214, 21, 3212, 21}, new int[]{0, 0, 0, 0, 0})));
            check("apply script cannot contain integer-return epilogue", !MasterVolumeScripts.matchesApply(rawScript(modern, 0,
                new int[]{0, 1, 3212, 21, 0, 21}, new int[]{19, 3796, 0, 0, 0, 0})));
            check("changed option in reachable apply prefix rejected", !MasterVolumeScripts.matchesApply(rawScript(modern, 0,
                new int[]{0, 1, 3212, 21}, new int[]{20, 3796, 0, 0})));
            check("changed varp in reachable apply prefix rejected", !MasterVolumeScripts.matchesApply(rawScript(modern, 0,
                new int[]{0, 1, 3212, 21}, new int[]{19, 3797, 0, 0})));
            check("wrong argument count rejected with epilogue", !MasterVolumeScripts.matchesRead(rawScript(modern, 2,
                new int[]{33, 3214, 21, 0, 21}, new int[]{0, 0, 0, 0, 0})));
            check("wrong default return operand rejected", !MasterVolumeScripts.matchesRead(rawScript(modern, 1,
                new int[]{33, 3214, 21, 0, 21}, new int[]{0, 0, 0, 0, 1})));
        }
        java.util.Random random = new java.util.Random(20261029L);
        for (int i = 0; i < 160; i++)
        {
            byte[] junk = new byte[random.nextInt(256)]; random.nextBytes(junk);
            check("bounded malformed diagnostic " + i, !MasterVolumeScripts.inspectRead(junk).supported
                && !MasterVolumeScripts.inspectApply(junk).supported);
        }
    }

    private void scripts()
    {
        for (boolean modern : new boolean[]{true, false})
        {
            for (boolean extra : new boolean[]{true, false})
            {
                byte[] set = script(true, modern, extra), get = script(false, modern, extra);
                check("apply contract " + modern + extra, MasterVolumeScripts.matchesApply(set));
                check("read contract " + modern + extra, MasterVolumeScripts.matchesRead(get));
                check("never substitute read for apply", !MasterVolumeScripts.matchesApply(get));
                check("never substitute apply for read", !MasterVolumeScripts.matchesRead(set));
            }
        }
        byte[] original = script(true, true, false);
        for (int length : new int[]{0, 1, 8, 15, original.length - 1})
        {
            check("truncated script rejected " + length,
                !MasterVolumeScripts.matchesApply(Arrays.copyOf(original, length)));
        }
        byte[] wrongOption = original.clone(); wrongOption[6] = 20;
        byte[] wrongVarp = original.clone(); wrongVarp[12] ^= 1;
        byte[] wrongOpcode = original.clone(); wrongOpcode[14] ^= 1;
        byte[] wrongFooter = original.clone(); wrongFooter[wrongFooter.length - 1] = 2;
        byte[] arguments = original.clone(); arguments[original.length - 8] = 1;
        for (byte[] wrong : new byte[][]{wrongOption, wrongVarp, wrongOpcode, wrongFooter, arguments})
        { check("mutated contract rejected", !MasterVolumeScripts.matchesApply(wrong)); }
        check("null contract rejected", !MasterVolumeScripts.matchesApply(null));
        check("large unexpected script rejected", !MasterVolumeScripts.matchesApply(new byte[4097]));
    }

    static final class NativeAccess implements InfoPanelNativeVolume.Access
    {
        int actual = 65, top, size, runs, applies, changes;
        int[] vars = new int[5000];
        boolean ready = true, compatible = true, cacheReady = true, readBroken, failApplyOnce;
        byte[] readBytes;
        int reports;
        String lastReport = "";
        NativeAccess() { vars[3796] = actual; vars[168] = 23; vars[169] = 77; vars[872] = 39; }
        public boolean ready() { return ready; }
        public byte[] script(int id)
        {
            return !cacheReady ? null : !compatible ? new byte[16]
                : id == 7456 && readBytes != null ? readBytes : InfoPanelVolumeChecks.script(id == 7109, true, false);
        }
        public int[] varps() { return vars; }
        public void run(int id, int... arguments)
        {
            runs++;
            if (id == 7456)
            {
                if (arguments.length != 1 || arguments[0] != 19) { throw new AssertionError("Unexpected read"); }
                size = readBroken ? 0 : 1; top = actual;
            }
            else if (id == 7109)
            {
                applies++;
                if (arguments.length != 0) { throw new AssertionError("Unexpected apply args"); }
                if (failApplyOnce) { failApplyOnce = false; return; }
                actual = vars[3796];
            }
            else { throw new AssertionError("Unexpected script: " + id); }
        }
        public void clearStack() { size = 0; }
        public int stackSize() { return size; }
        public int stackTop() { return top; }
        public void report(String detail) { reports++; lastReport = detail; }
        public void changed(int varp)
        {
            if (varp != 3796) { throw new AssertionError("Touched a non-master varp"); }
            changes++;
        }
    }

    private void bridge()
    {
        NativeAccess nativeAudio = new NativeAccess();
        InfoPanelNativeVolume bridge = new InfoPanelNativeVolume(nativeAudio);
        check("reads actual master", bridge.read() == 65);
        check("read does not apply volume", nativeAudio.applies == 0 && nativeAudio.changes == 0);
        for (int i = 0; i <= 100; i++)
        {
            bridge.write(i);
            check("native round-trip " + i, bridge.read() == i && nativeAudio.vars[3796] == i);
            check("channel balance preserved " + i, nativeAudio.vars[168] == 23 && nativeAudio.vars[169] == 77 && nativeAudio.vars[872] == 39);
        }
        int writes = nativeAudio.applies;
        bridge.write(100);
        check("same level does not rewrite native audio", nativeAudio.applies == writes);
        rejects("below zero refused", () -> bridge.write(-1));
        rejects("over 100 refused", () -> bridge.write(101));
        check("bad ranges do not write", nativeAudio.applies == writes);
        nativeAudio.actual = 37; nativeAudio.vars[3796] = 37;
        check("reads changes made in native settings", bridge.read() == 37);
        nativeAudio.failApplyOnce = true;
        rejects("failed apply is detected", () -> bridge.write(20));
        check("failure restores old native level and varp", nativeAudio.actual == 37 && nativeAudio.vars[3796] == 37);
        int runs = nativeAudio.runs;
        rejects("failed backend stays disabled", bridge::read);
        check("disabled bridge avoids repeatedly running scripts", nativeAudio.runs == runs);
        bridge.reset(); check("explicit retry recovers", bridge.read() == 37);
        nativeAudio.ready = false;
        rejects("logged-out bridge rejects write", () -> bridge.write(0));
        check("logout did not mute", nativeAudio.actual == 37);

        NativeAccess unsupported = new NativeAccess(); unsupported.compatible = false;
        InfoPanelNativeVolume unsupportedBridge = new InfoPanelNativeVolume(unsupported);
        rejects("unknown scripts rejected", () -> unsupportedBridge.write(0));
        check("guard failed before any execution or mutation", unsupported.runs == 0 && unsupported.vars[3796] == 65);
        NativeAccess pendingCache = new NativeAccess(); pendingCache.cacheReady = false;
        InfoPanelNativeVolume pendingBridge = new InfoPanelNativeVolume(pendingCache);
        rejects("missing cache is unavailable", pendingBridge::read);
        pendingCache.cacheReady = true;
        check("cache completion recovers without restarting", pendingBridge.read() == 65);
        NativeAccess badRead = new NativeAccess(); badRead.readBroken = true;
        InfoPanelNativeVolume badReadBridge = new InfoPanelNativeVolume(badRead);
        rejects("bad read stack rejected before write", () -> badReadBridge.write(0));
        check("bad read does not mute", badRead.actual == 65 && badRead.applies == 0);
        NativeAccess staleVarp = new NativeAccess(); staleVarp.vars[3796] = 10; staleVarp.failApplyOnce = true;
        rejects("stale varp failure detected", () -> new InfoPanelNativeVolume(staleVarp).write(20));
        check("rollback uses actual audio level, not stale varp", staleVarp.actual == 65 && staleVarp.vars[3796] == 65);
        NativeAccess badRange = new NativeAccess(); badRange.actual = 150;
        rejects("bad native range rejected", () -> new InfoPanelNativeVolume(badRange).read());
    }

    private void diagnostics()
    {
        NativeAccess unsupported = new NativeAccess(); unsupported.compatible = false;
        InfoPanelNativeVolume backend = new InfoPanelNativeVolume(unsupported);
        InfoPanelVolume model = new InfoPanelVolume();
        model.tick(backend, true, true, 0);
        String detail = model.snapshot().diagnostics;
        check("mismatch identifies both script ids", detail.contains("Apply script 7109") && detail.contains("Read script 7456"));
        check("mismatch contains cache fingerprint", detail.contains("sha256=") && detail.contains("bytes=16"));
        check("copy detail explains no audio change", detail.contains("No volume changed"));
        check("specific diagnostic reaches popup model", !model.snapshot().available && !detail.isEmpty());
        check("no execution to collect diagnostics", unsupported.runs == 0 && unsupported.actual == 65 && unsupported.vars[3796] == 65);
        check("diagnostic logged once", unsupported.reports == 1 && unsupported.lastReport.equals(detail));
        model.tick(backend, true, true, 300);
        check("latched failure doesn't repeat reports", unsupported.reports == 1);
        check("latched report is stable", detail.equals(model.snapshot().diagnostics));
        model.choose(0); model.toggle(); model.tick(backend, true, true, 600);
        check("blocked controls still do not mutate audio", unsupported.runs == 0 && unsupported.actual == 65);
        backend.reset(); unsupported.compatible = true; model.tick(backend, true, true, 900);
        check("explicit retry can recover", model.snapshot().available);
        check("successful retry clears old diagnostics", model.snapshot().diagnostics.isEmpty());
        model.suspend(); check("suspension clears stale report", model.snapshot().diagnostics.isEmpty());
        NativeAccess wrongRead = new NativeAccess();
        wrongRead.readBytes = rawScript(true, 1, new int[]{33, 3214, 21}, new int[]{1, 0, 0});
        InfoPanelNativeVolume readBackend = new InfoPanelNativeVolume(wrongRead);
        rejects("mismatched read rejected", readBackend::read);
        check("specific rejected reader named", readBackend.diagnostics().contains("blocked read script 7456"));
        check("wrong operand precisely reported", readBackend.diagnostics().contains("expected 33:0 but received 33:1"));
        check("reader failure still prevents ALL script execution", wrongRead.runs == 0);
        NativeAccess missing = new NativeAccess(); missing.cacheReady = false;
        InfoPanelNativeVolume waiting = new InfoPanelNativeVolume(missing);
        rejects("missing cache continues to wait", waiting::read);
        check("missing cache isn't logged as a confirmed mismatch", missing.reports == 0);
    }

    static final class Audio implements InfoPanelVolume.Backend
    {
        volatile int actual = 65;
        int writes, reads;
        boolean error;
        String detail = "";
        public String diagnostics() { return detail; }
        public int read() { reads++; if (error) { throw new IllegalStateException("Unavailable for test"); } return actual; }
        public void write(int value) { actual = value; writes++; }
    }
    private void model()
    {
        InfoPanelVolume v = new InfoPanelVolume(); Audio audio = new Audio();
        check("unavailable initial state", !v.snapshot().available && v.content().text.equals("Volume: —"));
        v.tick(audio, false, false, 0);
        check("inactive mode performs no reads or writes", audio.reads == 0 && audio.writes == 0);
        v.tick(audio, true, true, 0);
        check("opening/selecting observes only", v.snapshot().percent == 65 && audio.writes == 0);
        check("normal footer", v.content().text.equals("Volume: 65%"));
        check("tooltip", v.content().tooltip.equals("Control volume"));
        v.tick(audio, true, true, 100);
        check("read polling throttled", audio.reads == 1);
        v.choose(35); check("EDT action only queues intent", audio.writes == 0);
        v.tick(audio, true, true, 101);
        check("slider applied with read-back", audio.actual == 35 && v.snapshot().percent == 35);
        v.toggle(); v.tick(audio, true, true, 102);
        check("mute restores selected level", audio.actual == 0 && v.snapshot().selected == 35);
        check("muted footer", v.content().text.equals("Volume: Muted"));
        int writes = audio.writes;
        v.choose(20); v.tick(audio, true, true, 400);
        check("muted slider is silent", audio.actual == 0 && audio.writes == writes && v.snapshot().selected == 20);
        v.toggle(); v.tick(audio, true, true, 401);
        check("unmute uses staged level", audio.actual == 20);
        v.choose(0); v.tick(audio, true, true, 402);
        check("zero mutes and remembers previous positive level", audio.actual == 0 && v.snapshot().selected == 20);
        v.toggle(); v.tick(audio, true, true, 403);
        check("unmute after zero returns to previous level", audio.actual == 20);
        v.choose(60); v.cancelPending(); v.tick(audio, true, true, 700);
        check("close cancels queued command", audio.actual == 20);
        v.choose(70); audio.actual = 30; v.tick(audio, true, true, 701);
        check("stale UI command doesn't overwrite native change", audio.actual == 30 && !v.snapshot().pending);
        check("stale status explains change", v.snapshot().status.contains("changed in-game"));
        v.choose(60); v.tick(audio, true, false, 1000);
        check("hidden popup blocks queued writes", audio.actual == 30);
        writes = audio.writes;
        v.suspend(); v.tick(audio, false, false, 1001);
        check("disable never changes volume", audio.writes == writes && audio.actual == 30);
        v.tick(audio, true, true, 1002);
        audio.error = true; v.tick(audio, true, true, 1300);
        check("backend error is isolated", !v.snapshot().available && v.snapshot().status.equals("Unavailable for test"));
        v.toggle(); check("unavailable control cannot enqueue writes", !v.snapshot().pending);
        audio.error = false; v.tick(audio, true, true, 1600);
        check("model can recover availability", v.snapshot().available);
        v.reset(); audio.actual = 0; v.tick(audio, true, true, 2000);
        check("starting muted invents no restore level", !v.snapshot().canUnmute() && v.snapshot().selected == 0);
        v.toggle(); v.tick(audio, true, true, 2300);
        check("unknown previous level never blasts to 100", audio.actual == 0);
        v.choose(15); v.toggle(); v.tick(audio, true, true, 2301);
        check("explicit level permits unmute", audio.actual == 15);
        v.reset(); check("profile reset clears old restore level", v.snapshot().selected == 0);
        rejects("model rejects out of range", () -> v.choose(101));
    }
    private void controls()
    {
        InfoPanelPopup popup = new InfoPanelPopup(new InfoPanelTools(), () -> {});
        rejects("Volume no longer has a popup", () -> popup.createView(InfoPanelText.VOLUME));
    }
}
