package com.example.artemis.core.pool;

import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;

public class ProducerHolder {
    private final ClientSession session;
    private final ClientProducer producer;

    public ProducerHolder(ClientSession session, ClientProducer producer) {
        this.session = session;
        this.producer = producer;
    }

    public ClientSession getSession() {
        return session;
    }

    public ClientProducer getProducer() {
        return producer;
    }

    public void close() throws Exception {
        if (producer != null) {
            producer.close();
        } 
        if (session != null) {
            session.close();
        }
    }
}
