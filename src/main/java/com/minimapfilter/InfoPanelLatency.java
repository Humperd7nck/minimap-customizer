package com.minimapfilter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/** Bounded TCP fallback, called only on the dedicated ping worker. */
final class InfoPanelLatency
{
    private InfoPanelLatency() { }

    static int tcp(String address, int port, int timeoutMillis)
    {
        if (address == null || address.trim().isEmpty() || port < 1 || port > 65535 || timeoutMillis < 1)
        {
            return -1;
        }
        // DNS may use the JVM resolver's timeout; it is intentionally off the client thread.
        InetSocketAddress endpoint = new InetSocketAddress(address, port);
        if (endpoint.isUnresolved()) { return -1; }
        try (Socket socket = new Socket())
        {
            long start = System.nanoTime();
            socket.connect(endpoint, timeoutMillis);
            long milliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, milliseconds));
        }
        catch (IOException | SecurityException unavailable)
        {
            return -1;
        }
    }
}
