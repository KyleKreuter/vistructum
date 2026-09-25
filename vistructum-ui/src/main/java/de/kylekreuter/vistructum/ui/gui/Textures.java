package de.kylekreuter.vistructum.ui.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

final class Textures {

    private static final Color PANEL = new Color(0xC6, 0xC6, 0xC6);
    private static final Color BORDER = new Color(0x3F, 0x3F, 0x3F);
    private static final Color SLOT = new Color(0xB3, 0xB3, 0xB3);
    private static final Color SLOT_EDGE = new Color(0x8C, 0x8C, 0x8C);
    private static final Color SEPARATOR = new Color(0xA6, 0xA6, 0xA6);
    private static final Color WELL = new Color(0x2E, 0x2E, 0x2E);
    private static final Color FILTER = new Color(0xF5, 0xB0, 0x2A);
    private static final Color BUTTON = new Color(0x4A, 0x4A, 0x4A);
    private static final Color ARROW = new Color(0xA0, 0xA0, 0xA0);
    private static final Color ARROW_DISABLED = new Color(0x5A, 0x5A, 0x5A);
    private static final Color BAR_EDGE = new Color(0x1E, 0x1E, 0x1E);
    private static final Color BAR_WELL = new Color(0x3A, 0x3A, 0x3A);
    private static final Color BAR_LIGHT = new Color(0x8F, 0xE3, 0x9B);
    private static final Color BAR_FILL = new Color(0x3F, 0xA3, 0x4D);
    private static final Color BAR_SHADE = new Color(0x2A, 0x7A, 0x36);

    private Textures() {
    }

    static byte[] pixel(int height) {
        BufferedImage image = new BufferedImage(1, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        return png(image);
    }

    static byte[] barFrame() {
        BufferedImage image = new BufferedImage(ProgressBar.FRAME_WIDTH, ProgressBar.FRAME_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(BAR_EDGE);
        g.fillRect(0, 0, ProgressBar.FRAME_WIDTH, ProgressBar.FRAME_HEIGHT);
        g.setColor(BAR_WELL);
        g.fillRect(1, 1, ProgressBar.FILL_WIDTH, ProgressBar.FILL_HEIGHT);
        g.dispose();
        return png(image);
    }

    static byte[] barFill(int width) {
        BufferedImage image = new BufferedImage(width, ProgressBar.FILL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(BAR_FILL);
        g.fillRect(0, 0, width, ProgressBar.FILL_HEIGHT);
        g.setColor(BAR_LIGHT);
        g.fillRect(0, 0, width, 1);
        g.setColor(BAR_SHADE);
        g.fillRect(0, ProgressBar.FILL_HEIGHT - 1, width, 1);
        g.dispose();
        return png(image);
    }

    static byte[] detailBackground() {
        BufferedImage image = panel(Layout.GUI_WIDTH, Layout.DETAIL_HEIGHT);
        Graphics2D g = image.createGraphics();
        for (int slot : Layout.DETAIL_BUTTONS) {
            slot(g, Layout.slotLeft(slot), Layout.slotTop(slot));
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 7 + 18 * col, Layout.playerSlotTop(row));
            }
        }
        g.dispose();
        return png(image);
    }

    static byte[] scenePanel() {
        BufferedImage image = panel(Layout.SIDE_WIDTH, Layout.DETAIL_HEIGHT);
        Graphics2D g = image.createGraphics();
        separator(g, Layout.SCENE_SEPARATOR);
        int left = Layout.WELL_LEFT - Layout.SCENE_LEFT;
        g.setColor(BORDER);
        g.fillRect(left - 1, Layout.WELL_TOP - 1, Layout.WELL_SIZE + 2, Layout.WELL_SIZE + 2);
        g.setColor(WELL);
        g.fillRect(left, Layout.WELL_TOP, Layout.WELL_SIZE, Layout.WELL_SIZE);
        g.dispose();
        return png(image);
    }

    static byte[] infoPanel() {
        BufferedImage image = panel(Layout.SIDE_WIDTH, Layout.DETAIL_HEIGHT);
        Graphics2D g = image.createGraphics();
        int left = Layout.FACE_LEFT - Layout.INFO_LEFT;
        g.setColor(BORDER);
        g.fillRect(left - 1, Layout.FACE_TOP - 1, Layout.FACE_SIZE + 2, Layout.FACE_SIZE + 2);
        separator(g, Layout.INFO_SEPARATOR);
        g.dispose();
        return png(image);
    }

    static byte[] listBackground() {
        BufferedImage image = panel(Layout.GUI_WIDTH, Layout.LIST_HEIGHT);
        Graphics2D g = image.createGraphics();
        for (int slot : Layout.LIST_CARDS) {
            slot(g, Layout.slotLeft(slot), Layout.slotTop(slot));
        }
        for (int slot : Layout.LIST_BUTTONS) {
            int left = Layout.slotLeft(slot);
            int top = Layout.slotTop(slot);
            g.setColor(BORDER);
            g.fillRect(left, top, 18, 18);
            g.setColor(BUTTON);
            g.fillRect(left + 1, top + 1, 16, 16);
        }
        g.dispose();
        return png(image);
    }

    static byte[] icon(String kind) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        switch (kind) {
            case "confirm" -> {
                g.setColor(new Color(0x2E, 0xCC, 0x40));
                g.drawPolyline(new int[]{2, 6, 13}, new int[]{8, 12, 3}, 3);
            }
            case "deny" -> {
                g.setColor(new Color(0xE5, 0x39, 0x35));
                g.drawLine(3, 3, 12, 12);
                g.drawLine(12, 3, 3, 12);
            }
            case "teleport" -> {
                teleportLetters(g, 3, 5, new Color(0x6B, 0x4E, 0x00));
                teleportLetters(g, 2, 4, new Color(0xF5, 0xC5, 0x18));
            }
            case "list" -> {
                g.setColor(new Color(0x3F, 0x3F, 0x3F));
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        g.fillRect(2 + col * 7, 2 + row * 7, 5, 5);
                    }
                }
            }
            case "previous", "previous_disabled", "next", "next_disabled" -> {
                boolean disabled = kind.endsWith("_disabled");
                int[] xs = kind.startsWith("previous") ? new int[]{9, 6, 9} : new int[]{6, 9, 6};
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g.setColor(disabled ? ARROW_DISABLED : ARROW);
                g.drawPolyline(xs, new int[]{5, 8, 11}, 3);
            }
            case "filter_open" -> disc(image, 3.0, 5.0, FILTER);
            case "filter_closed" -> disc(image, 0, 5.0, FILTER);
            case "card_confirmed" -> card(g, new Color(0x9E, 0x1F, 0x1F), new Color(0xD3, 0x3B, 0x3B));
            case "card_dismissed" -> card(g, new Color(0x7A, 0x7A, 0x7A), new Color(0x9E, 0x9E, 0x9E));
            default -> card(g, new Color(0x1E, 0x4F, 0xC2), new Color(0x3F, 0x74, 0xE8));
        }
        g.dispose();
        return png(image);
    }

    private static void disc(BufferedImage image, double inner, double outer, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double distance = Math.hypot(x - 7.5, y - 7.5);
                if (distance >= inner && distance <= outer) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
        }
    }

    private static void card(Graphics2D g, Color edge, Color fill) {
        g.setColor(edge);
        g.fillRect(1, 1, 14, 14);
        g.setColor(fill);
        g.fillRect(2, 2, 12, 12);
    }

    private static void teleportLetters(Graphics2D g, int left, int top, Color color) {
        g.setColor(color);
        g.fillRect(left, top, 6, 2);
        g.fillRect(left + 2, top, 2, 8);
        g.fillRect(left + 7, top, 2, 8);
        g.fillRect(left + 7, top, 4, 2);
        g.fillRect(left + 10, top + 1, 2, 4);
        g.fillRect(left + 7, top + 4, 4, 2);
    }

    private static BufferedImage panel(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(BORDER);
        g.fillRect(0, 0, width, height);
        g.setColor(PANEL);
        g.fillRect(1, 1, width - 2, height - 2);
        g.dispose();
        return image;
    }

    private static void separator(Graphics2D g, int top) {
        g.setColor(SEPARATOR);
        g.fillRect(Layout.PADDING, top, Layout.SIDE_WIDTH - 2 * Layout.PADDING, 1);
    }

    private static void slot(Graphics2D g, int left, int top) {
        g.setColor(SLOT_EDGE);
        g.fillRect(left, top, 18, 18);
        g.setColor(SLOT);
        g.fillRect(left + 1, top + 1, 16, 16);
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
