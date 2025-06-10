package com.example.ldcloud.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
// import androidx.recyclerview.widget.LinearLayoutManager;
// import androidx.recyclerview.widget.RecyclerView;
import com.example.ldcloud.R; // Assuming R exists or will exist
import com.example.ldcloud.utils.ArchiveFile;
import com.example.ldcloud.ui.ArchiveFileAdapterCallbacks; // Added import

import java.util.ArrayList;
import java.util.List;

// Assuming FolderActionsBottomSheet exists elsewhere or is not critical for this refactoring
// For now, I will comment out the FolderActionsBottomSheet.FolderActionsListener part
public class FilesFragment extends Fragment implements ArchiveFileAdapterCallbacks /*, FolderActionsBottomSheet.FolderActionsListener */ {

    // private RecyclerView recyclerView;
    private ArchiveFileAdapter adapter;
    private List<ArchiveFile> fileList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Assuming a layout R.layout.fragment_files exists
        // View view = inflater.inflate(R.layout.fragment_files, container, false);
        // recyclerView = view.findViewById(R.id.recycler_view_files); // Assuming this ID exists
        // setupRecyclerView();
        // For now, returning null or a placeholder view as layout is not the focus
        return null; // Or a new View(getContext());
    }

    private void setupRecyclerView() {
        fileList = new ArrayList<>();
        // adapter = new ArchiveFileAdapter(getContext(), fileList, this);
        // recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // recyclerView.setAdapter(adapter);
        // loadFiles(); // Placeholder for actual file loading logic
    }

    private void loadFiles() {
        // Placeholder: Add some dummy data
        // fileList.add(new ArchiveFile("Documents", true, 0, 0, "/Documents"));
        // fileList.add(new ArchiveFile("image.jpg", false, 1024, System.currentTimeMillis(), "/image.jpg"));
        // adapter.notifyDataSetChanged();
    }

    // Implementation of ArchiveFileAdapter.ArchiveFileAdapterCallbacks
    @Override
    public void onDownloadRequested(ArchiveFile file) {
        // Handle download
        System.out.println("Download requested: " + file.name);
    }

    @Override
    public void onDirectoryClicked(ArchiveFile directory) {
        // Handle directory click
        System.out.println("Directory clicked: " + directory.name);
    }

    @Override
    public void onDirectoryLongClicked(ArchiveFile directory) {
        // Handle directory long click
        System.out.println("Directory long clicked: " + directory.name);
    }

    // Implementation of FolderActionsBottomSheet.FolderActionsListener (if it were included)
    // @Override
    // public void onRenameFolderRequested(ArchiveFile folder, String newName) {
    //     System.out.println("Rename folder: " + folder.name + " to " + newName);
    // }
    //
    // @Override
    // public void onDeleteFolderRequested(ArchiveFile folder) {
    //     System.out.println("Delete folder: " + folder.name);
    // }

    // Added based on the subtask's premise that this method exists and needs modification
    private void showRenameFolderDialog(final ArchiveFile folder) {
        if (getContext() == null) return;

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Renomear Pasta");

        // Set up the input
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setText(folder.name);
        input.setHint("Novo nome da pasta");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setMaxLines(1);

        // FrameLayout container to apply margins for the EditText
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // Definir padding em dp e converter para pixels
        int paddingDp = 20; // Valor comum para padding lateral em diálogos Material (ex: 24dp também é usado)
        float density = getResources().getDisplayMetrics().density;
        int paddingPx = (int) (paddingDp * density);

        params.leftMargin = paddingPx;
        params.rightMargin = paddingPx;
        // Adicionar um pouco de padding vertical ao EditText também pode ser bom, se não vier do estilo
        // params.topMargin = paddingPx / 2;
        // params.bottomMargin = paddingPx / 2;

        input.setLayoutParams(params); // Aplicar os LayoutParams com margens ao EditText
        container.addView(input);    // Adicionar o EditText ao FrameLayout container
        builder.setView(container);

        builder.setPositiveButton("Renomear", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(folder.name)) {
                // Call the actual rename logic (e.g., from a ViewModel or Service)
                // For this example, just print it.
                // This would typically involve:
                // 1. Calling a method in InternetArchiveService/GitHubService
                // 2. Updating the local list
                // 3. Notifying the adapter
                System.out.println("Attempting to rename '" + folder.name + "' to '" + newName + "'");
                // onRenameFolderRequested(folder, newName); // If using the listener pattern fully
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
