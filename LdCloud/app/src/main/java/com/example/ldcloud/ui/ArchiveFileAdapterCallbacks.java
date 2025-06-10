package com.example.ldcloud.ui;

import com.example.ldcloud.utils.ArchiveFile;

public interface ArchiveFileAdapterCallbacks {
    void onDownloadRequested(ArchiveFile file);
    void onDirectoryClicked(ArchiveFile directory);
    void onDirectoryLongClicked(ArchiveFile directory); // Novo método
}
