package org.nuxeo.ecm.restapi.server.jaxrs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.stream.Stream;

@WebObject(type = "interactions")
@Produces(MediaType.APPLICATION_JSON)
public class StreamController extends DefaultObject {

    private static final Logger log = LogManager.getLogger(StreamController.class);

    @GET
    @Path("/stream")
    @Produces("text/event-stream") // Specify SSE Content-Type
    public Response getStream() {
        StreamingOutput output = os -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(os))) {
                Stream.of("data1", "data2", "data3")
                        .forEach(data -> {
                            try {
                                writer.write("data: " + data + "\n\n");
                                writer.flush();
                                Thread.sleep(100); // Simulate delay
                            } catch (IOException | InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            }
        };

        return Response.ok(output)
                .header("Cache-Control", "no-cache") // Ensure no caching
                .header("Connection", "keep-alive") // Keep connection open
                .build();
    }
}
