package com.example.ldcloud.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

// AWS S3 specific imports
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request; // For S3 listing
import com.amazonaws.services.s3.model.ListObjectsV2Result; // For S3 listing
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary; // For S3 listing
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat; // For formatting dates
import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // For date formatting

public class InternetArchiveService {

    private static final String TAG = "InternetArchiveService";
    private static final String SHARED_PREFS_NAME = "LdCloudSettings";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";

    private String accessKey;
    private String secretKey;
    private boolean credentialsLoaded = false;
    // private CredentialStore credentialStore; // REMOVED - No longer using archive-sdk
    // private HttpIAMetadataSource metadataSource; // REMOVED - No longer using archive-sdk
    private AmazonS3Client s3Client; // AWS S3 Client


    public InternetArchiveService(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        this.accessKey = sharedPreferences.getString(KEY_ACCESS_KEY, null);
        this.secretKey = sharedPreferences.getString(KEY_SECRET_KEY, null);

        // REMOVED HttpIAMetadataSource initialization

        if (this.accessKey != null && !this.accessKey.isEmpty() && this.secretKey != null && !this.secretKey.isEmpty()) {
            this.credentialsLoaded = true;
            Log.i(TAG, "Credentials loaded successfully for S3 client.");

            // REMOVED archive-sdk credential store initialization

            // Initialize AWS S3 Client
            BasicAWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
            this.s3Client = new AmazonS3Client(credentials);
            try {
                this.s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
                this.s3Client.setEndpoint("s3.us.archive.org");
                Log.i(TAG, "AmazonS3Client initialized with IA endpoint and region.");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing AmazonS3Client: " + e.getMessage(), e);
                this.s3Client = null;
                this.credentialsLoaded = false;
            }
        } else {
            this.credentialsLoaded = false;
            Log.w(TAG, "Credentials not found or incomplete in SharedPreferences. S3 client not initialized.");
        }
    }

    public boolean areCredentialsLoaded() {
        return credentialsLoaded;
    }

    /**
     * Attempts to fetch metadata for a given itemTitle to test the connection.
     *
     * @param itemTitle The identifier of the item on Internet Archive.
     * @return true if metadata was successfully fetched, false otherwise.
     */
    public boolean testConnection(String itemTitle) {
        if (!credentialsLoaded) {
            Log.e(TAG, "testConnection: Credentials are not loaded. Cannot test connection.");
            return false;
        }
        if (itemTitle == null || itemTitle.isEmpty()) {
            Log.e(TAG, "testConnection: Item title is null or empty.");
            return false;
        }

        if (this.metadataSource == null) {
            Log.e(TAG, "testConnection: MetadataSource is not initialized.");
            return false;
        }

        try {
            Log.i(TAG, "Attempting to fetch metadata for item: " + itemTitle);
            // The HttpIAMetadataSource.get() method typically fetches metadata.
            Metadata metadata = metadataSource.get(itemTitle); // This should return an Item object

            if (metadata instanceof Item) {
                Item item = (Item) metadata;
                if (item.getIdentifier().equals(itemTitle)) {
                    Log.i(TAG, "Successfully fetched metadata for item: " + itemTitle);
                    Log.i(TAG, "Title: " + item.getTitle());
                    Log.i(TAG, "Creator: " + item.getCreator());
                    Log.i(TAG, "Date: " + item.getDate());
                    // Check if files are directly available
                    if (item.getFiles() != null && !item.getFiles().isEmpty()) {
                         Log.i(TAG, "File count from metadata: " + item.getFiles().size());
                    }
                    return true;
                } else {
                    Log.w(TAG, "Fetched metadata, but identifier does not match. Item: " + itemTitle + ", Fetched ID: " + item.getIdentifier());
                    return false;
                }
            } else if (metadata != null) {
                 Log.w(TAG, "Fetched metadata, but it's not an Item instance for item: " + itemTitle);
                 return false;
            } else {
                Log.w(TAG, "Failed to fetch metadata for item: " + itemTitle + ". Metadata (Item) object is null.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error testing connection to Internet Archive for item " + itemTitle, e);
            return false;
        }
    }

    // Example of how one might list items, if the SDK provides such a client directly.
    // This is speculative as AccessControlClient might be for different purposes.
    public void listUserItems() {
        if (!credentialsLoaded) {
            Log.e(TAG, "Cannot list items: Credentials not loaded.");
            return;
        }
        // This is a guess. The SDK's structure for listing items owned by a user
        // (which would require authentication) is not immediately obvious from class names alone.
        // AccessControlClient might be related to permissions on items rather than listing them.
        // Typically, listing S3 bucket contents (if IA items are treated as such by the SDK)
        // would use an S3 client.
        Log.i(TAG, "Listing items functionality is not yet implemented or SDK path is unclear.");

        // Example using AccessControlClient if it were for listing (unlikely for general item listing)
        /*
        try {
            AccessControlClient acClient = new AccessControlClient(accessKey, secretKey);
            AccessControlQuery query = new AccessControlQuery();
            // query.setOwner(accessKey); // or some user identifier
            // query.setCollection("my_collection"); // Example
            AccessControlResponse response = acClient.execute(query);
            if (response != null && response.getRecords() != null) {
                for (ArchiveRecord record : response.getRecords()) {
                    Log.i(TAG, "Item: " + record.getHeader().getUrl());
                }
            } else {
                Log.w(TAG, "No items found or error in response.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing items", e);
        }
        */
    }

    /**
     * Lists files and folders within a given Internet Archive item.
     *
     * @param itemTitle The identifier of the item.
     * @return A list of ArchiveFile objects, or an empty list if an error occurs or no files.
     */
    public List<ArchiveFile> listFilesAndFolders(String itemTitle) {
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        if (!credentialsLoaded && metadataSource == null) { // Check if metadataSource is usable even for public items
             Log.w(TAG, "listFilesAndFolders: Credentials not loaded and/or metadata source not initialized. Cannot list files.");
             // Allow listing for public items even if keys are not set, if HttpIAMetadataSource supports it.
             // Re-check initialization of metadataSource if it's null here.
            if (metadataSource == null) {
                 this.metadataSource = new HttpIAMetadataSource(new XmlMetadataParser());
                 Log.i(TAG, "Re-initialized metadataSource for public item listing.");
            }
        }

        if (itemTitle == null || itemTitle.isEmpty()) {
            Log.e(TAG, "listFilesAndFolders: Item title is null or empty.");
            return archiveFiles; // Return empty list
        }

        try {
            Log.i(TAG, "Attempting to list files for item: " + itemTitle);
            Metadata metadata = metadataSource.get(itemTitle);

            if (metadata instanceof Item) {
                Item item = (Item) metadata;
                if (item.getFiles() != null && !item.getFiles().isEmpty()) {
                    // The getFiles() method in the SDK's Item class returns a Map<String, File>.
                    // The key is usually the filename.
                    for (Map.Entry<String, File> entry : item.getFiles().entrySet()) {
                        File iaFile = entry.getValue();
                        String name = iaFile.getName();
                        if (name == null || name.isEmpty()) {
                            name = entry.getKey(); // Use map key if File.getName() is null
                        }
                        // Heuristic for directory: IA items often don't explicitly mark directories in this metadata structure.
                        // Files ending with "/" or having specific "format" like "Directory" could be hints.
                        // For IA, items are often flat, or directories are implied by path names.
                        // We will assume all listed files are files unless specific metadata indicates otherwise.
                        // The SDK's File object might have a format property.
                        boolean isDirectory = "Collection".equals(iaFile.getFormat()) || "Item".equals(iaFile.getFormat());
                        if (name.endsWith("/") && !isDirectory) { // Another heuristic
                            isDirectory = true;
                            name = name.substring(0, name.length() -1);
                        }

                        String size = iaFile.getSize() != null ? iaFile.getSize() : "N/A";
                        // Attempt to parse mtime as long if it's a Unix timestamp string
                        String lastModifiedDateStr = "N/A";
                        if (iaFile.getMtime() != null) {
                            try {
                                // Assuming mtime is a unix timestamp in seconds
                                long mtimeUnix = Long.parseLong(iaFile.getMtime());
                                java.util.Date date = new java.util.Date(mtimeUnix * 1000L);
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                                lastModifiedDateStr = sdf.format(date);
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Could not parse mtime: " + iaFile.getMtime());
                                lastModifiedDateStr = iaFile.getMtime(); // Use raw string if not parsable
                            }
                        }

                        archiveFiles.add(new ArchiveFile(name, isDirectory, size, lastModifiedDateStr));
                        Log.d(TAG, "Added file: " + name + ", isDir: " + isDirectory + ", Size: " + size + ", Modified: " + lastModifiedDateStr);
                    }
                    Log.i(TAG, "Successfully listed " + archiveFiles.size() + " files/folders for item: " + itemTitle);
                } else {
                    Log.i(TAG, "No files found in metadata for item: " + itemTitle);
                }
            } else {
                Log.w(TAG, "Could not retrieve item metadata or it's not an Item instance for: " + itemTitle);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing files for item " + itemTitle, e);
            // Optionally, throw a custom exception here
        }
        return archiveFiles;
    }

    /**
     * Attempts to upload a file to the specified Internet Archive item.
     * NOTE: The current archive-sdk (3.0.3) primarily focuses on metadata and archive file access,
     * Uses AWS S3 SDK to upload a file to Internet Archive's S3-compatible storage.
     *
     * @param itemTitleAsBucketName The IA item identifier, used as the S3 bucket name.
     * @param localFilePath         The local path of the file to upload.
     * @param remoteFileNameInItemAsKey The desired name for the file within the IA item (S3 object key).
     * @return true if upload was successful, false otherwise.
     */
    public boolean uploadFile(String itemTitleAsBucketName, String localFilePath, String remoteFileNameInItemAsKey) {
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "uploadFile: Credentials are not loaded or S3 client not initialized. Cannot upload file.");
            return false;
        }
        if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty() ||
            localFilePath == null || localFilePath.isEmpty() ||
            remoteFileNameInItemAsKey == null || remoteFileNameInItemAsKey.isEmpty()) {
            Log.e(TAG, "uploadFile: Missing itemTitle (bucket), localFilePath, or remoteFileName (key).");
            return false;
        }

        File fileToUpload = new File(localFilePath);
        if (!fileToUpload.exists()) {
            Log.e(TAG, "uploadFile: Local file does not exist at path: " + localFilePath);
            return false;
        }

        try {
            Log.i(TAG, "Starting S3 upload: " + localFilePath + " to bucket: " + itemTitleAsBucketName + " as key: " + remoteFileNameInItemAsKey);

            // IA S3 headers for access/secret key. These are non-standard S3 headers.
            // AWS SDK normally uses Authorization header. For IA, custom headers are needed.
            // This is typically done by constructing the request with specific metadata/headers.
            // However, the standard PutObjectRequest might not directly support this for IA's S3.
            // IA's S3 documentation states:
            // "The s3 C SDK and Boto library know how to generate this header for you.
            // Other libraries may require you to set it yourself, as a header like:
            // authorization: LOW your_access_key:your_secret_key"
            // The AWS SDK for Android should handle signing, but it must be for the correct endpoint & region.
            // If it fails, it might be due to how AWS SDK signs vs. what IA expects.

            // A common issue is that IA item identifiers can contain characters not allowed in S3 bucket names.
            // This needs to be handled if itemTitleAsBucketName is directly from user input for general items.
            // For controlled item names, it might be okay.

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    itemTitleAsBucketName,
                    remoteFileNameInItemAsKey,
                    fileToUpload
            );

            // If IA requires specific "x-archive-meta-*" headers for collection, etc., they'd be added here:
            // ObjectMetadata metadata = new ObjectMetadata();
            // metadata.addUserMetadata("x-archive-meta-collection", "your_collection");
            // putObjectRequest.setMetadata(metadata);

            s3Client.putObject(putObjectRequest);
            Log.i(TAG, "S3 Upload successful for: " + remoteFileNameInItemAsKey);
            return true;

        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException during S3 upload: " + e.getMessage(), e);
            Log.e(TAG, "AWS Error Code: " + e.getErrorCode());
            Log.e(TAG, "AWS Error Type: " + e.getErrorType());
            Log.e(TAG, "AWS Request ID: " + e.getRequestId());
        } catch (SdkClientException e) {
            Log.e(TAG, "SdkClientException during S3 upload: " + e.getMessage(), e);
        } catch (Exception e) { // Catch any other unexpected errors
            Log.e(TAG, "Unexpected exception during S3 upload: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Downloads a file from Internet Archive's S3-compatible storage.
     *
     * @param itemTitleAsBucketName     The IA item identifier, used as the S3 bucket name.
     * @param remoteFileNameInItemAsKey The S3 object key (name of the file in the item).
     * @param localTargetFile           The local java.io.File object where the downloaded file will be saved.
     * @return true if download was successful, false otherwise.
     */
    public boolean downloadFile(String itemTitleAsBucketName, String remoteFileNameInItemAsKey, File localTargetFile) {
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "downloadFile: Credentials are not loaded or S3 client not initialized. Cannot download file.");
            return false;
        }
        if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty() ||
            remoteFileNameInItemAsKey == null || remoteFileNameInItemAsKey.isEmpty() ||
            localTargetFile == null) {
            Log.e(TAG, "downloadFile: Missing itemTitle (bucket), remoteFileName (key), or localTargetFile.");
            return false;
        }

        try {
            Log.i(TAG, "Starting S3 download for key: " + remoteFileNameInItemAsKey + " from bucket: " + itemTitleAsBucketName + " to " + localTargetFile.getAbsolutePath());

            GetObjectRequest getObjectRequest = new GetObjectRequest(itemTitleAsBucketName, remoteFileNameInItemAsKey);
            s3Client.getObject(getObjectRequest, localTargetFile); // This downloads the file to the specified local file

            Log.i(TAG, "S3 Download successful to: " + localTargetFile.getAbsolutePath());
            return true;

        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException during S3 download: " + e.getMessage(), e);
            Log.e(TAG, "AWS Error Code: " + e.getErrorCode());
            Log.e(TAG, "AWS Error Type: " + e.getErrorType());
            Log.e(TAG, "AWS Request ID: " + e.getRequestId());
            // Attempt to delete partially downloaded file on error
            if (localTargetFile.exists()) {
                localTargetFile.delete();
            }
        } catch (SdkClientException e) {
            Log.e(TAG, "SdkClientException during S3 download: " + e.getMessage(), e);
            if (localTargetFile.exists()) {
                localTargetFile.delete();
            }
        } catch (Exception e) { // Catch any other unexpected errors
            Log.e(TAG, "Unexpected exception during S3 download: " + e.getMessage(), e);
            if (localTargetFile.exists()) {
                localTargetFile.delete();
            }
        }
        return false;
    }

    /**
     * Creates a "folder" in Internet Archive's S3-compatible storage.
     * Folders in S3 are typically zero-byte objects with a key ending in "/".
     *
     * @param itemTitleAsBucketName The IA item identifier, used as the S3 bucket name.
     * @param folderNameAsKey       The desired name for the folder (S3 object key).
     * @return true if folder creation was successful, false otherwise.
     */
    public boolean createFolder(String itemTitleAsBucketName, String folderNameAsKey) {
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "createFolder: Credentials are not loaded or S3 client not initialized. Cannot create folder.");
            return false;
        }
        if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty() ||
            folderNameAsKey == null || folderNameAsKey.isEmpty()) {
            Log.e(TAG, "createFolder: Missing itemTitle (bucket) or folderName (key).");
            return false;
        }

        // Ensure folder name ends with a slash
        String formattedFolderName = folderNameAsKey.endsWith("/") ? folderNameAsKey : folderNameAsKey + "/";

        try {
            Log.i(TAG, "Attempting to create folder: " + formattedFolderName + " in bucket: " + itemTitleAsBucketName);

            InputStream emptyInputStream = new ByteArrayInputStream(new byte[0]);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(0);

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    itemTitleAsBucketName,
                    formattedFolderName,
                    emptyInputStream,
                    metadata
            );

            s3Client.putObject(putObjectRequest);
            Log.i(TAG, "Folder created successfully: " + formattedFolderName);
            return true;

        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException during folder creation: " + e.getMessage(), e);
        } catch (SdkClientException e) {
            Log.e(TAG, "SdkClientException during folder creation: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during folder creation: " + e.getMessage(), e);
        }
        return false;
    }
}
