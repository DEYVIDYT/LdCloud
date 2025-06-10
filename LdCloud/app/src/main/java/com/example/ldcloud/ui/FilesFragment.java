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
    private static final String KEY_ITEM_TITLE = "itemTitle"; // From SettingsFragment

    private RecyclerView recyclerViewFiles;
    private ArchiveFileAdapter archiveFileAdapter;
    private ProgressBar progressBarLoadingFiles;
    private TextView textViewNoFiles;
    private FloatingActionButton fabCreateFolder; // FAB for creating folder
    private InternetArchiveService internetArchiveService;
    private SharedPreferences sharedPreferences;

    public FilesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            internetArchiveService = new InternetArchiveService(getContext());
            sharedPreferences = getContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
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

        loadFiles();
    }

    private void loadFiles() {
        if (internetArchiveService == null || sharedPreferences == null || getContext() == null) {
            Log.e(TAG, "Service or SharedPreferences not initialized for loadFiles.");
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error: Service not available for loading files.", Toast.LENGTH_SHORT).show();
            }
            textViewNoFiles.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.GONE);
            progressBarLoadingFiles.setVisibility(View.GONE);
            return;
        }

        String itemTitle = sharedPreferences.getString(KEY_ITEM_TITLE, null);

        if (itemTitle == null || itemTitle.isEmpty()) {
            Log.w(TAG, "Item Title not found in SharedPreferences.");
            Toast.makeText(getContext(), "Item Title not set in Settings.", Toast.LENGTH_LONG).show();
            textViewNoFiles.setText("Item Title not set in Settings for loading files.");
            textViewNoFiles.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.GONE);
            progressBarLoadingFiles.setVisibility(View.GONE);
            return;
        }

        // Show loading indicator
        progressBarLoadingFiles.setVisibility(View.VISIBLE);
        recyclerViewFiles.setVisibility(View.GONE);
        textViewNoFiles.setVisibility(View.GONE);

        // Perform network operation on a background thread
        // For simplicity in this environment, I'm calling it directly.
        // In a real Android app, use AsyncTask, Coroutines, or ExecutorService.
        new Thread(() -> {
            List<ArchiveFile> files = internetArchiveService.listFilesAndFolders(itemTitle);
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
        if (internetArchiveService == null || sharedPreferences == null || getContext() == null) {
            Toast.makeText(getContext(), "Error: Service not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        String itemTitle = sharedPreferences.getString(KEY_ITEM_TITLE, null);
        if (itemTitle == null || itemTitle.isEmpty()) {
            Toast.makeText(getContext(), "Item Title not set in Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), "Creating folder '" + folderName + "'...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = internetArchiveService.createFolder(itemTitle, folderName);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(getContext(), "Folder '" + folderName + "' created.", Toast.LENGTH_LONG).show();
                        loadFiles(); // Refresh file list
                    } else {
                        Toast.makeText(getContext(), "Failed to create folder '" + folderName + "'.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
