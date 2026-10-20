package com.minimapfilter;

import java.util.Arrays;

/**
 * Native minimap Tutor fingerprints verified against in-game sprites.
 * Only sprites visually identified in the capture are included.
 * Missing tutors are deliberately not guessed from similarity scores.
 *
 * This code is independent of icon index and does not mutate the client.
 */
final class TutorIconFingerprints
{
    private TutorIconFingerprints()
    {
    }

    static boolean matches(int width, int height, int maxWidth, int maxHeight,
                           int offsetX, int offsetY, int[] pixels)
    {
        if (width != 15 || height != 15 || maxWidth != 15 || maxHeight != 15
            || offsetX != 0 || offsetY != 0 || pixels == null || pixels.length != 225)
        {
            return false;
        }
        switch (Arrays.hashCode(pixels))
        {
            case 0x5352446f: // Combat Tutor
            case 0x88e44242: // Smithing Tutor
            case 0x0390f690: // Clue Tutor
            case 0x2ec9bcf6: // Woodcutting Tutor
            case 0xd00b1c48: // Ironman Tutor
            case 0xc1f05d38: // Danger Tutor
            case 0x1d41516d: // Cooking Tutor
            case 0xa8ccad35: // Unverified gray marker
            case 0x8bec0d07: // Lumbridge Guide
            case 0x3c9c9ab1: // Security Tutor
            case 0x2f07162b: // Prayer Tutor
            case 0xef0655e9: // Deadman Tutor
                return true;
            default:
                return false;
        }
    }
}
