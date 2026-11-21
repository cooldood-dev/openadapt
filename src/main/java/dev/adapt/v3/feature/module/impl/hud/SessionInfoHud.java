package dev.adapt.v3.feature.module.impl.hud;

import dev.adapt.v3.event.impl.ChatEvent;
import dev.adapt.v3.event.impl.JoinWorldEvent;
import dev.adapt.v3.event.impl.ReceivePacketEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.hud.HudModule;
import dev.adapt.v3.ui.render.Fonts;
import dev.adapt.v3.ui.render.Glass;
import dev.adapt.v3.ui.render.Render2D;
import dev.adapt.v3.ui.theme.OldSchoolStyle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SessionInfoHud extends HudModule {
    public static final int MODERN_PANEL = 0x7B151515;
    public static final int MODERN_TILE = 0xFFB4BDCB;
    public static final int MODERN_ICON = 0xFF34383D;
    public static final int ICON_SIZE = 64;
    public static final Identifier ICON_KILLS = lucide("swords");
    public static final Identifier ICON_WINS = lucide("trophy");
    public static final Identifier ICON_TIME = lucide("hourglass");
    public static final Identifier ICON_SERVER = lucide("server");
    public static final int MODERN_TEXT = 0xFFDDDDDD;
    public static final int OS_FILL = 0x64000000;
    public static final int OS_OUTLINE = 0xFFA1A9B4;
    public static final int OS_TEXT = 0xFFFFFFFF;
    public static final int OS_VALUE = 0xFFDDDDDD;
    public static final int OS_VALUE_DIM = 0xFFC8CCD2;
    private long startMs = System.currentTimeMillis();
    private int kills;
    private int wins;
    private FaviconTexture favicon;
    private String faviconIp;
    private byte[] faviconBytes;
    private boolean faviconValid;
    // when a proxy redirects you, the server you're actually sent to, not the proxy address you typed
    private String transferHost;
    private boolean expectingTransfer;
    // behind a transparent proxy the client never learns the backend, so fall back to the domain the server advertises
    private String advertisedName;
    private static final Pattern DOMAIN = Pattern.compile("(?i)\\b((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,})\\b");

    // Lucide icons are rasterised into these PNGs by tools/LucideRaster.java
    private static Identifier lucide(String name) {
        return Identifier.fromNamespaceAndPath("adaptv3", "textures/icons/lucide/" + name + ".png");
    }

    public SessionInfoHud() {
        super("Session Info", "Time played, kills and wins this session.", ModuleCategory.VISUAL, 0.006F, 0.12F);
        this.setEnabled(true);
    }

    @Override
    public float getWidth() {
        return OldSchoolStyle.active() ? 140.0F : 150.0F;
    }

    @Override
    public float getHeight() {
        return OldSchoolStyle.active() ? 55.0F : 128.0F;
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        this.startMs = System.currentTimeMillis();
        this.kills = 0;
        this.wins = 0;
        this.advertisedName = null;
        // keep the captured host across the transfer's reconnect; a plain connect clears a stale one
        if (this.expectingTransfer) {
            this.expectingTransfer = false;
        } else {
            this.transferHost = null;
        }
    }

    private void updateAdvertisedName() {
        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return;
        }

        String found = findDomain(objective.getDisplayName().getString());
        if (found == null) {
            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                if (entry.isHidden()) {
                    continue;
                }

                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                Component line = team == null ? entry.ownerName() : PlayerTeam.formatNameForTeam(team, entry.ownerName());
                found = findDomain(line.getString());
                if (found != null) {
                    break;
                }
            }
        }

        if (found != null) {
            this.advertisedName = found;
        }
    }

    private static String findDomain(String text) {
        Matcher matcher = DOMAIN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Subscribe
    public void onChat(ChatEvent event) {
        if (mc.player != null && event.getMessage().getString().contains("was killed by " + mc.player.getName().getString())) {
            this.kills++;
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundSetTitleTextPacket titlepacket && titlepacket.text().getString().contains("VICTORY!")) {
            this.wins++;
        }

        if (event.getPacket() instanceof ClientboundTransferPacket transferpacket) {
            this.transferHost = transferpacket.host();
            this.expectingTransfer = true;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, float partialTick, boolean editing) {
        this.updateAdvertisedName();
        String time = sessionTime(System.currentTimeMillis() - this.startMs);
        if (OldSchoolStyle.active()) {
            this.renderOldSchool(graphics, time);
        } else {
            this.renderModern(graphics, time, this.serverName());
        }
    }

    private String serverName() {
        if (mc.hasSingleplayerServer()) {
            return "Singleplayer";
        }

        if (this.advertisedName != null) {
            return this.advertisedName;
        }

        if (this.transferHost != null) {
            return this.transferHost;
        }

        return mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
    }

    private void renderModern(GuiGraphicsExtractor graphics, String time, String server) {
        Glass.drawBorderlessPanel(graphics, 0.0F, 0.0F, 150.0F, 128.0F, 8.0F, 1.0F, 0.55F, 1.0F, MODERN_PANEL);
        Fonts.TITLE.drawCentered(graphics, "Session Information", 75.0F, 14.0F, -1);
        Identifier favicon = this.serverFavicon();
        if (favicon != null) {
            Render2D.blitRoundedTexture(graphics, favicon, 18.0F, 26.0F, 20.0F, 20.0F, 4.0F, 0.0F, 0.0F, 1.0F, 1.0F, -1);
        } else {
            drawTile(graphics, ICON_SERVER, 26);
        }

        Fonts.SMALL.drawVCentered(graphics, server, 42.0F, 36.0F, MODERN_TEXT);
        Identifier[] icons = {ICON_KILLS, ICON_WINS, ICON_TIME};
        String[] values = {this.kills + " kills", this.wins + " wins", time};

        for (int i = 0; i < values.length; i++) {
            int ty = 51 + i * 25;
            drawTile(graphics, icons[i], ty);
            Fonts.SEMIBOLD.drawVCentered(graphics, values[i], 42.0F, ty + 10.0F, MODERN_TEXT);
        }
    }

    private static void drawTile(GuiGraphicsExtractor graphics, Identifier icon, int ty) {
        Render2D.fillSubpixelRoundedRect(graphics, 18.0F, ty, 20.0F, 20.0F, 5.0F, MODERN_TILE);
        graphics.nextStratum();
        Render2D.blit(graphics, icon, 21, ty + 3, 14, 14, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, MODERN_ICON);
    }

    // The server's own list icon, uploaded once per address the way the multiplayer screen does it
    private Identifier serverFavicon() {
        ServerData data = mc.getCurrentServer();
        byte[] bytes = data == null || mc.hasSingleplayerServer() ? null : data.getIconBytes();
        if (bytes == null) {
            return null;
        }

        if (this.favicon == null || !data.ip.equals(this.faviconIp)) {
            if (this.favicon != null) {
                this.favicon.close();
            }

            this.favicon = FaviconTexture.forServer(mc.getTextureManager(), data.ip);
            this.faviconIp = data.ip;
            this.faviconBytes = null;
        }

        if (bytes != this.faviconBytes) {
            this.faviconBytes = bytes;
            try {
                this.favicon.upload(NativeImage.read(bytes));
                this.faviconValid = true;
            } catch (Exception exception) {
                this.favicon.clear();
                this.faviconValid = false;
            }
        }

        return this.faviconValid ? this.favicon.textureLocation() : null;
    }

    private void renderOldSchool(GuiGraphicsExtractor graphics, String time) {
        float w = 140.0F;
        float h = 55.0F;
        Render2D.fillRect(graphics, 0.0F, 0.0F, w, h, OS_FILL);
        Render2D.fillRect(graphics, 0.0F, 0.0F, w, 1.0F, OS_OUTLINE);
        Render2D.fillRect(graphics, 0.0F, h - 1.0F, w, 1.0F, OS_OUTLINE);
        Render2D.fillRect(graphics, 0.0F, 0.0F, 1.0F, h, OS_OUTLINE);
        Render2D.fillRect(graphics, w - 1.0F, 0.0F, 1.0F, h, OS_OUTLINE);
        Fonts.SEMIBOLD.drawCentered(graphics, "Session Stats", 70.0F, 9.0F, OS_TEXT);
        Fonts.SEMIBOLD.drawCentered(graphics, time, 70.0F, 27.0F, OS_VALUE);
        Fonts.SMALL.drawCentered(graphics, "kills: " + this.kills, 35.0F, 41.0F, OS_VALUE_DIM);
        Fonts.SMALL.drawCentered(graphics, "wins: " + this.wins, 105.0F, 41.0F, OS_VALUE_DIM);
    }

    public static String sessionTime(long elapsedMs) {
        long total = Math.max(0L, elapsedMs / 1000L);
        long hours = total / 3600L;
        long minutes = total % 3600L / 60L;
        long seconds = total % 60L;
        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append("h ");
        }

        if (hours > 0L || minutes > 0L) {
            builder.append(minutes).append("m ");
        }

        builder.append(seconds).append("s");
        return builder.toString();
    }
}
