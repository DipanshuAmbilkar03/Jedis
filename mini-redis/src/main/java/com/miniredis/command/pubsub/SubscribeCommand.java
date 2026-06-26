package com.miniredis.command.pubsub;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.ClientHandler;

import java.io.IOException;
import java.util.List;

/**
 * SUBSCRIBE channel [channel ...]
 * Subscribes the client to one or more channels.
 * For each channel, sends a confirmation response directly to the client:
 *   ["subscribe", channelName, subscriptionCount]
 * Returns null since responses are sent inline.
 */
public class SubscribeCommand implements CommandHandler {

    private final PubSubManager pubSubManager;

    public SubscribeCommand(PubSubManager pubSubManager) {
        this.pubSubManager = pubSubManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.wrongArgCount("subscribe");
        }

        for (int i = 1; i < command.argCount(); i++) {
            String channel = command.getArg(i);
            int count = pubSubManager.subscribe(client, channel);

            String response = RespEncoder.encodeArray(List.of(
                    RespEncoder.encodeBulkString("subscribe"),
                    RespEncoder.encodeBulkString(channel),
                    RespEncoder.encodeInteger(count)
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
