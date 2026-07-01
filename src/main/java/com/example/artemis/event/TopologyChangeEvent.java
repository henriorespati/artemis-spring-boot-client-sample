package com.example.artemis.event;

import org.springframework.context.ApplicationEvent;

/** Published when the Artemis cluster topology changes (node joins or leaves). */
public class TopologyChangeEvent extends ApplicationEvent {

    public enum Type { NODE_UP, NODE_DOWN }

    private final Type    type;
    private final String  nodeId;
    private final boolean initialBroadcast;

    public TopologyChangeEvent(Object source, Type type, String nodeId, boolean initialBroadcast) {
        super(source);
        this.type             = type;
        this.nodeId           = nodeId;
        this.initialBroadcast = initialBroadcast;
    }

    public Type    getType()            { return type; }
    public String  getNodeId()          { return nodeId; }
    public boolean isInitialBroadcast() { return initialBroadcast; }

    @Override
    public String toString() {
        return "TopologyChangeEvent{type=" + type +
               ", nodeId='" + nodeId + '\'' +
               ", initialBroadcast=" + initialBroadcast + '}';
    }
}
