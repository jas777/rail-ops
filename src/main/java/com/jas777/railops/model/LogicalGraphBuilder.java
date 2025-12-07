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

        Map<String, Switch> connectionIdToSwitchMap = buildConnectionIdToSwitchMap(config.getSwitches());

        for (Track currentTrack : config.getTracks()) {

            List<TrackLink> links = new ArrayList<>();
            String exitId = currentTrack.exitConnectionId();

            if (exitId != null) {
                if (connectionIdToSwitchMap.containsKey(exitId)) {
                    Switch sw = connectionIdToSwitchMap.get(exitId);

                    String mainTargetId = sw.p2MainConnectionId();
                    if (mainTargetId != null) {
                        links.add(new TrackLink(mainTargetId, sw.id(), "MAIN"));
                    }

                    String sideTargetId = sw.p2SideConnectionId();
                    if (sideTargetId != null) {
                        links.add(new TrackLink(sideTargetId, sw.id(), "SIDE"));
                    }
                } else {
                    links.add(new TrackLink(exitId));
                }
            }

            logicalGraphMap.put(currentTrack.id(), links);
        }

        return logicalGraphMap;
    }

    private Map<String, Switch> buildConnectionIdToSwitchMap(List<Switch> switches) {
        Map<String, Switch> map = new HashMap<>();
        for (Switch sw : switches) {
            String p1Id = sw.p1ConnectionId();
            if (p1Id != null) {
                map.put(p1Id, sw);
            }
        }
        return map;
    }
}