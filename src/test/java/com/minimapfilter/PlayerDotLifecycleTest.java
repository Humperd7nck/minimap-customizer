package com.minimapfilter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.minimapfilter.MapIconClassificationTest.*;

/** Ordinary player dots must follow user settings without inspecting other players. */
public class PlayerDotLifecycleTest
{
    @Test
    public void visibilityColorsAndRestorationNeedNoPlayerClassification() throws Exception
    {
        MinimapFilterPlugin plugin = new MinimapFilterPlugin();
        SpritePixels[] dots = new SpritePixels[7];
        for (int i = 0; i < dots.length; i++)
        {
            dots[i] = dot(new int[]{0, 1, 0xffa0b0c0 + i, 0}, 2, 2);
        }
        SpritePixels[] originals = dots.clone();
        boolean[] visible = {true};
        Color[] color = {NativeDotColors.PLAYER};
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
            new Class<?>[]{Client.class}, (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getMapDots": return dots;
                    case "createSpritePixels": return dot((int[]) a[0], (int) a[1], (int) a[2]);
                    default: throw new AssertionError("Unexpected client query: " + m.getName());
                }
            });
        set(plugin, "client", client);
        set(plugin, "markerAssets", new MinimapMarkerAssets());
        set(plugin, "config", new MinimapFilterConfig()
        {
            @Override public boolean showPlayers() { return visible[0]; }
            @Override public Color otherPlayerDotColor() { return color[0]; }
        });
        invoke(plugin, "createTransparentSprites");
        invoke(plugin, "applyDotFilters");
        assertArrayEquals(originals, dots);

        color[0] = Color.CYAN;
        invoke(plugin, "applyDotFilters");
        SpritePixels tinted = dots[2];
        assertNotSame(originals[2], tinted);
        assertEquals(Color.CYAN.getRGB(), tinted.getPixels()[2]);
        assertArrayEquals(new int[]{0, 1, 0xffa0b0c2, 0}, originals[2].getPixels());
        for (int i = 0; i < 100; i++) { invoke(plugin, "applyDotFilters"); }
        assertSame("unchanged color reuses sprite", tinted, dots[2]);

        visible[0] = false;
        invoke(plugin, "applyDotFilters");
        assertSame(get(plugin, "transparentDot"), dots[2]);
        for (int i = 3; i < dots.length; i++)
        {
            assertSame("relationship dot remains independent", originals[i], dots[i]);
        }
        visible[0] = true;
        invoke(plugin, "applyDotFilters");
        assertSame(tinted, dots[2]);
        invoke(plugin, "restoreDots");
        assertArrayEquals(originals, dots);

        invoke(plugin, "applyDotFilters");
        SpritePixels external = dot(new int[]{0, 1, 0xff123456, 0}, 2, 2);
        dots[2] = external;
        invoke(plugin, "restoreDots");
        assertSame("shutdown preserves another plugin's replacement", external, dots[2]);
        invoke(plugin, "applyDotFilters");
        color[0] = NativeDotColors.PLAYER;
        invoke(plugin, "applyDotFilters");
        assertSame("reset follows latest native sprite", external, dots[2]);
    }

    private static SpritePixels dot(int[] source, int width, int height)
    {
        int[] pixels = Arrays.copyOf(source, source.length);
        return (SpritePixels) Proxy.newProxyInstance(SpritePixels.class.getClassLoader(),
            new Class<?>[]{SpritePixels.class}, (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getPixels": return pixels;
                    case "getWidth": case "getMaxWidth": return width;
                    case "getHeight": case "getMaxHeight": return height;
                    case "toBufferedImage":
                        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        image.setRGB(0, 0, width, height, pixels, 0, width);
                        return image;
                    default: return empty(m.getReturnType());
                }
            });
    }
}
