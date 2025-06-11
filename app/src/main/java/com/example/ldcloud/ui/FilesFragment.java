package com.example.ldcloud.ui;

import android.app.Activity;
import android.content.Context; // Added for onAttach
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher; // For requestPermissionLauncher example
// import androidx.activity.result.contract.ActivityResultContracts.RequestPermission; // For requestPermissionLauncher example
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton; // For fabAddItem example

import com.example.ldcloud.R;
import com.example.ldcloud.utils.ArchiveFile;
import com.example.ldcloud.ui.ArchiveFileAdapterCallbacks;
import com.example.ldcloud.utils.InternetArchiveService; // Now actually used

import java.util.ArrayList;
import java.util.List;

// Assuming FolderActionsBottomSheet exists elsewhere or is not critical for this refactoring
// For now, I will comment out the FolderActionsBottomSheet.FolderActionsListener part
public class FilesFragment extends Fragment implements ArchiveFileAdapterCallbacks /*, FolderActionsBottomSheet.FolderActionsListener */ {

    private static final String TAG = "FilesFragment";

    private ArchiveFileAdapter adapter;
    private List<ArchiveFile> fileList;
    private RecyclerView recyclerViewFiles; // Example
    private ProgressBar progressBar; // Example
    private TextView emptyView; // Example
    private InternetArchiveService internetArchiveService; // Uncommented
    private SharedPreferences sharedPreferences; // Example
    private String rootJsonPath;
    private String iaItemTitle;
    private String currentJsonPath;
    private ActivityResultLauncher<String> requestPermissionLauncher; // Example for POST_NOTIFICATIONS

    // Standard SharedPreferences constants (example)
    private static final String SHARED_PREFS_NAME = "LdCloudPrefs";
    private static final String KEY_ROOT_JSON_PATH = "root_json_path";
    private static final String KEY_IA_ITEM_TITLE = "ia_item_title";
    private static final String DEFAULT_ROOT_JSON_PATH = "files.json";


    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach: Fragmento anexado. Context: " + context);
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: Início. savedInstanceState is " + (savedInstanceState == null ? "null" : "not null"));
        super.onCreate(savedInstanceState);
        // internetArchiveService initialization moved to onViewCreated
        // Example: Initialize requestPermissionLauncher
        // requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        //     if (isGranted) {
        //         Log.d(TAG, "onCreate: POST_NOTIFICATIONS permission granted.");
        //     } else {
        //         Log.w(TAG, "onCreate: POST_NOTIFICATIONS permission denied.");
        //     }
        // });
        // Log.d(TAG, "onCreate: requestPermissionLauncher inicializado.");
        Log.d(TAG, "onCreate: internetArchiveService " + (internetArchiveService == null ? "é NULO aqui" : "inicializado")); // Will be null here
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Início. Inflating layout R.layout.fragment_files");
        View view = inflater.inflate(R.layout.fragment_files, container, false); // Assuming R.layout.fragment_files exists
        Log.d(TAG, "onCreateView: Layout inflado. View é " + (view == null ? "NULA" : "NÃO NULA"));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated: Início.");
        super.onViewCreated(view, savedInstanceState);

        recyclerViewFiles = view.findViewById(R.id.recycler_view_files); // Assuming R.id.recycler_view_files exists
        Log.d(TAG, "onViewCreated: recyclerViewFiles " + (recyclerViewFiles == null ? "NULO" : "OK"));
        progressBar = view.findViewById(R.id.progress_bar_files); // Assuming R.id.progress_bar_files exists
        Log.d(TAG, "onViewCreated: progressBar " + (progressBar == null ? "NULO" : "OK"));
        emptyView = view.findViewById(R.id.empty_view_files); // Assuming R.id.empty_view_files exists
        Log.d(TAG, "onViewCreated: emptyView " + (emptyView == null ? "NULO" : "OK"));
        FloatingActionButton fabAddItem = view.findViewById(R.id.fab_add_item); // Assuming R.id.fab_add_item exists
        Log.d(TAG, "onViewCreated: fabAddItem " + (fabAddItem == null ? "NULO" : "OK"));
        if (fabAddItem != null) {
            fabAddItem.setOnClickListener(v -> showAddActionDialog());
        }

        Log.d(TAG, "onViewCreated: internetArchiveService " + (internetArchiveService == null ? "NULO antes de init" : "JÁ INICIALIZADO"));
        if (getContext() == null) { // Should use requireContext() if context is critical
            Log.e(TAG, "onViewCreated: Context is null, cannot initialize services or UI.");
            return;
        }
        internetArchiveService = new InternetArchiveService(requireContext());
        Log.d(TAG, "onViewCreated: internetArchiveService inicializado AGORA.");

        Log.d(TAG, "onViewCreated: Carregando SharedPreferences...");
        sharedPreferences = requireActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "onViewCreated: SharedPreferences " + (sharedPreferences == null ? "NULA" : "OK"));

        rootJsonPath = sharedPreferences.getString(KEY_ROOT_JSON_PATH, DEFAULT_ROOT_JSON_PATH);
        iaItemTitle = sharedPreferences.getString(KEY_IA_ITEM_TITLE, ""); // Default to empty string if not found
        currentJsonPath = rootJsonPath;
        Log.d(TAG, "onViewCreated: Paths: root=" + rootJsonPath + ", iaItem=" + iaItemTitle + ", current=" + currentJsonPath);

        setupRecyclerView();

        Log.d(TAG, "onViewCreated: Chamando loadFiles com path: " + currentJsonPath);
        loadFiles(currentJsonPath);
        Log.d(TAG, "onViewCreated: Fim.");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Fragmento retomado.");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Fragmento pausado.");
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: View sendo destruída.");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Fragmento sendo destruído.");
        super.onDestroy();
    }

    private void setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Configurating RecyclerView...");
        fileList = new ArrayList<>();
        if (getContext() != null && recyclerViewFiles != null) {
            adapter = new ArchiveFileAdapter(requireContext(), fileList, this);
            // recyclerViewFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerViewFiles.setAdapter(adapter);
            Log.d(TAG, "setupRecyclerView: RecyclerView configurado com adapter.");
        } else {
            Log.e(TAG, "setupRecyclerView: Contexto ou RecyclerView nulo, não foi possível configurar o adapter.");
        }
    }

    private void loadFiles(String jsonPathToLoad) {
        Log.i(TAG, "loadFiles: Solicitado para jsonPath: " + jsonPathToLoad);
        Log.d(TAG, "loadFiles: internetArchiveService " + (internetArchiveService == null ? "NULO" : "OK") + ", getContext() " + (getContext() == null ? "NULO" : "OK"));

        if (internetArchiveService == null || getContext() == null) {
            Log.e(TAG, "loadFiles: Abortando. internetArchiveService ou Context é nulo.");
            return;
        }
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (recyclerViewFiles != null) recyclerViewFiles.setVisibility(View.GONE);

        new Thread(() -> {
            Log.d(TAG, "loadFiles (background): Chamando service.loadFilesAndFolders para: " + jsonPathToLoad);
            final List<ArchiveFile> files = internetArchiveService.loadFilesAndFolders(jsonPathToLoad);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "loadFiles (UI): Recebido " + (files != null ? files.size() : "NULO") + " arquivos/pastas para " + jsonPathToLoad);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    fileList.clear();
                    if (files != null && !files.isEmpty()) {
                        fileList.addAll(files);
                        if(adapter != null) adapter.notifyDataSetChanged();
                        if (recyclerViewFiles != null) recyclerViewFiles.setVisibility(View.VISIBLE);
                        Log.d(TAG, "loadFiles (UI): Exibindo RecyclerView com " + files.size() + " itens.");
                    } else {
                        if (recyclerViewFiles != null) recyclerViewFiles.setVisibility(View.GONE);
                        if (emptyView != null) {
                           emptyView.setVisibility(View.VISIBLE);
                           emptyView.setText(files == null ? "Erro ao carregar." : "Sem arquivos.");
                        }
                        Log.d(TAG, "loadFiles (UI): Exibindo mensagem 'sem arquivos'. files é " + (files == null ? "nulo" : "vazio"));
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDownloadRequested(ArchiveFile file) {
        Log.d(TAG, "onDownloadRequested para: " + (file != null ? file.name : "arquivo nulo"));
        if (file == null) return;
        System.out.println("Download requested: " + file.name);
    }

    @Override
    public void onDirectoryClicked(ArchiveFile directory) {
        Log.d(TAG, "onDirectoryClicked em: " + (directory != null ? directory.name : "diretório nulo") + " -> jsonPath: " + (directory != null ? directory.jsonPath : "N/A"));
        if (directory == null || directory.jsonPath == null) return;
        currentJsonPath = directory.jsonPath;
        loadFiles(currentJsonPath);
        // System.out.println("Directory clicked: " + directory.name); // Already logged
    }

    @Override
    public void onDirectoryLongClicked(ArchiveFile directory) {
        Log.d(TAG, "onDirectoryLongClicked em: " + (directory != null ? directory.name : "diretório nulo"));
        if (directory == null) return;
        // Show context menu or dialog for folder actions
    }

    private void showCreateFolderDialog() {
        Log.d(TAG, "showCreateFolderDialog chamado.");
        // ... (MaterialAlertDialogBuilder logic)
        // final EditText input = new EditText(requireContext());
        // ...
        // .setPositiveButton("Criar", (dialog, which) -> {
        //    String folderName = input.getText().toString().trim();
        //    if (!folderName.isEmpty()) createNewFolder(folderName);
        // })
    }

    private void createNewFolder(String folderName) {
        Log.d(TAG, "createNewFolder chamado com nome: " + folderName + ". currentJsonPath: " + currentJsonPath);
        if (internetArchiveService == null || folderName.isEmpty() || currentJsonPath == null || iaItemTitle == null) {
            Log.e(TAG, "createNewFolder: Abortando. Serviço, nome da pasta, currentJsonPath ou iaItemTitle é nulo/vazio.");
            return;
        }
        // internetArchiveService.createDirectoryAndIndex(currentJsonPath, folderName, iaItemTitle, true);
        // loadFiles(currentJsonPath); // Refresh
    }

    private void showAddActionDialog() {
        Log.d(TAG, "showAddActionDialog chamado.");
        // Options: Create Folder, Upload File
        // new MaterialAlertDialogBuilder(requireContext())
        // .setTitle("Adicionar")
        // .setItems(new CharSequence[]{"Nova Pasta", "Upload de Arquivo"}, (dialog, which) -> { ... })
        // .show();
    }

    private void openFileSelector() {
        Log.d(TAG, "openFileSelector chamado.");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            // startActivityForResult(intent, YOUR_REQUEST_CODE_FOR_FILE_PICKER); // Deprecated
            // Use ActivityResultLauncher if targeting newer APIs
            Log.d(TAG, "openFileSelector: Tentando abrir seletor de arquivos.");
             // For example purposes, this might be an ActivityResultLauncher<Intent>
             // filePickerLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "openFileSelector: Nenhuma aplicação encontrada para lidar com a seleção de arquivos.", ex);
            // Toast.makeText(requireContext(), "Por favor instale um gerenciador de arquivos.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data is " + (data == null ? "null" : "not null"));
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            // if (requestCode == YOUR_REQUEST_CODE_FOR_FILE_PICKER) {
            //    Uri fileUri = data.getData();
            //    startUploadService(fileUri);
            // }
        }
    }

    private void startUploadService(Uri fileUri) {
        Log.d(TAG, "startUploadService chamado com URI: " + fileUri + ". currentJsonPath: " + currentJsonPath);
        if (fileUri == null || iaItemTitle == null || currentJsonPath == null || getContext() == null) {
             Log.e(TAG, "startUploadService: Abortando. Informações necessárias ausentes.");
             return;
        }
        // Intent serviceIntent = new Intent(requireContext(), LdUploadService.class);
        // serviceIntent.putExtra("EXTRA_FILE_URI", fileUri.toString());
        // serviceIntent.putExtra("EXTRA_IA_ITEM_TITLE", iaItemTitle);
        // serviceIntent.putExtra("EXTRA_PARENT_JSON_PATH", currentJsonPath);
        // Bundle extras = serviceIntent.getExtras();
        // if (extras != null) {
        //    for (String key : extras.keySet()) {
        //        Log.d(TAG, "startUploadService: Extra: " + key + "=" + extras.get(key));
        //    }
        // }
        // requireContext().startService(serviceIntent);
    }

    public void onRenameFolderRequested(ArchiveFile folderToRename) {
        Log.d(TAG, "onRenameFolderRequested para: " + (folderToRename != null ? folderToRename.name : "pasta nula"));
        if (folderToRename == null) return;
        showRenameFolderDialog(folderToRename);
    }

    private void showRenameFolderDialog(final ArchiveFile folder) {
        Log.d(TAG, "showRenameFolderDialog para: " + (folder != null ? folder.name : "pasta nula"));
        if (folder == null || getContext() == null) return;

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Renomear Pasta");

        final EditText input = new EditText(requireContext()); // Use requireContext()
        input.setText(folder.name);
        input.setHint("Novo nome da pasta");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setMaxLines(1);

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );

        int paddingDp = 20;
        float density = getResources().getDisplayMetrics().density;
        int paddingPx = (int) (paddingDp * density);

        params.leftMargin = paddingPx;
        params.rightMargin = paddingPx;

        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Renomear", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            Log.d(TAG, "showRenameFolderDialog: Botão Renomear clicado. Novo nome: '" + newName + "'");
            if (!newName.isEmpty() && !newName.equals(folder.name)) {
                // internetArchiveService.renameFolderAndIndex(folder.jsonPath, currentJsonPath, newName, iaItemTitle);
                // loadFiles(currentJsonPath); // Refresh
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
