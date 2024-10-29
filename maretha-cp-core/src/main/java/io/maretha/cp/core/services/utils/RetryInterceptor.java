package io.maretha.cp.core.services.utils;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class RetryInterceptor implements Interceptor {
    private final int maxRetries;
    private final int retryDelayMillis;
    private final Log log = LogFactory.getLog(RetryInterceptor.class);

    public RetryInterceptor(int maxRetries, int retryDelayMillis) {
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        IOException exception = null;
        Response response = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                // Attempt the request
                response = chain.proceed(request);
                // If response is successful (2xx), return it
                if (response.isSuccessful()) {
                    return response;
                }
                // Log unsuccessful response if needed
            } catch (IOException e) {
                exception = e;
                log.error("HttpRequest failed:", e);
            }

            // Wait before retrying
            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new IOException("Interrupted while waiting to retry", e);
            }

            //TODO Try non-blocking thread
//            CompletableFuture.runAsync(() -> {
//                try {
//                    Thread.sleep(retryDelayMillis); // Sleep in a separate thread
//                    // Code to retry the operation goes here
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    throw new RuntimeException("Interrupted while waiting to retry", e);
//                }
//            });
        }

        // If we've exhausted all retries, throw the last exception encountered
        throw exception != null ? exception : new IOException("Request failed after retries");
    }
}