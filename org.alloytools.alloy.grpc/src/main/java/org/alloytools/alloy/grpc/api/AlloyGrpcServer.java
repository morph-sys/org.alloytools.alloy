package org.alloytools.alloy.grpc.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.alloytools.alloy.grpc.impl.AlloySolverServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;

/**
 * gRPC server for Alloy model solving.
 */
public class AlloyGrpcServer {
    
    private static final Logger logger = LoggerFactory.getLogger(AlloyGrpcServer.class);
    
    private final int port;
    private final Server server;
    private final HealthStatusManager healthStatusManager;

    /**
     * Create a new Alloy gRPC server.
     * 
     * @param port The port to listen on
     */
    public AlloyGrpcServer(int port) {
        this.port = port;
        this.healthStatusManager = new HealthStatusManager();
        
        this.server = ServerBuilder.forPort(port)
            .addService(new AlloySolverServiceImpl())
            .addService(healthStatusManager.getHealthService())
            .addService(ProtoReflectionService.newInstance())
            .build();
    }

    /**
     * Start the server.
     * 
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        server.start();
        healthStatusManager.setStatus("", ServingStatus.SERVING);
        logger.info("Alloy gRPC server started on port {}", port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Alloy gRPC server...");
            try {
                AlloyGrpcServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Stop the server.
     * 
     * @throws InterruptedException if interrupted while waiting for shutdown
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            healthStatusManager.setStatus("", ServingStatus.NOT_SERVING);
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("Alloy gRPC server stopped");
        }
    }

    /**
     * Block until the server shuts down.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Get the port the server is listening on.
     * 
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Check if the server is running.
     * 
     * @return true if the server is running
     */
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /**
     * Get the underlying gRPC server instance.
     * 
     * @return the gRPC server
     */
    public Server getServer() {
        return server;
    }

    /**
     * Main method for running the server standalone.
     * 
     * @param args command line arguments (optional port number)
     */
    public static void main(String[] args) {
        int port = 50051; // Default port
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}", args[0]);
                System.exit(1);
            }
        }

        AlloyGrpcServer server = new AlloyGrpcServer(port);
        try {
            server.start();
            logger.info("Alloy gRPC server is running on port {}", port);
            server.blockUntilShutdown();
        } catch (IOException e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("Server interrupted", e);
            Thread.currentThread().interrupt();
            System.exit(1);
        }
    }
}
