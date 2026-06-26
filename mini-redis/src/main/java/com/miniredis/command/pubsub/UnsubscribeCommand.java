package com.miniredis.command.pubsub;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.ClientHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * UNSUBSCRIBE [channel ...]
 * Unsubscribes the client from the specified channels, or all channels if none given.
 * For each channel, sends a confirmation response directly to the client:
 *   ["unsubscribe", channelName, remainingSubscriptionCount]
 * Returns null since responses are sent inline.
 */
public class UnsubscribeCommand implements CommandHandler {

    private final PubSubManager pubSubManager;

    public UnsubscribeCommand(PubSubManager pubSubManager) {
        this.pubSubManager = pubSubManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        List<String> channelsToUnsub;

        if (command.argCount() < 2) {
            // No channels specified — unsubscribe from all
            channelsToUnsub = new ArrayList<>(client.getSubscriptions());
        } else {
            channelsToUnsub = new ArrayList<>();
            for (int i = 1; i < command.argCount(); i++) {
                channelsToUnsub.add(command.getArg(i));
            }
        }

        // Handle edge case: no subscriptions at all
        if (channelsToUnsub.isEmpty()) {
            String response = RespEncoder.encodeArray(List.of(
                    RespEncoder.encodeBulkString("unsubscribe"),
                    RespEncoder.encodeNull(),
                    RespEncoder.encodeInteger(0)
            ));
            try {
                client.sendRawResponse(response);
            } catch (IOException e) {
                // Client disconnected
            }
            return null;
        }

        for (String channel : channelsToUnsub) {
            int remaining = pubSubManager.unsubscribe(client, channel);

            String response = RespEncoder.encodeArray(List.of(
                    RespEncoder.encodeBulkString("unsubscribe"),
                    RespEncoder.encodeBulkString(channel),
                    RespEncoder.encodeInteger(remaining)
            ));

            try {
                client.sendRawResponse(response);
            } catch (IOException e) {
                // Client disconnected — will be cleaned up
                break;
            }
        }

        return null; // Responses already sent directly
    }
}
