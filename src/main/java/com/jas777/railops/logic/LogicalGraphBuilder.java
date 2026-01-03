package com.jas777.railops.logic;

import com.jas777.railops.model.StationConfig;
import com.jas777.railops.model.Track;
import com.jas777.railops.model.Switch;
import com.jas777.railops.model.TrackLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogicalGraphBuilder {

    public Map<String, List<TrackLink>> buildLogicalGraph(StationConfig config) {

        Map<String, List<TrackLink>> logicalGraphMap = new HashMap<>();

        // Build connection map: connectionId -> Switch
        Map<String, Switch> p1ConnectionToSwitchMap = new HashMap<>();
        for (Switch sw : config.getSwitches()) {
            if (sw.p1ConnectionId() != null) {
                p1ConnectionToSwitchMap.put(sw.p1ConnectionId(), sw);
            }
        }

        System.out.println("\n=== Building Logical Graph ===");

        // STEP 1: For each track, create link FROM entryNodeId TO exitNodeId
        for (Track track : config.getTracks()) {
            if (track.entryNodeId() == null || track.exitNodeId() == null) continue;

            List<TrackLink> links = new ArrayList<>();
            links.add(new TrackLink(track.exitNodeId()));
            logicalGraphMap.put(track.entryNodeId(), links);

            System.out.println("Track " + track.id() + ": " + track.entryNodeId() + " -> " + track.exitNodeId());
        }

        // STEP 2: For each track exit that connects to a switch P1, add switch routing
        for (Track track : config.getTracks()) {
            String exitId = track.exitNodeId();

            if (exitId == null) continue;

            // Check if this track exits into a switch's P1
            if (p1ConnectionToSwitchMap.containsKey(exitId)) {
                Switch sw = p1ConnectionToSwitchMap.get(exitId);
                List<TrackLink> switchLinks = new ArrayList<>();

                System.out.println("Track " + track.id() + " exits to switch " + sw.id() + " P1");

                // Add conditional links based on switch state
                if (sw.p2MainConnectionId() != null) {
                    switchLinks.add(new TrackLink(sw.p2MainConnectionId(), sw.id(), "MAIN"));
                    System.out.println("  Switch " + sw.id() + " P1 -> P2Main: " + sw.p2MainConnectionId() + " (when MAIN)");
                }

                if (sw.p2SideConnectionId() != null) {
                    switchLinks.add(new TrackLink(sw.p2SideConnectionId(), sw.id(), "SIDE"));
                    System.out.println("  Switch " + sw.id() + " P1 -> P2Side: " + sw.p2SideConnectionId() + " (when SIDE)");
                }

                // Store using switch P1 connection as the key
                logicalGraphMap.put(exitId, switchLinks);
            }
        }

        System.out.println("\n=== Final Logical Graph ===");
        for (Map.Entry<String, List<TrackLink>> entry : logicalGraphMap.entrySet()) {
            System.out.print(entry.getKey() + " -> ");
            for (TrackLink link : entry.getValue()) {
                if (link.isConditional()) {
                    System.out.print(link.targetTrackId() + " (if " + link.requiredSwitchId() + "=" + link.requiredSwitchState() + ") ");
                } else {
                    System.out.print(link.targetTrackId() + " ");
                }
            }
            System.out.println();
        }

        return logicalGraphMap;
    }
}