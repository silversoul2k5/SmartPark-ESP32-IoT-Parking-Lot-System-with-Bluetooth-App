package de.kai_morich.simple_bluetooth_terminal;

class AiDecision {

    enum Action {
        OPEN_GATE,
        CLOSE_GATE,
        STATUS,
        NONE;

        static Action fromValue(String value) {
            if (value == null) {
                return NONE;
            }
            String normalized = value.trim().toUpperCase();
            for (Action action : values()) {
                if (action.name().equals(normalized)) {
                    return action;
                }
            }
            return NONE;
        }
    }

    private final Action action;
    private final String command;
    private final String reply;

    AiDecision(Action action, String command, String reply) {
        this.action = action;
        this.command = command;
        this.reply = reply;
    }

    Action getAction() {
        return action;
    }

    String getCommand() {
        return command;
    }

    String getReply() {
        return reply;
    }
}
