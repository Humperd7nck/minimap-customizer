package com.minimapfilter;

import java.awt.image.BufferedImage;

/** Small upright skulls. These are minimap symbols, not players' overhead skulls. */
final class WildernessSkullIcons
{
    private static final String[] PIXELS = {
        "..#######..",
        ".#HHHHHHH#.",
        "#HHHHHHHHH#",
        "#HHHHHHHHH#",
        "#HH##H##HH#",
        "#SS##S##SS#",
        "#SSSS#SSSS#",
        ".#SS###SS#.",
        "..#HHHHH#..",
        "..#H#H#H#..",
        "...#####..."
    };
    static final BufferedImage WHITE = create(0xfff4f4f4, 0xffb8b8b8);
    static final BufferedImage RED = create(0xffef4038, 0xffae2420);

    private WildernessSkullIcons() { }

    private static BufferedImage create(int main, int shade)
    {
        BufferedImage image = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < PIXELS.length; y++)
        {
            for (int x = 0; x < PIXELS[y].length(); x++)
            {
                char pixel = PIXELS[y].charAt(x);
                int argb = pixel == '#' ? 0xff151515 : pixel == 'H' ? main : pixel == 'S' ? shade : 0;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}
