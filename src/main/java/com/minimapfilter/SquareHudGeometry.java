package com.minimapfilter;

import java.awt.Rectangle;

/** Shared canvas-space geometry for painting, native buttons and the compass. */
final class SquareHudGeometry
{
    // Keep the native map size until larger draw/click masks have been live-tested.
    static final int MAP_SIZE = 152;
    static final int COLUMN_WIDTH = 52;
    static final int BORDER = 3;
    static final int GAP = 3;
    static final int WIDTH = BORDER * 2 + COLUMN_WIDTH + GAP + MAP_SIZE;
    static final int HEIGHT = BORDER * 2 + MAP_SIZE;
    static final int COMPASS_SIZE = 22;
    static final int FOOTER_HEIGHT = 34;
    static final int FOOTER_PADDING = 2;
    static final int FOOTER_SLOT_WIDTH = 60;

    private final int x;
    private final int y;
    private final int footerHeight;

    SquareHudGeometry(int x, int y)
    {
        this(x, y, FOOTER_HEIGHT);
    }

    SquareHudGeometry(int x, int y, int footerHeight)
    {
        this.x = x;
        this.y = y;
        this.footerHeight = Math.max(FOOTER_HEIGHT, footerHeight);
    }

    static int footerHeightForControls(int xpHeight, int worldMapHeight)
    {
        return Math.max(FOOTER_HEIGHT,
            Math.max(xpHeight, worldMapHeight) + FOOTER_PADDING * 2);
    }

    Rectangle panel()
    {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    Rectangle footer()
    {
        return new Rectangle(x, y + HEIGHT, WIDTH, footerHeight);
    }

    Rectangle footerReveal()
    {
        Rectangle footer = footer();
        // Two pixels of the upper frame reveal the bar from above too.
        return new Rectangle(footer.x, footer.y - 2, footer.width, footer.height + 2);
    }

    Rectangle zoneText()
    {
        Rectangle footer = footer();
        return new Rectangle(footer.x + FOOTER_SLOT_WIDTH, footer.y + BORDER,
            footer.width - 2 * FOOTER_SLOT_WIDTH, footer.height - 2 * BORDER);
    }

    Rectangle map()
    {
        return new Rectangle(x + BORDER + COLUMN_WIDTH + GAP, y + BORDER, MAP_SIZE, MAP_SIZE);
    }

    Rectangle block(int index)
    {
        if (index < 0 || index >= 4)
        {
            throw new IllegalArgumentException("Status block index must be 0..3");
        }
        int available = MAP_SIZE - GAP * 3;
        int start = index * available / 4;
        int end = (index + 1) * available / 4;
        return new Rectangle(x + BORDER, y + BORDER + start + index * GAP,
            COLUMN_WIDTH, end - start);
    }

    Rectangle wilderness(int width, int height)
    {
        Rectangle footer = footer();
        return new Rectangle(footer.x + footer.width - BORDER - width,
            footer.y + footer.height + GAP, width, height);
    }

    Rectangle multiCombat(Rectangle worldMapGraphic, int width, int height)
    {
        return multiCombat(worldMapGraphic, width, height, null);
    }

    Rectangle multiCombat(Rectangle worldMapGraphic, int width, int height, Rectangle wilderness)
    {
        Rectangle footer = footer();
        int centre = worldMapGraphic == null || worldMapGraphic.isEmpty()
            ? footer.x + footer.width - BORDER - 17
            : worldMapGraphic.x + worldMapGraphic.width / 2;
        int left = Math.max(footer.x, Math.min(footer.x + footer.width - width,
            centre - width / 2));
        int top = footer.y + footer.height + GAP;
        if (wilderness != null && !wilderness.isEmpty())
        {
            top = Math.max(top, wilderness.y + wilderness.height + GAP);
        }
        return new Rectangle(left, top, width, height);
    }

    Rectangle compass()
    {
        Rectangle map = map();
        return new Rectangle(map.x + map.width - COMPASS_SIZE - 4, map.y + 4,
            COMPASS_SIZE, COMPASS_SIZE);
    }
}
