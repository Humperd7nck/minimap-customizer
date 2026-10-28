package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Deterministic range, drawing and settings tests; no live game connection. */
public final class WildernessSkullChecks
{
    private int cases;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Wilderness skull checks passed: " + runAll());
    }

    public static int runAll() throws Exception
    {
        WildernessSkullChecks test = new WildernessSkullChecks();
        test.range();
        test.worlds();
        test.icons();
        test.overlay();
        SwingUtilities.invokeAndWait(test::settings);
        return test.cases;
    }

    private void check(String name, boolean ok)
    {
        cases++;
        if (!ok) { throw new AssertionError(name); }
    }

    private void range()
    {
        WildernessCombatRange r = WildernessCombatRange.from(100, "Level: 20");
        check("range 80-120", r != null && r.low == 80 && r.high == 120);
        for (int level : new int[]{80, 81, 100, 119, 120})
        { check("inclusive red " + level, r.marker(level) == WildernessCombatRange.Marker.RED); }
        for (int level : new int[]{3, 79, 121, 126})
        { check("out of range white " + level, r.marker(level) == WildernessCombatRange.Marker.WHITE); }
        for (int level : new int[]{-1, 0, 1, 2, 127, Integer.MAX_VALUE})
        { check("unknown dot " + level, r.marker(level) == WildernessCombatRange.Marker.DOT); }
        for (String text : new String[]{"Level: 20<br>80-120", "<col=ffff00>Level: 20</col>",
            " <col=ffff00>Level: 20<br/>80-120</col> ", "Level: 20\n80-120", "level: 20"})
        {
            r = WildernessCombatRange.from(100, text);
            check("formatted native level " + text, r != null && r.low == 80 && r.high == 120);
        }
        for (String text : new String[]{null, "", "80-120", "Safe", "Level: 0", "Level: -1", "Level: 101",
            "Level: 99999", "Level: 20.5", "Level: 20fake", "Other Level: 20"})
        { check("invalid level text " + text, WildernessCombatRange.from(100, text) == null); }
        for (int combat : new int[]{0, 2, 127, Integer.MAX_VALUE})
        { check("bad local level " + combat, WildernessCombatRange.from(combat, "Level: 20") == null); }
        r = WildernessCombatRange.from(3, "Level: 60");
        check("lower clamp", r.low == 3 && r.high == 63);
        r = WildernessCombatRange.from(126, "Level: 60");
        check("upper clamp", r.low == 66 && r.high == 126);
        r = WildernessCombatRange.from(100, "Level: 1");
        check("edge level1", r.marker(98) == WildernessCombatRange.Marker.WHITE
            && r.marker(99) == WildernessCombatRange.Marker.RED
            && r.marker(101) == WildernessCombatRange.Marker.RED
            && r.marker(102) == WildernessCombatRange.Marker.WHITE);
    }

    private void worlds()
    {
        check("standard F2P", WildernessCombatRange.standardWorld(EnumSet.noneOf(WorldType.class)));
        check("standard members high risk", WildernessCombatRange.standardWorld(EnumSet.of(WorldType.MEMBERS, WorldType.HIGH_RISK)));
        check("null world unknown", !WildernessCombatRange.standardWorld(null));
        for (WorldType type : WorldType.values())
        {
            boolean ordinary = type == WorldType.MEMBERS || type == WorldType.SKILL_TOTAL
                || type == WorldType.HIGH_RISK || type == WorldType.FRESH_START_WORLD || type == WorldType.LEGACY_ONLY;
            check("explicit world flag " + type, WildernessCombatRange.standardWorld(EnumSet.of(type)) == ordinary);
        }
        check("mixed special flags rejected", !WildernessCombatRange.standardWorld(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
    }

    private void icons()
    {
        BufferedImage white = WildernessSkullIcons.WHITE;
        BufferedImage red = WildernessSkullIcons.RED;
        check("compact icons", white.getWidth() == 11 && white.getHeight() == 11);
        boolean geometry = true, outline = true;
        int whiteFill = 0, redFill = 0;
        for (int y = 0; y < 11; y++)
        {
            for (int x = 0; x < 11; x++)
            {
                int a = white.getRGB(x, y), b = red.getRGB(x, y);
                geometry &= (a >>> 24) == (b >>> 24);
                if (a == 0xff151515) { outline &= a == b; }
                if (a == 0xfff4f4f4) { whiteFill++; }
                if (b == 0xffef4038) { redFill++; }
            }
        }
        check("same silhouette", geometry);
        check("same black outline and sockets", outline);
        check("equal colored areas", whiteFill > 20 && whiteFill == redFill);
        check("transparent outer corners", white.getRGB(0, 0) == 0 && red.getRGB(10, 10) == 0);
    }

    private void overlay()
    {
        Fixture f = new Fixture();
        f.overlay.refresh();
        check("setting defaults off", !new MinimapFilterConfig() {}.wildernessPlayerSkulls());
        check("off leaves dots", !f.overlay.isReplacingOtherPlayers());
        f.config.enabled = true;
        f.overlay.refresh();
        check("enabled in Wilderness", f.overlay.isReplacingOtherPlayers());
        f.others.add(f.peer);
        check("in bracket red rendered", f.paint().getRGB(50, 47) == 0xffef4038);
        f.peer.level = 79;
        check("out bracket white rendered", f.paint().getRGB(50, 47) == 0xfff4f4f4);
        f.peer.level = 0;
        check("unknown level keeps native dot", f.paint().getRGB(50, 50) == 0xff00ffff);
        f.peer.level = 120;
        check("inclusive endpoint rendered red", f.paint().getRGB(50, 47) == 0xffef4038);
        f.levelText = "Level: 19<br>81-119";
        f.overlay.refresh();
        check("depth change updates immediately", f.paint().getRGB(50, 47) == 0xfff4f4f4);
        f.local.level = 101;
        f.overlay.refresh();
        check("local level change updates", f.paint().getRGB(50, 47) == 0xffef4038);
        f.local.level = 100;
        f.peer.level = 100;
        f.levelText = "Level: 20";
        f.overlay.refresh();
        for (int relation = 0; relation < 4; relation++)
        {
            f.peer.friend = relation == 0;
            f.peer.fc = relation == 1;
            f.peer.clan = relation == 2;
            f.local.team = relation == 3 ? 7 : 0;
            f.peer.team = relation == 3 ? 7 : 0;
            check("relationship not replaced " + relation, !WildernessPlayerOverlay.isOtherPlayer(f.peer.player, f.local.player));
            check("relationship not drawn " + relation, opaque(f.paint()) == 0);
        }
        f.peer.friend = false; f.peer.fc = false; f.peer.clan = false;
        f.peer.team = 8;
        check("different team is other player", WildernessPlayerOverlay.isOtherPlayer(f.peer.player, f.local.player));
        check("different team draws", opaque(f.paint()) > 0);
        f.local.team = 0; f.peer.team = 0;
        check("no team is not shared team", WildernessPlayerOverlay.isOtherPlayer(f.peer.player, f.local.player));
        check("self excluded", !WildernessPlayerOverlay.isOtherPlayer(f.local.player, f.local.player));
        check("null excluded", !WildernessPlayerOverlay.isOtherPlayer(null, f.local.player));
        f.config.show = false;
        f.overlay.refresh();
        check("Other players hidden", !f.overlay.isReplacingOtherPlayers() && opaque(f.paint()) == 0);
        f.config.show = true;
        f.overlay.refresh();
        check("re-enable restores skulls", f.overlay.isReplacingOtherPlayers());
        f.wild = 0; f.overlay.refresh();
        check("outside Wilderness even with stale text", !f.overlay.isReplacingOtherPlayers() && opaque(f.paint()) == 0);
        f.wild = 1;
        for (WorldType type : new WorldType[]{WorldType.PVP, WorldType.DEADMAN, WorldType.SEASONAL, WorldType.BOUNTY})
        {
            f.types = EnumSet.of(type); f.overlay.refresh();
            check("special world leaves dots " + type, !f.overlay.isReplacingOtherPlayers() && opaque(f.paint()) == 0);
        }
        f.types = EnumSet.noneOf(WorldType.class); f.overlay.refresh();
        check("normal F2P Wilderness supported", f.overlay.isReplacingOtherPlayers());
        f.levelText = ""; f.overlay.refresh();
        check("missing bracket leaves dots", !f.overlay.isReplacingOtherPlayers());
        f.levelText = "Level: 20"; f.overlay.refresh();
        f.view.square = true; f.view.paintable = false;
        check("square world-map overlap yields", opaque(f.paint()) == 0);
        f.view.paintable = true;
        Area clip = new Area(new Rectangle(10, 10, 90, 90));
        clip.subtract(new Area(new Rectangle(70, 10, 30, 30)));
        f.view.shape = clip;
        f.peer.x = 74; f.peer.y = 25;
        check("skull cannot cover compass", opaque(f.paint()) == 0);
        f.peer.x = 95; f.peer.y = 95;
        check("square corner marker rendered", opaque(f.paint()) > 0);
        f.peer.x = 99; f.peer.y = 50;
        BufferedImage edge = f.paint();
        check("square right edge clips", edge.getRGB(102, 50) == 0 && opaque(edge) > 0);
        f.peer.x = 105;
        check("outside square omitted", opaque(f.paint()) == 0);
        f.view.square = false;
        for (int top : new int[]{InterfaceID.TOPLEVEL_PRE_EOC, InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL})
        {
            f.top = top; f.peer.x = 50; f.peer.y = 50;
            check("classic layout center " + top, opaque(f.paint()) > 0);
            f.peer.x = 12; f.peer.y = 12;
            check("classic outside circle clipped " + top, opaque(f.paint()) == 0);
        }
        f.peer.x = 50; f.peer.y = 50;
        f.mapHidden = true;
        check("native hidden map", opaque(f.paint()) == 0);
        f.mapHidden = false;
        f.worldMapOpen = true;
        check("overlapping Classic World Map yields", opaque(f.paint()) == 0);
        f.worldMapOpen = false;
        check("Classic returns when map closed", opaque(f.paint()) > 0);
        f.peer.hasComposition = false;
        check("unloaded player skipped", opaque(f.paint()) == 0);
        f.peer.hasComposition = true;
        f.state = GameState.LOADING; f.overlay.refresh();
        check("loading clears active context", !f.overlay.isReplacingOtherPlayers());
        f.state = GameState.LOGGED_IN; f.overlay.refresh();
        check("relog refresh", f.overlay.isReplacingOtherPlayers());
        f.overlay.clear();
        check("cleanup clears markers", !f.overlay.isReplacingOtherPlayers() && opaque(f.paint()) == 0);
        f.config.enabled = false; f.overlay.refresh();
        check("off after relog stays off", !f.overlay.isReplacingOtherPlayers());
    }

    private void settings()
    {
        TestConfig c = new TestConfig();
        MinimapLayoutControls controls = new MinimapLayoutControls(c);
        JPanel root = new JPanel();
        JPanel row = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Wilderness player skulls");
        label.setToolTipText("Replace Other players; Requires Other players.");
        JCheckBox box = new JCheckBox();
        box.setSelected(true);
        MouseAdapter reset = new MouseAdapter() { public void mouseClicked(MouseEvent e) { box.setSelected(false); } };
        label.addMouseListener(reset);
        row.add(label, BorderLayout.CENTER); row.add(box, BorderLayout.EAST); root.add(row);
        c.show = false; controls.updateTree(root, true);
        check("checkbox disabled with Other players off", !box.isEnabled());
        check("label gray and disabled", !label.isEnabled() && label.getForeground().equals(new Color(128, 128, 128)));
        check("saved skull preference retained", box.isSelected());
        boolean resetPresent = false;
        for (java.awt.event.MouseListener listener : label.getMouseListeners()) { resetPresent |= listener == reset; }
        check("reset bypass removed", !resetPresent);
        c.show = true; controls.updateTree(root, false);
        check("enabled independently of Square HUD", box.isEnabled() && label.isEnabled());
        check("selected still retained", box.isSelected());
        resetPresent = false;
        for (java.awt.event.MouseListener listener : label.getMouseListeners()) { resetPresent |= listener == reset; }
        check("reset listener restored", resetPresent);
        c.show = false; controls.updateTree(root, true); controls.restoreAll();
        check("shutdown restores controls", box.isEnabled() && label.isEnabled());
    }

    static int opaque(BufferedImage image)
    {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++)
        { for (int x = 0; x < image.getWidth(); x++) { if ((image.getRGB(x, y) >>> 24) != 0) { n++; } } }
        return n;
    }

    static Object empty(Class<?> type)
    {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        if (type == double.class) { return 0d; }
        if (type == float.class) { return 0f; }
        return null;
    }

    static class TestConfig implements MinimapFilterConfig
    {
        boolean enabled;
        boolean show = true;
        Color otherColor = NativeDotColors.PLAYER;
        public Color otherPlayerDotColor() { return otherColor; }
        public boolean wildernessPlayerSkulls() { return enabled; }
        public boolean showPlayers() { return show; }
    }

    static class Peer
    {
        int id, level = 100, team, x = 50, y = 50;
        boolean friend, fc, clan, hasComposition = true;
        final Player player;
        Peer(int id)
        {
            this.id = id;
            PlayerComposition composition = (PlayerComposition) Proxy.newProxyInstance(PlayerComposition.class.getClassLoader(),
                new Class<?>[]{PlayerComposition.class}, (p, m, a) -> empty(m.getReturnType()));
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getId": return this.id;
                    case "getCombatLevel": return level;
                    case "getTeam": return team;
                    case "isFriend": return friend;
                    case "isFriendsChatMember": return fc;
                    case "isClanMember": return clan;
                    case "getPlayerComposition": return hasComposition ? composition : null;
                    case "getLocalLocation": return new LocalPoint(x, y, -1);
                    case "getMinimapLocation": return new Point(x, y);
                    case "getName": return "Player" + this.id;
                    default: return empty(m.getReturnType());
                }
            });
        }
    }

    static SpritePixels sprite(int[] pixels, int width, int height)
    {
        return (SpritePixels) Proxy.newProxyInstance(SpritePixels.class.getClassLoader(), new Class<?>[]{SpritePixels.class}, (p,m,a) ->
        {
            switch (m.getName())
            {
                case "getWidth": return width;
                case "getHeight": return height;
                case "getPixels": return pixels;
                case "toBufferedImage":
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0, 0, width, height, pixels, 0, width);
                    return image;
                default: return empty(m.getReturnType());
            }
        });
    }

    static class TestView implements WildernessPlayerOverlay.MapView
    {
        boolean square, paintable = true;
        Shape shape = new Rectangle(10, 10, 90, 90);
        public boolean isSquare() { return square; }
        public boolean isPaintable() { return paintable; }
        public Shape clip() { return shape; }
        public Point project(LocalPoint point) { return new Point(point.getX(), point.getY()); }
    }

    static class Fixture
    {
        final TestConfig config = new TestConfig();
        final TestView view = new TestView();
        final Peer local = new Peer(0), peer = new Peer(1);
        final List<Peer> others = new ArrayList<>();
        EnumSet<WorldType> types = EnumSet.of(WorldType.MEMBERS);
        GameState state = GameState.LOGGED_IN;
        int wild = 1, top = InterfaceID.TOPLEVEL_PRE_EOC;
        String levelText = "Level: 20";
        boolean mapHidden, worldMapOpen;
        final Client client;
        final WildernessPlayerOverlay overlay;

        Fixture()
        {
            Widget widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class}, (p,m,a) ->
            {
                switch (m.getName())
                {
                    case "getText": return levelText;
                    case "getBounds": return new Rectangle(10, 10, 90, 90);
                    case "isHidden": return mapHidden;
                    default: return empty(m.getReturnType());
                }
            });
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p,m,a) ->
            {
                switch (m.getName())
                {
                    case "getGameState": return state;
                    case "getLocalPlayer": return local.player;
                    case "getVarbitValue": return wild;
                    case "getWorldType": return types;
                    case "getTopLevelInterfaceId": return top;
                    case "getWidget":
                        if (((Integer) a[0]) == InterfaceID.Worldmap.MAP_CONTAINER)
                        { return worldMapOpen ? widget : null; }
                        return widget;
                    case "getPlayers":
                        List<Player> result = new ArrayList<>(); result.add(local.player);
                        for (Peer other : others) { result.add(other.player); }
                        return result;
                    case "createSpritePixels": return sprite((int[])a[0], (int)a[1], (int)a[2]);
                    default: return empty(m.getReturnType());
                }
            });
            overlay = new WildernessPlayerOverlay(client, config, view);
            overlay.setFallbackDot(sprite(new int[]{0xff00ffff}, 1, 1), NativeDotColors.PLAYER);
        }

        BufferedImage paint()
        {
            BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { overlay.render(g); } finally { g.dispose(); }
            return image;
        }
    }
}
