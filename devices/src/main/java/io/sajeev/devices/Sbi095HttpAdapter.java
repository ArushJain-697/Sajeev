// #36 (WS2) | Bible §6.1 | SBI 0.9.5 over HTTP. Wraps the SBI 1.0 wire code and accepts only devices that report
// serviceVersion 0.9.5. Java 11 only. The client's own 0.9.5 providers (registration-client fork) are not wrapped here
// yet; that is a WS2 task (#40+). Tested against MockMDS 0.9.5 responses recorded in testdata/recorded-095.
package io.sajeev.devices;

import com.fasterxml.jackson.databind.JsonNode;

/** SBI 0.9.5 adapter: same HTTP methods as 1.0 in this wire format, filtered to devices speaking 0.9.5. */
public final class Sbi095HttpAdapter extends Sbi10HttpAdapter {

    /**
     * @param host host to scan
     * @param minPort first port
     * @param maxPort last port
     * @param timeoutMs socket timeout in ms
     */
    public Sbi095HttpAdapter(String host, int minPort, int maxPort, int timeoutMs) {
        super(host, minPort, maxPort, timeoutMs);
    }

    @Override
    protected boolean acceptsDevice(JsonNode d) {
        return d.path("serviceVersion").asText().startsWith("0.9.5");
    }
}
