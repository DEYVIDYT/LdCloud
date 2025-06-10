package com.example.ldcloud.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.ldcloud.R;
import com.example.ldcloud.utils.ArchiveFile;
import java.util.List;
import java.util.ArrayList;

public class ArchiveFileAdapter extends RecyclerView.Adapter<ArchiveFileAdapter.ViewHolder> {

    private List<ArchiveFile> archiveFiles;
    private ArchiveFileAdapterCallbacks callbacks;

    public ArchiveFileAdapter(List<ArchiveFile> archiveFiles, ArchiveFileAdapterCallbacks callbacks) {
        this.archiveFiles = archiveFiles != null ? archiveFiles : new ArrayList<>();
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_archive_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArchiveFile file = archiveFiles.get(position);
        holder.fileName.setText(file.getName());

        String details;
        if (file.isDirectory()) {
            holder.fileIcon.setImageResource(android.R.drawable.ic_folder); // Standard folder icon
            details = "Folder | Modified: " + file.getLastModifiedDate();
            holder.downloadButton.setVisibility(View.GONE); // Hide download for directories
        } else {
            holder.fileIcon.setImageResource(android.R.drawable.ic_menu_gallery); // Placeholder file icon
            details = "Size: " + file.getSize() + " bytes | Modified: " + file.getLastModifiedDate();
            holder.downloadButton.setVisibility(View.VISIBLE);
            holder.downloadButton.setOnClickListener(v -> {
                if (callbacks != null) {
                    callbacks.onDownloadRequested(file);
                }
            });
        }
        holder.fileDetails.setText(details);
    }

    @Override
    public int getItemCount() {
        return archiveFiles.size();
    }

    public void updateData(List<ArchiveFile> newFiles) {
        this.archiveFiles.clear();
        if (newFiles != null) {
            this.archiveFiles.addAll(newFiles);
        }
        notifyDataSetChanged(); // Consider using DiffUtil for better performance
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView fileIcon;
        TextView fileName;
        TextView fileDetails;
        ImageView downloadButton;

        ViewHolder(View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.image_view_file_icon);
            fileName = itemView.findViewById(R.id.text_view_file_name);
            fileDetails = itemView.findViewById(R.id.text_view_file_details);
            downloadButton = itemView.findViewById(R.id.button_download_file);
        }
    }
}
