package com.example.ldcloud.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText; // For AlertDialog input
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog; // For folder name input dialog
import com.example.ldcloud.R;
import com.example.ldcloud.utils.ArchiveFile;
import com.example.ldcloud.utils.InternetArchiveService;
import com.google.android.material.floatingactionbutton.FloatingActionButton; // For the new FAB
import java.io.File; // For creating local target file
import java.util.ArrayList;
import java.util.List;
import android.os.Environment; // For getting downloads directory

public class FilesFragment extends Fragment implements ArchiveFileAdapterCallbacks {

    private static final String TAG = "FilesFragment";
    private static final String SHARED_PREFS_NAME = "LdCloudSettings";
    // IA S3 Bucket (Item Title)
    private static final String KEY_IA_ITEM_TITLE = "itemTitle";
    // GitHub Root JSON Path
    private static final String KEY_ROOT_JSON_PATH = "root_json_path";
    private static final String DEFAULT_ROOT_JSON_PATH = "ldcloud_root.json";

    private RecyclerView recyclerViewFiles;
    private ArchiveFileAdapter archiveFileAdapter;
    private ProgressBar progressBarLoadingFiles;
    private TextView textViewNoFiles;
    private FloatingActionButton fabCreateFolder;
    private InternetArchiveService internetArchiveService;
    private SharedPreferences sharedPreferences;

    private String currentJsonPath;
    private String rootJsonPath;
    private String iaItemTitle; // For S3 operations like creating folder markers

    public FilesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            internetArchiveService = new InternetArchiveService(getContext());
            // SharedPreferences initialized here, but values loaded in onViewCreated or onResume
            // to ensure fragment is attached to activity for requireActivity().
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerViewFiles = view.findViewById(R.id.recycler_view_files);
        progressBarLoadingFiles = view.findViewById(R.id.progress_bar_loading_files);
        textViewNoFiles = view.findViewById(R.id.text_view_no_files);
        fabCreateFolder = view.findViewById(R.id.fab_create_folder);

        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        archiveFileAdapter = new ArchiveFileAdapter(new ArrayList<>(), this);
        recyclerViewFiles.setAdapter(archiveFileAdapter);

        fabCreateFolder.setOnClickListener(v -> showCreateFolderDialog());

        // Initialize SharedPreferences here as requireActivity() is safe
        if (getActivity() != null) {
            sharedPreferences = requireActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            rootJsonPath = sharedPreferences.getString(KEY_ROOT_JSON_PATH, DEFAULT_ROOT_JSON_PATH);
            iaItemTitle = sharedPreferences.getString(KEY_IA_ITEM_TITLE, ""); // IA S3 Bucket name
            currentJsonPath = rootJsonPath; // Start at the root
            loadFiles(currentJsonPath);
        } else {
            Log.e(TAG, "Activity is null in onViewCreated, cannot initialize SharedPreferences or load files.");
            // Display an error or handle appropriately
            textViewNoFiles.setText("Error initializing settings.");
            textViewNoFiles.setVisibility(View.VISIBLE);
            progressBarLoadingFiles.setVisibility(View.GONE);
            recyclerViewFiles.setVisibility(View.GONE);
        }
    }

    // Step 3: Modify loadFiles()
    private void loadFiles(String jsonPathToLoad) {
        if (internetArchiveService == null || getContext() == null) {
            Log.e(TAG, "Service or Context not available for loadFiles.");
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error: Service not available.", Toast.LENGTH_SHORT).show();
            }
            textViewNoFiles.setText("Error: Service not available.");
            textViewNoFiles.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.GONE);
            progressBarLoadingFiles.setVisibility(View.GONE);
            return;
        }

        this.currentJsonPath = jsonPathToLoad; // Update current path
        Log.i(TAG, "Loading files from: " + this.currentJsonPath);

        progressBarLoadingFiles.setVisibility(View.VISIBLE);
        recyclerViewFiles.setVisibility(View.GONE);
        textViewNoFiles.setVisibility(View.GONE);

        new Thread(() -> {
            List<ArchiveFile> files = internetArchiveService.loadFilesAndFolders(jsonPathToLoad);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressBarLoadingFiles.setVisibility(View.GONE);
                    if (files != null && !files.isEmpty()) {
                        archiveFileAdapter.updateData(files);
                        recyclerViewFiles.setVisibility(View.VISIBLE);
                        textViewNoFiles.setVisibility(View.GONE);
                    } else {
                        Log.w(TAG, "No files found or error loading files for item: " + itemTitle);
                        Toast.makeText(getContext(), "No files found or error fetching list.", Toast.LENGTH_LONG).show();
                        textViewNoFiles.setVisibility(View.VISIBLE);
                        recyclerViewFiles.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDownloadRequested(ArchiveFile file) {
        if (internetArchiveService == null || sharedPreferences == null || getContext() == null) {
            Log.e(TAG, "Service or SharedPreferences not initialized for onDownloadRequested.");
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error: Service not available for download.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        Log.d(TAG, "Download requested for: " + file.getName());
        String itemTitle = sharedPreferences.getString(KEY_ITEM_TITLE, null);

        if (itemTitle == null || itemTitle.isEmpty()) {
            Toast.makeText(getContext(), "Item Title not set in Settings. Cannot download.", Toast.LENGTH_LONG).show();
            return;
        }
        if (file.isDirectory()) {
            Toast.makeText(getContext(), "Cannot download a directory.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Define the target directory and file
        File targetDirectory = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (targetDirectory == null) {
            Toast.makeText(getContext(), "Error getting downloads directory.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }
        File localTargetFile = new File(targetDirectory, file.getName());

        Toast.makeText(getContext(), "Starting download for " + file.getName(), Toast.LENGTH_SHORT).show();

        // Perform download on a background thread
        new Thread(() -> {
            boolean success = internetArchiveService.downloadFile(itemTitle, file.getName(), localTargetFile);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(getContext(), "File '" + file.getName() + "' downloaded to " + localTargetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "Download failed for '" + file.getName() + "'.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void showCreateFolderDialog() {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Create New Folder");

        final EditText input = new EditText(getContext());
        input.setHint("Folder Name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                createNewFolder(folderName);
            } else {
                Toast.makeText(getContext(), "Folder name cannot be empty.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void createNewFolder(String folderName) {
        if (internetArchiveService == null || sharedPreferences == null || getContext() == null || currentJsonPath == null || iaItemTitle == null) {
            Toast.makeText(getContext(), "Error: Service, settings, or current path not available.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "createNewFolder: Prerequisites not met. internetArchiveService=" + internetArchiveService +
                               ", sharedPreferences=" + sharedPreferences +
                               ", currentJsonPath=" + currentJsonPath +
                               ", iaItemTitle=" + iaItemTitle);
            return;
        }
        // iaItemTitle is already loaded in onViewCreated from KEY_IA_ITEM_TITLE

        // Simplified newDirJsonFileName logic: place it "flat" in the repo but with a name that indicates path
        // e.g., if currentJsonPath is "root/subdir.json" and folderName is "newfolder",
        // newDirJsonFileName could be "root_subdir_newfolder.json"
        // A more robust solution would store these in actual GitHub subdirectories.
        String safeFolderName = folderName.replaceAll("[^a-zA-Z0-9-_]", "");
        String baseName = currentJsonPath.substring(0, currentJsonPath.lastIndexOf('.'));
        if(baseName.contains("/")) { // If it's a path, replace slashes for the filename part
            baseName = baseName.replace("/", "_");
        }
        String newDirJsonFileName = baseName + "_" + safeFolderName + ".json";

        // For S3, the folder path should reflect the visual hierarchy
        String s3FolderPath = ""; // Assume root for now if currentJsonPath is root.json
        if (!currentJsonPath.equals(rootJsonPath)) {
            // Attempt to derive S3 path from JSON path structure
            // This logic assumes jsonPath reflects S3 path structure, which needs careful design
            String parentPath = currentJsonPath.substring(0, currentJsonPath.lastIndexOf('/') > 0 ? currentJsonPath.lastIndexOf('/') : currentJsonPath.length());
             if(parentPath.endsWith(".json")) parentPath = parentPath.substring(0, parentPath.lastIndexOf('.')); // remove .json
            parentPath = parentPath.replace(rootJsonPath.substring(0, rootJsonPath.lastIndexOf('.')), ""); // remove root part
            if(parentPath.startsWith("_")) parentPath = parentPath.substring(1); // remove leading _ if root_
            parentPath = parentPath.replace("_", "/"); // replace internal _ with /
            if(!parentPath.isEmpty() && !parentPath.endsWith("/")) parentPath += "/";
            s3FolderPath = parentPath;
        }
        s3FolderPath += folderName + "/";


        Log.d(TAG, "Creating folder: " + folderName +
                   ", parentJsonPath: " + currentJsonPath +
                   ", newDirJsonFileName: " + newDirJsonFileName +
                   ", iaItemTitle: " + iaItemTitle +
                   ", s3FolderPath: " + s3FolderPath);

        Toast.makeText(getContext(), "Creating folder '" + folderName + "'...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = internetArchiveService.createDirectoryAndIndex(
                    folderName,
                    currentJsonPath,
                    newDirJsonFileName,
                    iaItemTitle, // IA S3 bucket name
                    s3FolderPath // S3 "folder" key
            );
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(getContext(), "Folder '" + folderName + "' created.", Toast.LENGTH_LONG).show();
                        loadFiles(currentJsonPath); // Refresh current directory
                    } else {
                        Toast.makeText(getContext(), "Failed to create folder '" + folderName + "'.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDirectoryClicked(ArchiveFile directory) {
        if (directory.jsonPath != null && !directory.jsonPath.isEmpty()) {
            Log.d(TAG, "Directory clicked: " + directory.getName() + ", loading path: " + directory.jsonPath);
            loadFiles(directory.jsonPath); // Carrega o conteúdo da subpasta
        } else {
            Toast.makeText(getContext(), "Caminho JSON para o diretório não definido.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Directory clicked but jsonPath is null or empty for: " + directory.getName());
        }
    }

    private void showCreateFolderDialog() {
        if (getContext() == null) return;
