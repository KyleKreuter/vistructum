package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import static de.kylekreuter.vistructum.ui.text.Messages.number;
import static de.kylekreuter.vistructum.ui.text.Messages.text;

public final class ScanText {

    private ScanText() {
    }

    public static Component progress(Messages messages, ScanJob job) {
        return messages.chat(job.tilesTotal() == 0 ? Message.SCAN_PLANNING : Message.SCAN_PROGRESS, values(job));
    }

    public static Component finished(Messages messages, ScanJob job) {
        return messages.chat(switch (job.status()) {
            case DONE, QUEUED, RUNNING -> Message.SCAN_DONE;
            case CANCELLED -> Message.SCAN_STOPPED;
            case FAILED -> Message.SCAN_FAILED;
        }, values(job));
    }

    public static TagResolver values(ScanJob job) {
        return TagResolver.resolver(number("id", job.id()), text("world", job.world()),
                number("done", job.tilesDone()), number("total", job.tilesTotal()),
                number("findings", job.findings()), number("failures", job.failures()));
    }
}
