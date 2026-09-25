package de.kylekreuter.vistructum.inference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

final class Clusters {

    private Clusters() {
    }

    static List<Detection> flagged(WindowBatch batch, double[] scores, double threshold, int minVotes) {
        return all(batch, scores, threshold).stream().filter(detection -> detection.votes() >= minVotes).toList();
    }

    static List<Detection> all(WindowBatch batch, double[] scores, double threshold) {
        int grid = Contract.GRID;
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= threshold) {
                hits.add(i);
            }
        }
        int[] parent = new int[hits.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int a = 0; a < hits.size(); a++) {
            for (int b = a + 1; b < hits.size(); b++) {
                if (Math.abs(batch.tops()[hits.get(a)] - batch.tops()[hits.get(b)]) < grid
                        && Math.abs(batch.lefts()[hits.get(a)] - batch.lefts()[hits.get(b)]) < grid) {
                    union(parent, a, b);
                }
            }
        }
        TreeMap<Integer, List<Integer>> components = new TreeMap<>();
        for (int i = 0; i < hits.size(); i++) {
            components.computeIfAbsent(find(parent, i), root -> new ArrayList<>()).add(hits.get(i));
        }
        List<Detection> detections = new ArrayList<>();
        for (List<Integer> members : components.values()) {
            int top = Integer.MAX_VALUE;
            int left = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;
            int right = Integer.MIN_VALUE;
            double score = Double.NEGATIVE_INFINITY;
            for (int window : members) {
                top = Math.min(top, batch.tops()[window]);
                left = Math.min(left, batch.lefts()[window]);
                bottom = Math.max(bottom, batch.tops()[window] + grid);
                right = Math.max(right, batch.lefts()[window] + grid);
                score = Math.max(score, scores[window]);
            }
            detections.add(new Detection(top, left, bottom, right, score, members.size()));
        }
        detections.sort(Comparator.comparingDouble(Detection::score).reversed());
        return detections;
    }

    private static int find(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
        }
    }
}
