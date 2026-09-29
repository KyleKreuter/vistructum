package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.api.Recording;

import java.util.List;

public record EvidenceView(long findingId, VolumeView before, List<ChangeView> changes,
                           List<RecordingView> recordings) {

    public static EvidenceView of(Evidence evidence) {
        return shifted(evidence, 0, 0, 0);
    }

    public static EvidenceView relative(Evidence evidence) {
        BlockVolume before = evidence.before();
        return shifted(evidence, before.minX(), before.minY(), before.minZ());
    }

    private static EvidenceView shifted(Evidence evidence, int shiftX, int shiftY, int shiftZ) {
        BlockVolume before = evidence.before();
        VolumeView volume = new VolumeView(before.minX() - shiftX, before.minY() - shiftY, before.minZ() - shiftZ,
                before.sizeX(), before.sizeY(), before.sizeZ(), before.palette(), before.cells());
        List<ChangeView> changes = evidence.changes().stream()
                .map(change -> ChangeView.of(change, shiftX, shiftY, shiftZ)).toList();
        List<RecordingView> recordings = evidence.recordings().stream()
                .map(recording -> RecordingView.of(recording, shiftX, shiftY, shiftZ)).toList();
        return new EvidenceView(evidence.findingId(), volume, changes, recordings);
    }

    public record VolumeView(long minX, long minY, long minZ, long sizeX, long sizeY, long sizeZ, List<String> palette,
                             int[] cells) {
    }

    public record ChangeView(long t, String player, String playerName, String action, long x, long y, long z,
                             String blockData) {

        static ChangeView of(BlockEvent change, int shiftX, int shiftY, int shiftZ) {
            return new ChangeView(change.at().toEpochMilli(), change.player().toString(), change.playerName(),
                    change.action().name(), change.x() - shiftX, change.y() - shiftY, change.z() - shiftZ,
                    change.blockData());
        }
    }

    public record RecordingView(String player, String playerName, List<FrameView> frames) {

        static RecordingView of(Recording recording, int shiftX, int shiftY, int shiftZ) {
            return new RecordingView(recording.player().toString(), recording.playerName(), recording.frames().stream()
                    .map(frame -> FrameView.of(frame, shiftX, shiftY, shiftZ)).toList());
        }
    }

    public record FrameView(long t, double x, double y, double z, float yaw, float pitch, long flags,
                            String mainHand) {

        static FrameView of(MotionFrame frame, int shiftX, int shiftY, int shiftZ) {
            return new FrameView(frame.atMillis(), frame.x() - shiftX, frame.y() - shiftY, frame.z() - shiftZ,
                    frame.yaw(), frame.pitch(), frame.flags(), frame.mainHand());
        }
    }
}
