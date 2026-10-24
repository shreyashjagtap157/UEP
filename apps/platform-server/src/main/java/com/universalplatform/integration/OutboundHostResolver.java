package com.universalplatform.integration;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
interface OutboundHostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
}
