package com.miniredis.command.pubsub;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.ClientHandler;

/**
 * PUBLISH channel message
 * Posts a message to the given channel.
 * Returns the number of clients that received the message.
 */
public class PublishCommand implements CommandHandler {

    private final PubSubManager pubSubManager;

    public PublishCommand(PubSubManager pubSubManager) {
        this.pubSubManager = pubSubManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 3) {
            return RespEncoder.wrongArgCount("publish");
        }

        String channel = command.getArg(1);
        String message = command.getArg(2);

        int receivers = pubSubManager.publish(channel, message);
        return RespEncoder.encodeInteger(receivers);
    }
}
