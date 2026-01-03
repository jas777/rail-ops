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

        System.out.println("\n=== PathFinder Debug ===");
        System.out.println("Start: " + startNodeId);
        System.out.println("Target: " + targetNodeId);
        System.out.println("Available nodes in graph: " + logicalGraph.keySet());

        if (!logicalGraph.containsKey(startNodeId)) {
            System.out.println("ERROR: Start node not in graph!");
            return null;
        }

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

            System.out.println("Visiting: " + currentNode);

            if (currentNode.equals(targetNodeId)) {
                List<String> path = reconstructPath(previous, startNodeId, targetNodeId);
                System.out.println("Path found: " + path);
                return path;
            }

            List<TrackLink> neighbors = logicalGraph.getOrDefault(currentNode, new ArrayList<>());
            System.out.println("  Neighbors: " + neighbors.size());

            for (TrackLink link : neighbors) {
                System.out.println("    Checking link to: " + link.targetTrackId());

                // Check if this link is traversable given current switch states
                if (link.isConditional()) {
                    String requiredState = link.requiredSwitchState();
                    String actualState = switchStates.get(link.requiredSwitchId());
                    System.out.println("      Conditional: needs " + link.requiredSwitchId() + "=" + requiredState +
                            ", actual=" + actualState);
                    if (actualState == null || !requiredState.equals(actualState)) {
                        System.out.println("      BLOCKED - switch in wrong position");
                        continue;
                    }
                }

                String neighbor = link.targetTrackId();
                double newDist = distances.get(currentNode) + 1.0;

                if (newDist < distances.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    distances.put(neighbor, newDist);
                    previous.put(neighbor, currentNode);
                    queue.offer(new NodeDistance(neighbor, newDist));
                    System.out.println("      Added to queue");
                }
            }
        }

        System.out.println("No path found - exhausted all possibilities");
        System.out.println("Visited nodes: " + visited);
        return null;
    }

    private List<String> reconstructPath(Map<String, String> previous,
                                         String start, String target) {
        List<String> path = new ArrayList<>();
        String current = target;

        while (current != null) {
            path.add(0, current);
            current = previous.get(current);
            if (current != null && current.equals(start)) {
                path.add(0, start);
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