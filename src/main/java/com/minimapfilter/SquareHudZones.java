package com.minimapfilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;

/** Bundled, offline region names. This is not an official named-zone API. */
final class SquareHudZones
{
    private static final Map<Integer, String> NAMES = load();
    private SquareHudZones() { }

    static String lookup(int region)
    {
        return NAMES.getOrDefault(region, "");
    }

    static int regionCount() { return NAMES.size(); }

    private static Map<Integer, String> load()
    {
        try (InputStream stream = SquareHudZones.class.getResourceAsStream("zones.tsv"))
        {
            if (stream == null) { throw new IOException("Missing zones.tsv"); }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        catch (IOException | IllegalArgumentException ex)
        {
            LoggerFactory.getLogger(SquareHudZones.class).warn("Location names could not be loaded", ex);
            return Collections.emptyMap();
        }
    }

    static Map<Integer, String> parse(BufferedReader reader) throws IOException
    {
        Map<Integer, String> names = new HashMap<>();
        Set<Integer> ambiguous = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.isBlank() || line.startsWith("#")) { continue; }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 2 || fields[0].isBlank()) { throw new IOException("Invalid zone row"); }
            for (String pair : fields[1].trim().split(" +"))
            {
                String[] xy = pair.split(",", -1);
                if (xy.length != 2) { throw new IOException("Invalid zone coordinate"); }
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);
                if (x < 0 || x > 255 || y < 0 || y > 255) { throw new IOException("Invalid zone region"); }
                int region = (x << 8) | y;
                String previous = names.putIfAbsent(region, fields[0]);
                if (previous != null && !previous.equals(fields[0])) { ambiguous.add(region); }
            }
        }
        // Conflicting source cells remain unnamed instead of depending on file order.
        ambiguous.forEach(names::remove);
        return Collections.unmodifiableMap(names);
    }
}
