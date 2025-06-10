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
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonClientException; // Replaced SdkClientException

// Standard Java imports
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InternetArchiveService {

    private static final String TAG = "InternetArchiveService";
    private static final String SHARED_PREFS_NAME = "LdCloudSettings";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";

    private String accessKey;
    private String secretKey;
    private boolean credentialsLoaded = false;
    private AmazonS3Client s3Client;

    public InternetArchiveService(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        this.accessKey = sharedPreferences.getString(KEY_ACCESS_KEY, null);
        this.secretKey = sharedPreferences.getString(KEY_SECRET_KEY, null);

        if (this.accessKey != null && !this.accessKey.isEmpty() && this.secretKey != null && !this.secretKey.isEmpty()) {
            try {
                BasicAWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
                this.s3Client = new AmazonS3Client(credentials);
                this.s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
                this.s3Client.setEndpoint("s3.us.archive.org");
                this.credentialsLoaded = true;
                Log.i(TAG, "AmazonS3Client initialized successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing AmazonS3Client: " + e.getMessage(), e);
                this.s3Client = null;
                this.credentialsLoaded = false;
            }
        } else {
            this.credentialsLoaded = false;
            Log.w(TAG, "Credentials not found or incomplete. S3 client not initialized.");
        }
    }

    public boolean areCredentialsLoaded() {
        return credentialsLoaded;
    }

    /**
     * Tests the S3 connection and credentials by attempting to list the first object in the bucket.
     *
     * @param itemTitleAsBucketName The identifier of the item (bucket) on Internet Archive.
     * @return true if the connection and credentials seem valid, false otherwise.
     */
    public boolean testConnection(String itemTitleAsBucketName) {
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "testConnection: Credentials are not loaded or S3 client not initialized.");
            return false;
        }
        if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty()) {
            Log.e(TAG, "testConnection: Item title (bucket name) is null or empty.");
            return false;
        }

        try {
            Log.i(TAG, "Attempting S3 connection test for bucket: " + itemTitleAsBucketName);
            s3Client.listObjectsV2(new ListObjectsV2Request().withBucketName(itemTitleAsBucketName).withMaxKeys(1));
            Log.i(TAG, "S3 connection test successful for bucket: " + itemTitleAsBucketName);
            return true;
        } catch (AmazonServiceException e) {
            Log.e(TAG, "S3 Connection Test Failed (AmazonServiceException): " + e.getMessage(), e);
        } catch (AmazonClientException e) { // Replaced SdkClientException
            Log.e(TAG, "S3 Connection Test Failed (AmazonClientException): " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "S3 Connection Test Failed (Exception): " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Lists files and folders within a given Internet Archive item using S3.
     * This version supports listing contents of a specific path (prefix) within the bucket.
     *
     * @param itemTitleAsBucketName The identifier of the item (S3 bucket).
     * @param currentPath           The current path (prefix) within the bucket to list. Use "" or null for root.
     * @return A list of ArchiveFile objects, or an empty list if an error occurs or no files.
     */
    public List<ArchiveFile> listFilesAndFolders(String itemTitleAsBucketName, String currentPath) {
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        if (!credentialsLoaded || s3Client == null) {
            Log.w(TAG, "listFilesAndFolders: Credentials not loaded or S3 client not initialized for bucket: " + itemTitleAsBucketName);
            return archiveFiles;
        }

        if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty()) {
            Log.e(TAG, "listFilesAndFolders: Item title (bucket name) is null or empty.");
            return archiveFiles;
        }

        String prefix = (currentPath == null || currentPath.isEmpty()) ? "" : (currentPath.endsWith("/") ? currentPath : currentPath + "/");

        try {
            Log.i(TAG, "Listing S3 objects for bucket: " + itemTitleAsBucketName + ", Prefix: '" + prefix + "'");
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(itemTitleAsBucketName)
                    .withPrefix(prefix)
                    .withDelimiter("/");

            ListObjectsV2Result result;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            do {
                result = s3Client.listObjectsV2(request);

                if (result.getCommonPrefixes() != null) {
                    for (String commonPrefix : result.getCommonPrefixes()) {
                        if (commonPrefix.equals(prefix)) continue;
                        String folderName = commonPrefix.substring(prefix.length());
                        if (folderName.endsWith("/")) {
                            folderName = folderName.substring(0, folderName.length() - 1);
                        }
                        if (folderName.isEmpty()) continue;
                        archiveFiles.add(new ArchiveFile(folderName, true, "DIR", "N/A"));
                        Log.d(TAG, "S3 CommonPrefix (Folder): " + folderName);
                    }
                }

                if (result.getObjectSummaries() != null) {
                    for (S3ObjectSummary summary : result.getObjectSummaries()) {
                        String key = summary.getKey();
                        if (key.equals(prefix)) continue;

                        String name = key.substring(prefix.length());
                        if (name.isEmpty() || name.equals("/")) continue;

                        boolean isDirectory = key.endsWith("/");

                        if (isDirectory) {
                            name = name.substring(0, name.length() - 1);
                            if (name.isEmpty()) continue;

                            boolean alreadyAdded = false;
                            for(ArchiveFile af : archiveFiles) {
                                if(af.isDirectory() && af.getName().equals(name)) {
                                    alreadyAdded = true;
                                    break;
                                }
                            }
                            if(alreadyAdded) continue;
                        }

                        String sizeStr = isDirectory ? "DIR" : String.valueOf(summary.getSize());
                        String lastModifiedStr = summary.getLastModified() != null ? sdf.format(summary.getLastModified()) : "N/A";

                        archiveFiles.add(new ArchiveFile(name, isDirectory, sizeStr, lastModifiedStr));
                        Log.d(TAG, "S3 Object (File/ExplicitFolder): " + name + ", IsDir=" + isDirectory + ", Size=" + sizeStr);
                    }
                }
                request.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());

            Log.i(TAG, "Successfully listed " + archiveFiles.size() + " S3 objects for bucket: " + itemTitleAsBucketName + ", Path: " + prefix);

        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException listing S3 objects: " + e.getMessage(), e);
        } catch (AmazonClientException e) { // Replaced SdkClientException
            Log.e(TAG, "AmazonClientException listing S3 objects: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception listing S3 objects: " + e.getMessage(), e);
        }
        return archiveFiles;
    }

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
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    itemTitleAsBucketName,
                    remoteFileNameInItemAsKey,
                    fileToUpload
            );
            s3Client.putObject(putObjectRequest);
            Log.i(TAG, "S3 Upload successful for: " + remoteFileNameInItemAsKey);
            return true;
        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException during S3 upload: " + e.getMessage(), e);
        } catch (AmazonClientException e) { // Replaced SdkClientException
            Log.e(TAG, "AmazonClientException during S3 upload: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during S3 upload: " + e.getMessage(), e);
        }
        return false;
    }

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
            s3Client.getObject(getObjectRequest, localTargetFile);
            Log.i(TAG, "S3 Download successful to: " + localTargetFile.getAbsolutePath());
            return true;
        } catch (AmazonServiceException e) {
            Log.e(TAG, "AmazonServiceException during S3 download: " + e.getMessage(), e);
            if (localTargetFile.exists()) { localTargetFile.delete(); }
        } catch (AmazonClientException e) { // Replaced SdkClientException
            Log.e(TAG, "AmazonClientException during S3 download: " + e.getMessage(), e);
            if (localTargetFile.exists()) { localTargetFile.delete(); }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during S3 download: " + e.getMessage(), e);
            if (localTargetFile.exists()) { localTargetFile.delete(); }
        }
        return false;
    }

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
