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
// Removed: import com.amazonaws.AmazonServiceException;
// Removed: import com.amazonaws.AmazonClientException;

// Standard Java imports
import org.json.JSONObject; // Added
import org.json.JSONArray;  // Added
import org.json.JSONException; // Added
import java.io.IOException;    // Added

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
    private boolean credentialsLoaded = false; // Primarily for S3 credentials now
    private AmazonS3Client s3Client;
    private GitHubService gitHubService; // New field for GitHubService

    public InternetArchiveService(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        // Load IA S3 Credentials
        this.accessKey = sharedPreferences.getString(KEY_ACCESS_KEY, null);
        this.secretKey = sharedPreferences.getString(KEY_SECRET_KEY, null);

        if (this.accessKey != null && !this.accessKey.isEmpty() && this.secretKey != null && !this.secretKey.isEmpty()) {
            try {
                BasicAWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
                this.s3Client = new AmazonS3Client(credentials);
                this.s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
                this.s3Client.setEndpoint("s3.us.archive.org");
                this.credentialsLoaded = true; // S3 credentials loaded
                Log.i(TAG, "AmazonS3Client initialized successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing AmazonS3Client: " + e.getMessage(), e);
                this.s3Client = null;
                this.credentialsLoaded = false; // S3 credentials failed to load
            }
        } else {
            this.credentialsLoaded = false;
            Log.w(TAG, "IA S3 Credentials not found or incomplete. S3 client not initialized.");
        }

        // Initialize GitHubService
        this.gitHubService = new GitHubService(context);
        // Note: GitHubService loads its own credentials internally from SharedPreferences.
        // We might want a way to check if GitHub credentials are also loaded successfully if needed for combined status.
    }

    public boolean areCredentialsLoaded() {
        // This now primarily reflects S3 credential status.
        // GitHubService has its own internal credential loading, might need a separate check if required.
        return credentialsLoaded;
    }

    /**
     * Tests S3 connection and GitHub connection (by trying to fetch the root JSON).
     *
     * @param itemTitleAsBucketName The IA S3 bucket name.
     * @param rootJsonPath The path to the root JSON file on GitHub.
     * @return true if both connections are successful, false otherwise.
     */
    public boolean testConnection(String itemTitleAsBucketName, String rootJsonPath) {
        Log.d(TAG, "testConnection: Testando IA S3 bucket=" + itemTitleAsBucketName + " e GitHub JSON root=" + rootJsonPath);
        boolean s3Ok = false;
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "testConnection: IA S3 Credentials are not loaded or S3 client not initialized.");
        } else if (itemTitleAsBucketName == null || itemTitleAsBucketName.isEmpty()) {
            Log.e(TAG, "testConnection: Item title (S3 bucket name) is null or empty.");
        } else {
            try {
                Log.i(TAG, "Attempting S3 connection test for bucket: " + itemTitleAsBucketName);
                s3Client.listObjectsV2(new ListObjectsV2Request().withBucketName(itemTitleAsBucketName).withMaxKeys(1));
                Log.i(TAG, "S3 connection test successful for bucket: " + itemTitleAsBucketName);
                s3Ok = true;
            } catch (Exception e) {
                Log.e(TAG, "Caught exception type during S3 testConnection: " + e.getClass().getName());
                Log.e(TAG, "S3 Connection Test Failed: " + e.getMessage(), e);
            }
        }

        boolean githubOk = false;
        if (gitHubService == null) {
            Log.e(TAG, "testConnection: GitHubService is not initialized.");
        } else if (rootJsonPath == null || rootJsonPath.isEmpty()) {
            Log.e(TAG, "testConnection: Root JSON path for GitHub is null or empty.");
        } else {
            try {
                Log.i(TAG, "Attempting GitHub connection test by fetching root JSON: " + rootJsonPath);
                JSONObject rootJson = gitHubService.getJsonFileContent(rootJsonPath);
                if (rootJson != null) {
                    Log.i(TAG, "GitHub connection test successful (fetched root JSON).");
                    githubOk = true;
                } else {
                    Log.w(TAG, "GitHub connection test failed: root JSON was null (file not found or other error).");
                }
            } catch (IOException e) {
                Log.e(TAG, "Caught IOException during GitHub testConnection (likely from getJsonFileContent): " + e.getClass().getName());
                Log.e(TAG, "GitHub Connection Test Failed: " + e.getMessage(), e);
            }
        }
        Log.d(TAG, "testConnection: Teste S3 " + (s3Ok ? "OK" : "Falhou") + ". Teste GitHub JSON " + (githubOk ? "OK" : "Falhou"));
        return s3Ok && githubOk;
    }

    /**
     * Loads file and folder listings from a JSON file specified by jsonPath, using GitHubService.
     * This method replaces the direct S3 listing.
     *
     * @param jsonPath The path to the JSON file on GitHub that describes the directory content.
     * @return A list of ArchiveFile objects.
     */
    public List<ArchiveFile> loadFilesAndFolders(String jsonPath) {
        Log.d(TAG, "loadFilesAndFolders: Carregando para jsonPath: " + jsonPath);
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        if (gitHubService == null) {
            Log.e(TAG, "GitHubService not initialized in loadFilesAndFolders.");
            return archiveFiles;
        }
        if (jsonPath == null || jsonPath.isEmpty()) {
            Log.e(TAG, "JSON path cannot be null or empty in loadFilesAndFolders.");
            return archiveFiles;
        }

        try {
            JSONObject jsonDirectory = gitHubService.getJsonFileContent(jsonPath);
            if (jsonDirectory == null) {
                Log.w(TAG, "loadFilesAndFolders: JSON do diretório não encontrado ou erro ao buscar: " + jsonPath + ". Retornando lista vazia.");
                return archiveFiles;
            }

            JSONArray entries = jsonDirectory.optJSONArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i); // Use the imported JSONObject
                    String name = entry.getString("name");
                    String type = entry.getString("type");
                    boolean isDirectory = "directory".equalsIgnoreCase(type);

                    String iaS3Key = entry.optString("ia_s3_key", null);
                    String entryJsonPath = entry.optString("json_path", null);
                    String size = entry.optString("size", isDirectory ? "DIR" : "0");
                    String lastModified = entry.optString("last_modified", "N/A");

                    archiveFiles.add(new ArchiveFile(name, isDirectory, size, lastModified, iaS3Key, entryJsonPath));
                }
            }
             Log.i(TAG, "Successfully loaded " + archiveFiles.size() + " entries from: " + jsonPath);
        } catch (IOException e) {
            Log.e(TAG, "IOException while loading files/folders from GitHub JSON: " + jsonPath, e);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException while parsing GitHub JSON: " + jsonPath, e);
        }
        Log.d(TAG, "loadFilesAndFolders: Retornando " + archiveFiles.size() + " entradas para jsonPath: " + jsonPath);
        return archiveFiles;
    }

    /**
     * Uploads a file to IA S3 and then updates the GitHub JSON index.
     * @param localFilePath Path to the local file to upload.
     * @param targetS3Key The desired S3 key for the file in the IA bucket.
     * @param parentJsonPath Path to the parent directory's JSON file on GitHub.
     * @param itemTitleAsBucketName The IA S3 bucket name.
     * @return true if both S3 upload and GitHub index update are successful.
     */
    public boolean uploadFileAndIndex(String localFilePath, String targetS3Key, String parentJsonPath, String itemTitleAsBucketName) {
        Log.d(TAG, "uploadFileAndIndex: localPath=" + localFilePath + ", targetS3Key=" + targetS3Key + ", parentJsonPath=" + parentJsonPath);
        // 1. Upload to Internet Archive S3
        boolean s3UploadSuccess = uploadFile(itemTitleAsBucketName, localFilePath, targetS3Key);
        Log.d(TAG, "uploadFileAndIndex: Upload S3 para " + targetS3Key + (s3UploadSuccess ? " bem-sucedido." : " falhou."));

        if (!s3UploadSuccess) {
            Log.e(TAG, "uploadFileAndIndex: S3 upload failed for " + targetS3Key + ". Aborting index update.");
            return false;
        }
        // Log.i(TAG, "uploadFileAndIndex: S3 upload successful for " + targetS3Key); // Redundant with above

        // 2. Update GitHub JSON index
        if (gitHubService == null) {
            Log.e(TAG, "uploadFileAndIndex: GitHubService not initialized.");
            return false; // Or handle this state more gracefully
        }
         if (parentJsonPath == null || parentJsonPath.isEmpty()) {
            Log.e(TAG, "uploadFileAndIndex: parentJsonPath is null or empty. Cannot update index.");
            return false;
        }

        try {
            JSONObject parentJson = null;
            JSONArray entries;
            try {
                parentJson = gitHubService.getJsonFileContent(parentJsonPath);
            } catch (IOException e) {
                Log.e(TAG, "IOException ao buscar JSON pai para upload: " + parentJsonPath, e);
                return false;
            }

            if (parentJson == null) {
                Log.i(TAG, "uploadFileAndIndex: JSON pai não encontrado em " + parentJsonPath + ". Criando novo JSON pai em memória.");
                parentJson = new JSONObject();
                entries = new JSONArray();
                try {
                    parentJson.put("entries", entries);
                } catch (JSONException e) {
                    Log.e(TAG, "Erro ao inicializar JSON pai: ", e);
                    return false;
                }
            } else {
                entries = parentJson.optJSONArray("entries");
                if (entries == null) { // JSON existe, mas não tem array 'entries'
                    Log.w(TAG, "JSON pai " + parentJsonPath + " não continha array 'entries'. Criando array.");
                    entries = new JSONArray(); // Use imported JSONArray
                    try {
                        parentJson.put("entries", entries);
                    } catch (JSONException e) { // Use imported JSONException
                        Log.e(TAG, "Erro ao adicionar array 'entries' ao JSON pai: ", e);
                        return false;
                    }
                }
            }

            // Create new entry for the uploaded file
            java.io.File uploadedFile = new java.io.File(localFilePath); // Explicitly java.io.File
            JSONObject newEntry = new JSONObject(); // Use imported JSONObject
            newEntry.put("name", uploadedFile.getName());
            newEntry.put("type", "file");
            newEntry.put("ia_s3_key", targetS3Key);
            newEntry.put("size", String.valueOf(uploadedFile.length()));
            newEntry.put("last_modified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(uploadedFile.lastModified())));

            boolean entryUpdated = false;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject currentEntry = entries.getJSONObject(i); // Use imported JSONObject
                if (uploadedFile.getName().equals(currentEntry.optString("name")) && "file".equals(currentEntry.optString("type"))) {
                    entries.put(i, newEntry);
                    entryUpdated = true;
                    break;
                }
            }
            if (!entryUpdated) {
                entries.put(newEntry);
            }

            String commitMessage = "Added/Updated file: " + uploadedFile.getName();
            boolean githubUpdateSuccess = gitHubService.updateJsonFile(parentJsonPath, parentJson, commitMessage);
            Log.d(TAG, "uploadFileAndIndex: Atualização do JSON " + parentJsonPath + (githubUpdateSuccess ? " bem-sucedida." : " falhou."));
            return githubUpdateSuccess;

        } catch (IOException e) {
            Log.e(TAG, "uploadFileAndIndex: IOException during GitHub JSON update: " + e.getMessage(), e);
        } catch (JSONException e) {
            Log.e(TAG, "uploadFileAndIndex: JSONException during GitHub JSON update: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Creates a new directory:
     * 1. Creates an empty JSON file on GitHub to represent the directory's index.
     * 2. Adds an entry for this new directory to its parent's JSON index file on GitHub.
     * 3. (Optional) Creates a corresponding placeholder "folder" object in IA S3.
     * @param newDirName Name for the new directory.
     * @param parentJsonPath Path to the parent directory's JSON index on GitHub.
     * @param newDirJsonFileName Full path (including name) for the new directory's JSON index file on GitHub.
     * @param itemTitleAsBucketName IA S3 bucket name (for optional S3 folder marker).
     * @param s3FolderPath Full S3 key for the optional IA S3 folder marker (e.g., "path/to/newDirName/").
     * @return true if the directory index was successfully created on GitHub.
     */
    public boolean createDirectoryAndIndex(String newDirName, String parentJsonPath, String newDirJsonFileName, String itemTitleAsBucketName, String s3FolderPath) {
        Log.d(TAG, "createDirectoryAndIndex: newDir=" + newDirName + ", parentJson=" + parentJsonPath + ", newDirJsonFile=" + newDirJsonFileName);
        if (gitHubService == null) {
            Log.e(TAG, "createDirectoryAndIndex: GitHubService not initialized.");
            return false;
        }
        if (newDirName == null || newDirName.isEmpty() || parentJsonPath == null || parentJsonPath.isEmpty() || newDirJsonFileName == null || newDirJsonFileName.isEmpty()) {
            Log.e(TAG, "createDirectoryAndIndex: Directory name, parent JSON path, or new directory JSON filename is null or empty.");
            return false;
        }

        try {
            // 1. Create the JSON file for the new directory
            JSONObject newDirJson = new JSONObject();
            newDirJson.put("entries", new JSONArray());

            boolean newDirJsonCreationSuccess = gitHubService.updateJsonFile(newDirJsonFileName, newDirJson, "Created directory structure for " + newDirName);
            Log.d(TAG, "createDirectoryAndIndex: Criação do JSON " + newDirJsonFileName + (newDirJsonCreationSuccess ? " bem-sucedida." : " falhou."));
            if (!newDirJsonCreationSuccess) {
                Log.e(TAG, "createDirectoryAndIndex: Failed to create new directory JSON file on GitHub: " + newDirJsonFileName);
                return false;
            }
            // Log.i(TAG, "createDirectoryAndIndex: New directory JSON created on GitHub: " + newDirJsonFileName); // Redundant

            // 2. Update the parent directory's JSON
            JSONObject parentJson = null;
            JSONArray parentEntries;
            try {
                parentJson = gitHubService.getJsonFileContent(parentJsonPath);
            } catch (IOException e) {
                Log.e(TAG, "IOException ao buscar JSON pai para criar diretório: " + parentJsonPath, e);
                return false;
            }

            if (parentJson == null) {
                Log.i(TAG, "createDirectoryAndIndex: JSON pai não encontrado em " + parentJsonPath + ". Criando novo JSON pai em memória.");
                parentJson = new JSONObject();
                parentEntries = new JSONArray();
                try {
                    parentJson.put("entries", parentEntries);
                } catch (JSONException e) {
                    Log.e(TAG, "Erro ao inicializar JSON pai: ", e);
                    return false;
                }
            } else {
                parentEntries = parentJson.optJSONArray("entries");
                if (parentEntries == null) {
                    Log.w(TAG, "JSON pai " + parentJsonPath + " não continha array 'entries'. Criando array.");
                    parentEntries = new JSONArray(); // Use imported JSONArray
                    try {
                        parentJson.put("entries", parentEntries);
                    } catch (JSONException e) { // Use imported JSONException
                        Log.e(TAG, "Erro ao adicionar array 'entries' ao JSON pai: ", e);
                        return false;
                    }
                }
            }

            JSONObject newDirEntry = new JSONObject(); // Use imported JSONObject
            newDirEntry.put("name", newDirName);
            newDirEntry.put("type", "directory");
            newDirEntry.put("json_path", newDirJsonFileName);
            newDirEntry.put("last_modified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

            boolean entryUpdatedOrAdded = false;
            for (int i = 0; i < parentEntries.length(); i++) {
                JSONObject currentEntry = parentEntries.getJSONObject(i); // Use imported JSONObject
                if (newDirName.equals(currentEntry.optString("name")) && "directory".equals(currentEntry.optString("type"))) {
                    parentEntries.put(i, newDirEntry);
                    entryUpdatedOrAdded = true;
                    break;
                }
            }
            if (!entryUpdatedOrAdded) {
                parentEntries.put(newDirEntry);
            }

            boolean parentUpdateSuccess = gitHubService.updateJsonFile(parentJsonPath, parentJson, "Added subdirectory: " + newDirName);
            Log.d(TAG, "createDirectoryAndIndex: Atualização do JSON pai " + parentJsonPath + (parentUpdateSuccess ? " bem-sucedida." : " falhou."));
            if (!parentUpdateSuccess) {
                Log.e(TAG, "createDirectoryAndIndex: Failed to update parent JSON (" + parentJsonPath + ") with new directory entry.");
                return false;
            }
            // Log.i(TAG, "createDirectoryAndIndex: Parent JSON updated successfully with new directory: " + newDirName); // Redundant

            if (itemTitleAsBucketName != null && !itemTitleAsBucketName.isEmpty() && s3FolderPath != null && !s3FolderPath.isEmpty()) {
                boolean s3FolderCreated = createFolder(itemTitleAsBucketName, s3FolderPath);
                if (!s3FolderCreated) {
                    Log.w(TAG, "createDirectoryAndIndex: Failed to create S3 folder marker at " + s3FolderPath + " (this is optional).");
                } else {
                    Log.i(TAG, "createDirectoryAndIndex: S3 folder marker created at " + s3FolderPath);
                }
            }
            return true;

        } catch (IOException e) {
            Log.e(TAG, "createDirectoryAndIndex: IOException: " + e.getMessage(), e);
        } catch (JSONException e) {
            Log.e(TAG, "createDirectoryAndIndex: JSONException: " + e.getMessage(), e);
        }
        return false;
    }

    // This is the original S3-only uploadFile, now used internally or for direct S3 uploads without indexing
    public boolean uploadFile(String itemTitleAsBucketName, String localFilePath, String remoteFileNameInItemAsKey) {
        if (!credentialsLoaded || s3Client == null) {
            Log.e(TAG, "uploadFile (S3-only): Credentials are not loaded or S3 client not initialized. Cannot upload file.");
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
        } catch (Exception e) {
            Log.e(TAG, "Caught exception type: " + e.getClass().getName());
            Log.e(TAG, "Exception during S3 upload: " + e.getMessage(), e);
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
        } catch (Exception e) {
            Log.e(TAG, "Caught exception type: " + e.getClass().getName());
            Log.e(TAG, "Exception during S3 download: " + e.getMessage(), e);
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
        } catch (Exception e) {
            Log.e(TAG, "Caught exception type: " + e.getClass().getName());
            Log.e(TAG, "Exception during folder creation: " + e.getMessage(), e);
        }
        return false;
    }
}
