package com.example.artemis.listener;

import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import com.example.artemis.event.TopologyChangeEvent;
import com.example.artemis.service.ClusterTopologyTracker;

/**
 * Registered on the Artemis {@code ServerLocator} to receive cluster topology changes.
 * Delegates to {@link ClusterTopologyTracker} to maintain the live node map and publishes
 * a {@link TopologyChangeEvent} so the rebalancer can react without coupling to Artemis APIs.
 *
 * <p>Initial broadcast detection uses a startup grace period rather than the {@code last}
 * flag — that flag is always {@code false} in some Artemis cluster configurations, causing
 * runtime nodeUP events (including broker recovery) to be incorrectly suppressed.
 */
public class TopologyRebalanceListener implements ClusterTopologyListener {

    private static final Logger log = LoggerFactory.getLogger(TopologyRebalanceListener.class);

    private final ClusterTopologyTracker    tracker;
    private final ApplicationEventPublisher eventPublisher;
    private final long                      startTime             = System.currentTimeMillis();
    private final long                      startupGracePeriodMs;

    public TopologyRebalanceListener(ClusterTopologyTracker tracker,
                                     ApplicationEventPublisher eventPublisher,
                                     long startupGracePeriodMs) {
        this.tracker              = tracker;
        this.eventPublisher       = eventPublisher;
        this.startupGracePeriodMs = startupGracePeriodMs;
    }

    @Override
    public void nodeUP(TopologyMember member, boolean last) {
        tracker.nodeUP(member, last);

        long    uptimeMs  = System.currentTimeMillis() - startTime;
        boolean isInitial = uptimeMs < startupGracePeriodMs;

        TopologyChangeEvent event = new TopologyChangeEvent(
                this, TopologyChangeEvent.Type.NODE_UP, member.getNodeId(), isInitial);

        log.debug("Publishing {} (uptimeMs={}, gracePeriodMs={}, isInitial={})",
                event, uptimeMs, startupGracePeriodMs, isInitial);

        eventPublisher.publishEvent(event);
    }

    @Override
    public void nodeDown(long eventUID, String nodeID) {
        tracker.nodeDown(eventUID, nodeID);

        TopologyChangeEvent event = new TopologyChangeEvent(
                this, TopologyChangeEvent.Type.NODE_DOWN, nodeID, false);

        log.debug("Publishing {}", event);
        eventPublisher.publishEvent(event);
    }
}
