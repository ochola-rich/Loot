package com.loot.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Minimal IPv4/IPv6 CIDR membership check - no extra dependency needed for one comparison. */
final class CidrMatcher {

    private CidrMatcher() {
    }

    static boolean matches(String cidr, String ip) {
        try {
            String[] parts = cidr.split("/", 2);
            InetAddress cidrAddress = InetAddress.getByName(parts[0]);
            InetAddress targetAddress = InetAddress.getByName(ip);

            byte[] cidrBytes = cidrAddress.getAddress();
            byte[] targetBytes = targetAddress.getAddress();
            if (cidrBytes.length != targetBytes.length) {
                return false;
            }

            int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : cidrBytes.length * 8;
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != targetBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                if ((cidrBytes[fullBytes] & mask) != (targetBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
