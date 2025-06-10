package com.example.ldcloud.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log; // Adicionado para Log.w
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.example.ldcloud.R;
import com.example.ldcloud.utils.ArchiveFile;

public class FolderActionsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "FolderActionsBottomSheet"; // Adicionado TAG
    private static final String ARG_FOLDER = "arg_folder";
    private ArchiveFile folder;
    private FolderActionsListener listener;

    public interface FolderActionsListener {
        void onRenameFolderRequested(ArchiveFile folder);
        void onDeleteFolderRequested(ArchiveFile folder);
    }

    public static FolderActionsBottomSheet newInstance(ArchiveFile folder) {
        FolderActionsBottomSheet fragment = new FolderActionsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_FOLDER, folder);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Tentar definir o listener a partir do Fragmento pai ou da Activity
        if (getParentFragment() instanceof FolderActionsListener) {
            listener = (FolderActionsListener) getParentFragment();
        } else if (context instanceof FolderActionsListener) {
            // Isso seria para o caso da Activity implementar o listener.
            // No nosso caso, o FilesFragment é o pai e deve implementar.
            // listener = (FolderActionsListener) context;
            Log.w(TAG, "Contexto pai não é o listener esperado, tentando Fragmento pai.");
            // A lógica acima com getParentFragment() é geralmente mais correta para comunicação fragmento-fragmento.
        }
        // Se getParentFragment() não for o listener, e o contexto também não (improvável para nosso design),
        // então o listener permanecerá null. Os botões verificarão isso.
        if (listener == null) {
             Log.w(TAG, "FolderActionsListener não implementado pelo Fragmento pai ou Activity.");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folder = (ArchiveFile) getArguments().getSerializable(ARG_FOLDER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_folder_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton renameButton = view.findViewById(R.id.button_rename_folder);
        MaterialButton deleteButton = view.findViewById(R.id.button_delete_folder);
        TextView folderNameTitle = view.findViewById(R.id.text_view_folder_name_title);

        if (folder != null) {
            folderNameTitle.setText("Ações para: " + folder.name);
        } else {
            folderNameTitle.setText("Ações de Pasta");
        }

        renameButton.setOnClickListener(v -> {
            if (listener != null && folder != null) {
                listener.onRenameFolderRequested(folder);
            } else {
                Log.w(TAG, "Rename listener ou folder é null.");
            }
            dismiss();
        });

        deleteButton.setOnClickListener(v -> {
            if (listener != null && folder != null) {
                listener.onDeleteFolderRequested(folder);
            } else {
                Log.w(TAG, "Delete listener ou folder é null.");
            }
            dismiss();
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null; // Limpar referência ao listener
    }
}
