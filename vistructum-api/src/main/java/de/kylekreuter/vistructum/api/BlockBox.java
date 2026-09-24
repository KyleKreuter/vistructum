package de.kylekreuter.vistructum.api;

public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("inverted box " + minX + "," + minY + "," + minZ + " .. "
                    + maxX + "," + maxY + "," + maxZ);
        }
    }

    public int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    public int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    public boolean overlaps(BlockBox other) {
        return minX <= other.maxX && other.minX <= maxX
                && minY <= other.maxY && other.minY <= maxY
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }
}
