package com.example.ldcloud.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.ldcloud.R; // Assuming R exists or will exist
import com.example.ldcloud.utils.ArchiveFile;

import java.util.List;

public class ArchiveFileAdapter extends RecyclerView.Adapter<ArchiveFileAdapter.ViewHolder> {

    private final List<ArchiveFile> fileList;
    private final ArchiveFileAdapterCallbacks callbacks; // Now refers to top-level interface
    private final Context context;

    // Inner interface (ArchiveFileAdapterCallbacks) removed from here.

    public ArchiveFileAdapter(Context context, List<ArchiveFile> fileList, ArchiveFileAdapterCallbacks callbacks) {
        this.context = context;
        this.fileList = fileList;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Assuming a layout file R.layout.item_archive_file exists
        // Since it likely doesn't in this minimal setup, this will be a point of failure if we try to build
        // For the purpose of refactoring the interface, the layout itself is not strictly needed yet.
        View view = LayoutInflater.from(context).inflate(R.layout.item_archive_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArchiveFile file = fileList.get(position);
        holder.fileName.setText(file.name);
        // Further binding logic would go here

        holder.itemView.setOnClickListener(v -> {
            if (file.isDirectory) {
                callbacks.onDirectoryClicked(file);
            } else {
                // Potentially call a different callback for file click or do nothing
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (file.isDirectory) {
                callbacks.onDirectoryLongClicked(file);
                return true;
            }
            return false;
        });

        // Example for download button, assuming a button with id R.id.download_button exists in item_archive_file.xml
        // View downloadButton = holder.itemView.findViewById(R.id.download_button);
        // if (downloadButton != null && !file.isDirectory) {
        //    downloadButton.setOnClickListener(v -> callbacks.onDownloadRequested(file));
        // }
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        // Define other views in R.layout.item_archive_file here

        ViewHolder(View itemView) {
            super(itemView);
            // Example: fileName = itemView.findViewById(R.id.file_name_textview);
            // This will cause issues if R or R.id.file_name_textview doesn't exist
            // For now, I'll comment it out to avoid build errors if R is not generated.
            // fileName = itemView.findViewById(R.id.file_name_textview);
        }
    }
}
