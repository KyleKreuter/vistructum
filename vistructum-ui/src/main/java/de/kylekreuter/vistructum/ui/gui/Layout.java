package de.kylekreuter.vistructum.ui.gui;

import java.util.ArrayList;
import java.util.List;

final class Layout {

    static final int GUI_WIDTH = 176;
    static final int DETAIL_ROWS = 3;
    static final int DETAIL_HEIGHT = 114 + 18 * DETAIL_ROWS;
    static final int LIST_HEIGHT = 127;
    static final int CONFIRM = 11;
    static final int TELEPORT = 13;
    static final int DENY = 15;
    static final int BACK = 18;
    static final List<Integer> DETAIL_BUTTONS = List.of(CONFIRM, TELEPORT, DENY, BACK);
    static final List<Integer> LIST_CARDS = cards();
    static final int PREVIOUS_PAGE = 45;
    static final int NEXT_PAGE = 53;
    static final int FILTER = 8;
    static final List<Integer> LIST_BUTTONS = List.of(PREVIOUS_PAGE, NEXT_PAGE);
    static final int PAGE_TOP = slotTop(PREVIOUS_PAGE) + 5;
    static final int TITLE_TOP = 6;
    static final int LOGO_WIDTH = 114;
    static final int LOGO_HEIGHT = 24;
    static final int LOGO_TOP = 4;
    static final int LOGO_LEFT = (GUI_WIDTH - LOGO_WIDTH) / 2;
    static final int PADDING = 6;
    static final int SIDE_GAP = 4;
    static final int SIDE_WIDTH = 140;
    static final int SCENE_LEFT = -SIDE_GAP - SIDE_WIDTH;
    static final int INFO_LEFT = GUI_WIDTH + SIDE_GAP;
    static final int MAP_PIXELS = 64;
    static final int WELL_SIZE = 128;
    static final int WELL_LEFT = SCENE_LEFT + PADDING;
    static final int WELL_TOP = DETAIL_HEIGHT - PADDING - WELL_SIZE;
    static final int SCENE_LABEL_TOP = 12;
    static final int SCENE_SEPARATOR = (SCENE_LABEL_TOP + 7 + WELL_TOP - 1) / 2;
    static final int FACE_PIXEL = 4;
    static final int FACE_SIZE = 8 * FACE_PIXEL;
    static final int FACE_LEFT = INFO_LEFT + PADDING;
    static final int FACE_TOP = PADDING;
    static final int NAME_LEFT = FACE_LEFT + FACE_SIZE + PADDING;
    static final int NAME_TOP = FACE_TOP + (FACE_SIZE - 7) / 2;
    static final int INFO_SEPARATOR = FACE_TOP + FACE_SIZE + PADDING;
    static final int MAP_PIXEL = 2;
    static final int SPACIOUS_FIELDS = 5;
    static final int VALUE_OFFSET = 10;

    private Layout() {
    }

    static int fieldTop(int fields) {
        return INFO_SEPARATOR + (fields > SPACIOUS_FIELDS ? 5 : 6);
    }

    static int fieldStep(int fields) {
        return fields > SPACIOUS_FIELDS ? 19 : 24;
    }

    static int mapLeft(int pixelSize) {
        return WELL_LEFT + (WELL_SIZE - MAP_PIXELS * pixelSize) / 2;
    }

    static int mapTop(int pixelSize) {
        return WELL_TOP + (WELL_SIZE - MAP_PIXELS * pixelSize) / 2;
    }

    static int slotLeft(int slot) {
        return 7 + 18 * (slot % 9);
    }

    static int slotTop(int slot) {
        return 17 + 18 * (slot / 9);
    }

    static int playerSlotTop(int row) {
        return row < 3 ? DETAIL_HEIGHT - 84 + 18 * row : DETAIL_HEIGHT - 26;
    }

    private static List<Integer> cards() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return List.copyOf(slots);
    }
}
