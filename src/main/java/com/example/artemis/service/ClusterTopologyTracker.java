package com.example.artemis.service;

import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live Artemis cluster nodes as the broker pushes topology updates.
 * Registered as a {@link ClusterTopologyListener} via {@link ArtemisTopologyRebalancer}.
 */
public class ClusterTopologyTracker implements ClusterTopologyListener {

    private static final Logger log = LoggerFactory.getLogger(ClusterTopologyTracker.class);

    private final ConcurrentHashMap<String, TopologyMember> activeNodes = new ConcurrentHashMap<>();

    @Override
    public void nodeUP(TopologyMember member, boolean last) {
        activeNodes.put(member.getNodeId(), member);
        log.info("Cluster nodeUP: nodeId={} primary={} backup={} totalKnownNodes={} initialBroadcastComplete={}",
                member.getNodeId(), member.getPrimary(), member.getBackup(), activeNodes.size(), last);
    }

    @Override
    public void nodeDown(long eventUID, String nodeID) {
        TopologyMember removed = activeNodes.remove(nodeID);
        if (removed != null) {
            log.warn("Cluster nodeDown: nodeId={} remainingNodes={}", nodeID, activeNodes.size());
        } else {
            log.debug("Cluster nodeDown received for unknown nodeId={}", nodeID);
        }
    }

    public int  getActiveNodeCount() { return activeNodes.size(); }
    public boolean isClusterHealthy() { return activeNodes.size() > 1; }
}
