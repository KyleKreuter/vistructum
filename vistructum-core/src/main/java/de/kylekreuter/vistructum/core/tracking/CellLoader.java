package de.kylekreuter.vistructum.core.tracking;

import java.sql.SQLException;
import java.util.List;

public interface CellLoader {

    List<PositionSummary> summaries(String world, int cellX, int cellZ) throws SQLException;

    List<BlockChange> changes(String world, int cellX, int cellZ) throws SQLException;
}
