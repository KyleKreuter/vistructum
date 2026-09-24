package de.kylekreuter.vistructum.core.store;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface Binder {

    Binder NONE = statement -> {
    };

    void bind(PreparedStatement statement) throws SQLException;
}
