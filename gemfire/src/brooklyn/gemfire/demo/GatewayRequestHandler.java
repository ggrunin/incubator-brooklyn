package brooklyn.gemfire.demo;

import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.Random;

public class GatewayRequestHandler implements HttpHandler {

    private static final String ADDED_MESSAGE       = "Added gateway:%s";
    private static final String REMOVED_MESSAGE     = "Removed gateway:%s";
    private static final String NOT_REMOVED_MESSAGE = "Gateway %s not removed";

    private static final String ID_KEY="id";
    private static final String ENDPOINT_ID_KEY="endpointId";
    private static final String PORT_KEY="port";
    private static final String HOST_KEY="host";

    // For GatewayQueueAttributes - see com.gemstone.gemfire.cache.util.GatewayQueueAttributes
    private static final String DISK_STORE_NAME_KEY="diskStoreName"; //String
    private static final String MAX_QUEUE_MEMORY_KEY="maximumQueueMemory"; //int
    private static final String BATCH_SIZE_KEY="batchSize"; //int
    private static final String BATCH_TIME_INTERVAL_KEY="batchTimeInterval"; //int
    private static final String BATCH_CONFLATION_KEY="batchConflation"; //boolean
    private static final String ENABLE_PERSISTENCE_KEY="enablePersistence"; // boolean
    private static final String ALERT_THRESHOLD_KEY="alertThreshold"; //int

    private final GatewayChangeListener changeListener;

    public GatewayRequestHandler(GatewayChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    private static final String USAGE = "Example usage:\n" +
            "GET http://host:port/add?id=US&endpointId=US-1&host=localhost&port=44444 \n";

    public void handle(HttpExchange httpExchange) throws IOException {
        URI uri = httpExchange.getRequestURI();
        String path = uri.getPath();
        try {
            if(path.startsWith("/add")) handleAdd(httpExchange);
            else if( path.startsWith("/remove")) handleRemove(httpExchange);
            else handleUnknown(httpExchange);
        } catch(Throwable t) {
            sendResponse(httpExchange,500,t.getMessage());
            t.printStackTrace();
            throw new IOException("error on path:" +path, t);
        }
    }

    private void handleRemove(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);

        String id = (String)parameters.get(ID_KEY);
        boolean result = changeListener.gatewayRemoved(id);

        String message = result ? REMOVED_MESSAGE : NOT_REMOVED_MESSAGE;
        sendResponse(httpExchange,200,String.format(message,id));
    }

    private void handleAdd(HttpExchange httpExchange) throws IOException {
        String query = httpExchange.getRequestURI().getRawQuery();
        Map<String,Object> parameters = new ParameterParser().parse(query);

        String id = (String)parameters.get(ID_KEY);
        String endpointId = (String)parameters.get(ENDPOINT_ID_KEY);
        String host = (String)parameters.get(HOST_KEY);
        int port = Integer.parseInt((String)parameters.get(PORT_KEY));

        GatewayQueueAttributes attributes = getQueueAttributes(parameters);
        attributes.setOverflowDirectory( computeOverflowDirectory(endpointId) );

        changeListener.gatewayAdded(id, endpointId, host,  port, attributes);

        sendResponse(httpExchange,200,String.format(ADDED_MESSAGE,id));
    }

    private String computeOverflowDirectory(String endpointId) {
        return "overflow-"+endpointId+"-"+new Random().nextInt();
    }

    private void handleUnknown(HttpExchange httpExchange) throws IOException {
        sendResponse(httpExchange,404,USAGE);
    }

    private void sendResponse(HttpExchange httpExchange, int code, String message) throws IOException {
        Headers responseHeaders = httpExchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "text/plain");
        httpExchange.sendResponseHeaders(code, 0);
        OutputStream response = httpExchange.getResponseBody();
        response.write((message+"\n").getBytes());
        response.close();
    }

    private GatewayQueueAttributes getQueueAttributes(Map<String,Object> parameters) {
        GatewayQueueAttributes result = new GatewayQueueAttributes();

        String diskStoreName = (String)parameters.get(DISK_STORE_NAME_KEY);
        if (diskStoreName != null) result.setDiskStoreName(diskStoreName);

        Integer maxQueueMemory = getAsInteger(MAX_QUEUE_MEMORY_KEY, parameters);
        if (maxQueueMemory!= null) result.setMaximumQueueMemory(maxQueueMemory);

        Integer batchSize = getAsInteger(BATCH_SIZE_KEY, parameters);
        if (batchSize!=null) result.setBatchSize(batchSize);

        Integer batchTimeInterval = getAsInteger(BATCH_TIME_INTERVAL_KEY, parameters);
        if (batchTimeInterval!=null) result.setBatchTimeInterval(batchTimeInterval);

        Integer alertThreshHold = getAsInteger(ALERT_THRESHOLD_KEY, parameters);
        if (alertThreshHold!=null) result.setAlertThreshold(alertThreshHold);

        Boolean batchConflation = getAsBoolean(BATCH_CONFLATION_KEY,parameters);
        if (batchConflation!=null) result.setBatchConflation(batchConflation);

        Boolean enablePersistence = getAsBoolean(ENABLE_PERSISTENCE_KEY,parameters);
        if(enablePersistence!=null) result.setEnablePersistence(enablePersistence);

        return result;
    }

    private Integer getAsInteger(String key, Map<String, Object> params) {
        String asString = (String)params.get(key);
        if (asString == null) return null;
        return Integer.parseInt(asString);
    }

    private Boolean getAsBoolean(String key, Map<String,Object> params) {
        String asString = (String)params.get(key);
        if (asString == null) return null;
        return Boolean.parseBoolean(asString);
    }
}
