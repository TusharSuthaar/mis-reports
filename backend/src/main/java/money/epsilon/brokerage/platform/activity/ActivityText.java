package money.epsilon.brokerage.platform.activity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit events that belong on the Activity screen, in plain words. Only operational steps are listed (saving,
 * locking, exporting…); everyday master edits stay on the Audit log screen. Work done by a background job is
 * shown from the job itself (its title comes from the job handler), so it is not repeated here.
 */
final class ActivityText {

    /** "ENTITY:ACTION" → what happened. */
    static final Map<String, String> EVENTS = new LinkedHashMap<>();

    static {
        EVENTS.put("UPLOAD:COMMIT", "Saved an uploaded brokerage file into its month");
        EVENTS.put("UPLOAD:COMMIT_CORRECTION", "Saved corrections into the open month");
        EVENTS.put("UPLOAD:DISCARD", "Discarded an uploaded brokerage file");
        EVENTS.put("PERIOD:LOCK", "Locked a month");
        EVENTS.put("PERIOD:UNLOCK", "Unlocked a month");
        EVENTS.put("END_FILE:EXPORT", "Downloaded the END file");
        EVENTS.put("FINESSE_CLIENT_MASTER:LOAD", "Uploaded the Finesse client master");
        EVENTS.put("PARTNER:MERGE", "Merged two partners");
        EVENTS.put("CLIENT:PAN_CHANGE", "Changed a client's PAN");
        EVENTS.put("USER:PASSWORD_RESET", "Reset a user's password");
    }

    private ActivityText() {
    }
}
