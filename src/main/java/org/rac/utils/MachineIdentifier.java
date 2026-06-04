package org.rac.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MachineIdentifier {

    private static final Map<String, String> MACHINE_USERS = Map.of(
            "ed2a2802-67b4-40a9-a82a-de1a226e13f8", "Abhishek",
            "00b12c29-29a5-4258-8c29-9c87fb9d1334", "Nisha",
            "62b2f9cc-8ab3-4826-ae70-0a293b6e7bc6", "Meena"
    );

    private static String cachedGuid;

    public static String getMachineGuid() {
        if (cachedGuid != null) return cachedGuid;
        try {
            Process p = new ProcessBuilder(
                    "reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            Matcher m = Pattern.compile("MachineGuid\\s+REG_SZ\\s+(\\S+)").matcher(out);
            cachedGuid = m.find() ? m.group(1).toLowerCase() : "unknown";
        } catch (Exception e) {
            cachedGuid = "unknown";
        }
        return cachedGuid;
    }

    public static String getUserName() {
        String guid = getMachineGuid();
        return MACHINE_USERS.getOrDefault(guid, "Other_" + guid);
    }
}
