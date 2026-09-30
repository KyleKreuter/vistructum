package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.RollbackResult;

public record RollbackView(int restored, int skipped) {

    public static RollbackView of(RollbackResult result) {
        return new RollbackView(result.restored(), result.skipped());
    }
}
