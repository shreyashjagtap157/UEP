package com.universalplatform.tenancy;

import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * REST controller for storage operations.
 * Handles file uploads to the configured storage provider (local, S3, Google Drive).
 */
@Component
@Path("/api/v1/storage")
public class StorageController {

    private final StorageAdapterRegistry adapterRegistry;

    public StorageController(StorageAdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    /** GET /api/v1/storage/provider - returns the currently configured storage provider */
    @GET
    @Path("/provider")
    @Produces(MediaType.APPLICATION_JSON)
    public StorageProviderType getConfiguredProvider() {
        // Determine the storage type from the platform config
        // This could be enhanced to read from Environment, but for now we return the default
        return StorageProviderType.LOCAL;
    }

    /** POST /api/v1/storage/upload - upload a file blob to the configured storage */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(
            @FormParam("key") String objectKey,
            @FormParam("contentType") String contentType,
            @FormParam("length") long length,
            @javax.ws.rs.core.MultivaluedMap<String, javax.ws.rs.core.FormDataBodyPart> formData) {

        // Get the uploaded file part
        javax.ws.rs.core.MultivaluedMap<String, javax.ws.rs.core.FormDataBodyPart> parts = formData;
        if (parts.isEmpty()) {
            return Response.status(400).entity("No file part provided").build();
        }

        // Get the first file part
        javax.ws.rs.core.FormDataBodyPart filePart = parts.firstEntry().getValue();
        InputStream fileInputStream = filePart.getValueAs(InputStream.class);

        if (fileInputStream == null) {
            return Response.status(400).entity("Empty file content").build();
        }

        // Get the adapter for the configured storage type
        StorageAdapter adapter = adapterRegistry.require(StorageProviderType.LOCAL);

        try {
            String locator = adapter.put(objectKey, fileInputStream, length, contentType);
            return Response.ok(new UploadResponse(locator, adapter.type())).build();
        } catch (Exception e) {
            return Response.status(500).entity("Storage upload failed: " + e.getMessage()).build();
        }
    }

    /** Simple response DTO for upload results */
    public static class UploadResponse {
        private final String locator;
        private final StorageProviderType provider;

        public UploadResponse(String locator, StorageProviderType provider) {
            this.locator = locator;
            this.provider = provider;
        }

        public String getLocator() {
            return locator;
        }

        public StorageProviderType getProvider() {
            return provider;
        }
    }
}