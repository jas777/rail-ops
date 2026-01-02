package com.jas777.railops.logic;

import com.jas777.railops.model.TrackLink;

import java.util.*;

public class PathFinder {

    private final Map<String, List<TrackLink>> logicalGraph;

    public PathFinder(Map<String, List<TrackLink>> logicalGraph) {
        this.logicalGraph = logicalGraph;
    }

    /**
     * Find a path from start node to target node considering switch states.
     * Uses Dijkstra's algorithm.
     */
    public List<String> findPath(String startNodeId, String targetNodeId,
                                 Map<String, String> switchStates) {

        Map<String, String> previous = new HashMap<>();
        Map<String, Double> distances = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(
                Comparator.comparingDouble(nd -> nd.distance)
        );
        Set<String> visited = new HashSet<>();

        distances.put(startNodeId, 0.0);
        queue.offer(new NodeDistance(startNodeId, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            String currentNode = current.nodeId;

            if (visited.contains(currentNode)) continue;
            visited.add(currentNode);

            if (currentNode.equals(targetNodeId)) {
                return reconstructPath(previous, startNodeId, targetNodeId);
            }

            List<TrackLink> neighbors = logicalGraph.getOrDefault(currentNode, new ArrayList<>());

            for (TrackLink link : neighbors) {
                // Check if this link is traversable given current switch states
                if (link.isConditional()) {
                    String requiredState = link.requiredSwitchState();
                    String actualState = switchStates.get(link.requiredSwitchId());
                    if (!requiredState.equals(actualState)) {
                        continue; // This path is not available
                    }
                }

                String neighbor = link.targetTrackId();
                double newDist = distances.get(currentNode) + 1.0;

                if (newDist < distances.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    distances.put(neighbor, newDist);
                    previous.put(neighbor, currentNode);
                    queue.offer(new NodeDistance(neighbor, newDist));
                }
            }
        }

        return null; // No path found
    }

    private List<String> reconstructPath(Map<String, String> previous,
                                         String start, String target) {
        List<String> path = new ArrayList<>();
        String current = target;

        while (current != null) {
            path.addFirst(current);
            current = previous.get(current);
            if (current != null && current.equals(start)) {
                path.addFirst(start);
                break;
            }
        }

        return path;
    }

    private static class NodeDistance {
        String nodeId;
        double distance;

        NodeDistance(String nodeId, double distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }
    }
}