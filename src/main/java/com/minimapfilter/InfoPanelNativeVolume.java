package com.minimapfilter;

/** Native bridge with a deliberately small testable access boundary.
 * All methods must be called on the client thread, outside a running client script.
 * It changes ONLY the native master varp/device option, never any channel volumes.
 */
final class InfoPanelNativeVolume implements InfoPanelVolume.Backend
{
    interface Access
    {
        boolean ready();
        byte[] script(int id);
        int[] varps();
        void run(int id, int... arguments);
        void clearStack();
        int stackSize();
        int stackTop();
        void changed(int varp);
        default void report(String detail) { }
    }

    private final Access access;
    private boolean verified;
    private String failure;
    private String diagnostics = "";

    InfoPanelNativeVolume(Access access) { this.access = access; }
    void reset() { verified = false; failure = null; diagnostics = ""; }
    @Override public String diagnostics() { return diagnostics; }

    private void check()
    {
        if (!access.ready()) { throw new IllegalStateException("Log in to use game volume."); }
        if (failure != null) { throw new IllegalStateException(failure); }
        if (!verified)
        {
            byte[] apply = access.script(MasterVolumeScripts.APPLY);
            byte[] read = access.script(MasterVolumeScripts.READ);
            // Missing cache data can arrive later; do not permanently fail just for that.
            if (apply == null || read == null) { throw new IllegalStateException("Waiting for native audio scripts."); }
            MasterVolumeScripts.Check applyCheck = MasterVolumeScripts.inspectApply(apply);
            MasterVolumeScripts.Check readCheck = MasterVolumeScripts.inspectRead(read);
            diagnostics = "Minimap Customizer 3.0.0-dev.6 — Volume compatibility\n"
                + "Native master option=" + MasterVolumeScripts.MASTER_OPTION
                + "; varp=" + MasterVolumeScripts.MASTER_VARP + "\n"
                + "Apply script " + MasterVolumeScripts.APPLY + ": " + applyCheck.detail + "\n"
                + "Read script " + MasterVolumeScripts.READ + ": " + readCheck.detail + "\n"
                + "This report contains script metadata only; no account or session data.";
            if (!applyCheck.supported || !readCheck.supported)
            {
                String blocked = !applyCheck.supported && !readCheck.supported ? "apply/read scripts"
                    : !applyCheck.supported ? "apply script 7109" : "read script 7456";
                fail("Native audio check blocked " + blocked + ". No volume changed. Use Copy diagnostics.");
            }
            int[] varps = access.varps();
            if (varps == null || varps.length <= MasterVolumeScripts.MASTER_VARP)
            {
                fail("Native master volume setting is unavailable.");
            }
            verified = true;
        }
    }

    @Override public int read()
    {
        check();
        try { return readNative(); }
        catch (RuntimeException | LinkageError ex)
        {
            fail("Native master-volume read failed. Use the game's audio settings.");
            return -1;
        }
    }

    private int readNative()
    {
        access.clearStack(); // A failed/missing script must not masquerade as a stale result.
        access.run(MasterVolumeScripts.READ, MasterVolumeScripts.MASTER_OPTION);
        if (access.stackSize() != 1) { throw new IllegalStateException("No native audio read-back."); }
        int value = access.stackTop();
        if (value < 0 || value > 100) { throw new IllegalStateException("Unexpected native volume range."); }
        return value;
    }

    @Override public void write(int percent)
    {
        if (percent < 0 || percent > 100) { throw new IllegalArgumentException("Volume must be 0-100."); }
        check();
        int before = read();
        if (before == percent) { return; }
        int[] varps = access.varps();
        try
        {
            varps[MasterVolumeScripts.MASTER_VARP] = percent;
            access.run(MasterVolumeScripts.APPLY);
            if (readNative() != percent) { throw new IllegalStateException("Native volume did not change."); }
            access.changed(MasterVolumeScripts.MASTER_VARP);
        }
        catch (RuntimeException | LinkageError ex)
        {
            // Best-effort native rollback; never leave a misleading master setting behind.
            boolean restored = false;
            try
            {
                varps[MasterVolumeScripts.MASTER_VARP] = before;
                access.run(MasterVolumeScripts.APPLY);
                restored = readNative() == before;
            }
            catch (RuntimeException | LinkageError rollbackFailed) { /* report below, no retries */ }
            finally
            {
                varps[MasterVolumeScripts.MASTER_VARP] = before;
                try { access.changed(MasterVolumeScripts.MASTER_VARP); }
                catch (RuntimeException | LinkageError ignored) { /* still report unavailability */ }
            }
            fail(restored ? "Native volume change failed; the previous level was restored."
                : "Native volume change failed. Check the game's audio settings before continuing.");
        }
    }

    private void fail(String message)
    {
        failure = message;
        // Emitted once per failed lifecycle, not every frame. Do not include
        // exception messages or account data; the fingerprint is sufficient.
        diagnostics += (diagnostics.isEmpty() ? "" : "\n") + "Status: " + message;
        try { access.report(diagnostics); }
        catch (RuntimeException | LinkageError ignored) { /* logging cannot enable unsafe audio */ }
        throw new IllegalStateException(message);
    }
}
