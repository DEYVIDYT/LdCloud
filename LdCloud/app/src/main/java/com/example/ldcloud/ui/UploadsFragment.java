package com.example.ldcloud.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.example.ldcloud.R;
import com.example.ldcloud.utils.InternetArchiveService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class UploadsFragment extends Fragment {

    private static final String TAG = "UploadsFragment";
    private static final String SHARED_PREFS_NAME = "LdCloudSettings";
    // IA S3 Bucket (Item Title)
    private static final String KEY_IA_ITEM_TITLE = "itemTitle";
    // GitHub Root JSON Path (used as default parent for uploads in this simplified version)
    private static final String KEY_ROOT_JSON_PATH = "root_json_path";
    private static final String DEFAULT_ROOT_JSON_PATH = "ldcloud_root.json";


    private FloatingActionButton fabUploadFile;
    private InternetArchiveService internetArchiveService;
    private SharedPreferences sharedPreferences;

    private String parentJsonPathForUpload;
    private String iaItemTitleForUpload;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    public UploadsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            internetArchiveService = new InternetArchiveService(requireContext());
            sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);

            // Load paths needed for upload operation
            // For this subtask, uploads always go to the directory specified by root_json_path.
            // A more complex implementation would get currentJsonPath from FilesFragment.
            parentJsonPathForUpload = sharedPreferences.getString(KEY_ROOT_JSON_PATH, DEFAULT_ROOT_JSON_PATH);
            iaItemTitleForUpload = sharedPreferences.getString(KEY_IA_ITEM_TITLE, "");

            if (iaItemTitleForUpload.isEmpty()) {
                Log.e(TAG, "IA Item Title (S3 Bucket) is not configured.");
                // Consider disabling upload FAB if critical info is missing, or show persistent error on UI
            }
             if (parentJsonPathForUpload.isEmpty() && DEFAULT_ROOT_JSON_PATH.equals(sharedPreferences.getString(KEY_ROOT_JSON_PATH, DEFAULT_ROOT_JSON_PATH))) {
                Log.w(TAG, "Root JSON path is the default and might not exist yet. Uploads will attempt to use/create it.");
            }

        } else {
            Log.e(TAG, "Context is null during onCreate, cannot initialize services.");
        }

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            Log.d(TAG, "File selected: " + selectedFileUri.toString());
                            // Attempt to get path and upload
                            handleSelectedFile(selectedFileUri);
                        } else {
                            Log.e(TAG, "Selected file URI is null");
                            Toast.makeText(getContext(), "Failed to get file URI", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "File selection cancelled or failed.");
                        // Toast.makeText(getContext(), "File selection cancelled", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_uploads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fabUploadFile = view.findViewById(R.id.fab_upload_file);
        fabUploadFile.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow all file types
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Select a file to upload"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getContext(), "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        if (getContext() == null || internetArchiveService == null) {
            Log.e(TAG, "Context or InternetArchiveService is null in handleSelectedFile.");
            Toast.makeText(getContext(), "Error: Service not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        // iaItemTitleForUpload and parentJsonPathForUpload are now loaded in onCreate

        if (iaItemTitleForUpload == null || iaItemTitleForUpload.isEmpty()) {
            Toast.makeText(getContext(), "IA Item Title (S3 Bucket) not set in Settings.", Toast.LENGTH_LONG).show();
            return;
        }
        if (parentJsonPathForUpload == null || parentJsonPathForUpload.isEmpty()){
            Toast.makeText(getContext(), "Parent JSON path for GitHub not configured.", Toast.LENGTH_LONG).show();
            return;
        }

        // For ACTION_GET_CONTENT, directly getting a real path is problematic and not recommended.
        // Best practice is to copy the file to app's cache directory and upload from there.
        String fileName = getFileName(getContext(), uri);
        File cacheFile = new File(getContext().getCacheDir(), fileName);

        try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(cacheFile)) {
            if (inputStream == null) {
                Toast.makeText(getContext(), "Failed to open file stream", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            Log.i(TAG, "File copied to cache: " + cacheFile.getAbsolutePath());

            // Now 'upload' this cacheFile.getAbsolutePath()
            // In a real app, run this in a background thread
            // Ensure this part is executed off the main thread if not already.
            // For this exercise, direct call is shown.
            String originalFileName = fileName; // Renaming for clarity to match parameter name for uploadFileAndIndex
            String targetS3Key = originalFileName; // Simple S3 key strategy

            new Thread(() -> {
                // Use the new orchestrated method
                final boolean success = internetArchiveService.uploadFileAndIndex(
                        cacheFile.getAbsolutePath(),
                        targetS3Key,
                        parentJsonPathForUpload, // Class field, loaded in onCreate
                        iaItemTitleForUpload     // Class field, loaded in onCreate
                );

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(getContext(), "Upload and index for '" + originalFileName + "' successful!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Upload and/or index for '" + originalFileName + "' failed.", Toast.LENGTH_LONG).show();
                        }

                        if (cacheFile.exists()) {
                            if (cacheFile.delete()) {
                                Log.i(TAG, "Cache file deleted: " + cacheFile.getAbsolutePath());
                            } else {
                                Log.w(TAG, "Failed to delete cache file: " + cacheFile.getAbsolutePath());
                            }
                        }
                    });
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error handling file selection or preparing for upload: " + e.getMessage(), e);
            Toast.makeText(getContext(), "Error processing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            // Clean up cache file in case of error during preparation
            if (cacheFile.exists()) {
                if(cacheFile.delete()){
                    Log.i(TAG, "Cache file deleted due to error during preparation: " + cacheFile.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to delete cache file during error cleanup: " + cacheFile.getAbsolutePath());
                }
            }
        }
    }

    // Helper method to get file name from URI
    private String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                         result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        // Sanitize filename if necessary, or use a default
        return result != null ? result : "upload_file_" + System.currentTimeMillis();
    }
}
