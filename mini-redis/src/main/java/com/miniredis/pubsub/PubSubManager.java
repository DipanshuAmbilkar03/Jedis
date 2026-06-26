package com.miniredis.pubsub;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Pub/Sub channels and message delivery.
 * 
 * Maintains a map of channel name → set of subscribed clients.
 * Messages are fire-and-forget: undelivered messages are lost.
 */
public class PubSubManager {

    /** channel name → set of subscribed client handlers */
    private final ConcurrentHashMap<String, Set<ClientHandler>> channels;

    public PubSubManager() {
        this.channels = new ConcurrentHashMap<>();
    }

    /**
     * Subscribe a client to a channel.
     * Returns the total number of subscriptions for this client.
     */
    public int subscribe(ClientHandler client, String channel) {
        channels.computeIfAbsent(channel, k ->
                Collections.synchronizedSet(new LinkedHashSet<>())
        ).add(client);

        client.addSubscription(channel);
        return client.getSubscriptionCount();
    }

    /**
     * Unsubscribe a client from a channel.
     * Returns the total number of remaining subscriptions.
     */
    public int unsubscribe(ClientHandler client, String channel) {
        Set<ClientHandler> subscribers = channels.get(channel);
        if (subscribers != null) {
            subscribers.remove(client);
            if (subscribers.isEmpty()) {
                channels.remove(channel);
            }
        }

        client.removeSubscription(channel);
        return client.getSubscriptionCount();
    }

    /**
     * Unsubscribe a client from all channels.
     */
    public void unsubscribeAll(ClientHandler client) {
        Set<String> subs = new HashSet<>(client.getSubscriptions());
        for (String channel : subs) {
            unsubscribe(client, channel);
        }
    }

    /**
     * Publish a message to a channel.
     * Delivers to all subscribers. Returns the number of clients that received it.
     */
    public int publish(String channel, String message) {
        Set<ClientHandler> subscribers = channels.get(channel);
        if (subscribers == null || subscribers.isEmpty()) {
            return 0;
        }

        // Build the RESP2 message: ["message", channel, message]
        String resp = RespEncoder.encodeArray(List.of(
                RespEncoder.encodeBulkString("message"),
                RespEncoder.encodeBulkString(channel),
                RespEncoder.encodeBulkString(message)
        ));

        int delivered = 0;
        for (ClientHandler client : subscribers) {
            try {
                client.sendRawResponse(resp);
                delivered++;
            } catch (Exception e) {
                // Client disconnected — will be cleaned up
                System.err.println("[PubSub] Failed to deliver to client: " + e.getMessage());
            }
        }
        return delivered;
    }

    /**
     * Get the number of subscribers on a channel.
     */
    public int subscriberCount(String channel) {
        Set<ClientHandler> subscribers = channels.get(channel);
        return subscribers != null ? subscribers.size() : 0;
    }
}
