package com.example.ldcloud.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

// AWS SDK Imports REMOVED

import com.example.ldcloud.MainActivity;
import com.example.ldcloud.R;
import com.example.ldcloud.utils.InternetArchiveUploader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LdUploadService extends Service {

    private static final String TAG = "LdUploadService";
    private static final String CHANNEL_ID = "LdUploadChannel";
    private static final int NOTIFICATION_ID_FOREGROUND = 1;

    private ExecutorService executorService;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private GitHubService gitHubService;

    private static final String PREFS_NAME = "LdCloudSettings";
    private static final String KEY_IA_ACCESS_KEY = "accessKey";
    private static final String KEY_IA_SECRET_KEY = "secretKey";

    public static final String EXTRA_FILE_URI = "fileUri";
    public static final String EXTRA_TARGET_S3_KEY = "targetS3Key";
    public static final String EXTRA_FILE_SIZE = "fileSize"; // This is the original file size
    public static final String EXTRA_IA_ITEM_TITLE = "iaItemTitle";
    public static final String EXTRA_PARENT_JSON_PATH = "parentJsonPath";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: LdUploadService criando...");
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        executorService = Executors.newSingleThreadExecutor();
        gitHubService = new GitHubService(this);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LdUploadService::WakeLock");
            wakeLock.setReferenceCounted(false);
        } else {
            Log.e(TAG, "PowerManager não disponível.");
        }
        Log.d(TAG, "onCreate: LdUploadService criação concluída (sem init AWS SDK).");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: SERVIÇO CHAMADO! Intent: " + intent);
        if (intent == null) {
            Log.w(TAG, "onStartCommand: Intent nulo, parando serviço.");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "onStartCommand: Chamando startForeground com NOTIFICATION_ID_FOREGROUND: " + NOTIFICATION_ID_FOREGROUND);
        try {
            Notification initialNotification = buildNotification("Preparando upload...", 0);
            startForeground(NOTIFICATION_ID_FOREGROUND, initialNotification);
            Log.i(TAG, "onStartCommand: Serviço iniciado em primeiro plano com notificação 'Preparando upload...'.");
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand: ERRO AO CHAMAR startForeground!", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (wakeLock != null && !wakeLock.isHeld()) {
            Log.d(TAG, "onStartCommand: Adquirindo WakeLock.");
            wakeLock.acquire(30 * 60 * 1000L);
        } else if (wakeLock == null) {
            Log.w(TAG, "onStartCommand: WakeLock não inicializado.");
        }

        final String fileUriString = intent.getStringExtra(EXTRA_FILE_URI);
        final String targetS3Key = intent.getStringExtra(EXTRA_TARGET_S3_KEY);
        final long originalFileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0); // Use this for metadata
        final String iaItemTitleFromIntent = intent.getStringExtra(EXTRA_IA_ITEM_TITLE);
        final String parentJsonPath = intent.getStringExtra(EXTRA_PARENT_JSON_PATH);
        final String fileNameForDisplay = targetS3Key != null ? targetS3Key : (fileUriString != null ? new File(Uri.parse(fileUriString).getPath()).getName() : "Arquivo desconhecido");

        Log.i(TAG, "Preparando para submeter tarefa de upload para: " + fileNameForDisplay);
        executorService.submit(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String iaAccessKey = prefs.getString(KEY_IA_ACCESS_KEY, null);
            String iaSecretKey = prefs.getString(KEY_IA_SECRET_KEY, null);

            if (iaAccessKey == null || iaAccessKey.isEmpty() ||
                iaSecretKey == null || iaSecretKey.isEmpty() ||
                iaItemTitleFromIntent == null || iaItemTitleFromIntent.isEmpty() ||
                fileUriString == null ) {
                Log.e(TAG, "Configurações/parâmetros ausentes para upload. Abortando. IA Key: " + (iaAccessKey != null) + ", Secret Key: " + (iaSecretKey != null) + ", Item: " + iaItemTitleFromIntent + ", URI: " + fileUriString);
                handleError("Configurações do Internet Archive ou parâmetros de arquivo incompletos.", fileNameForDisplay);
                return;
            }
            handleUploadAndIndex(fileUriString, fileNameForDisplay, targetS3Key, originalFileSize,
                                 iaItemTitleFromIntent, iaAccessKey, iaSecretKey, parentJsonPath);
        });

        return START_NOT_STICKY;
    }

    private void handleUploadAndIndex(String fileUriString, String fileNameForDisplay, String targetS3Key, long originalFileSize,
                                      String iaItemTitle, String iaAccessKey, String iaSecretKey, String parentJsonPath) {
        Log.i(TAG, "handleUploadAndIndex: Usando InternetArchiveUploader para " + fileNameForDisplay + " em item: " + iaItemTitle);
        Uri fileUri = Uri.parse(fileUriString);
        File tempFileToUpload = null;

        try {
            // Criar arquivo temporário a partir do URI para passar para InternetArchiveUploader
            String tempFileName = "ldcloud_temp_" + System.currentTimeMillis();
            String extension = getFileExtension(fileNameForDisplay);
            if (extension != null && !extension.isEmpty()) tempFileName += "." + extension;
            tempFileToUpload = new File(getCacheDir(), tempFileName);

            try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                 FileOutputStream fos = new FileOutputStream(tempFileToUpload)) {
                if (inputStream == null) {
                    throw new IOException("Não foi possível abrir InputStream para URI: " + fileUri);
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                Log.i(TAG, "Arquivo copiado para cache para upload: " + tempFileToUpload.getAbsolutePath() + ", Tamanho: " + tempFileToUpload.length());
            }

            final File finalFileToUpload = tempFileToUpload;

            InternetArchiveUploader uploader = new InternetArchiveUploader(iaAccessKey, iaSecretKey);

            uploader.uploadFile(iaItemTitle, targetS3Key, finalFileToUpload, new InternetArchiveUploader.UploadCallback() {
                @Override
                public void onProgress(int progress) {
                    LdUploadService.this.updateNotification("Enviando " + fileNameForDisplay + "...", progress);
                }

                @Override
                public void onSuccess(String fileUrl) { // Parameter name 'fileUrl' is fine, type String matches 'message'
                    Log.i(TAG, "Upload para IA bem-sucedido para " + fileNameForDisplay + ". URL: " + fileUrl);
                    LdUploadService.this.updateNotification("Indexando " + fileNameForDisplay + "...", 100);

                    try {
                        JSONObject parentJson = gitHubService.getJsonFileContent(parentJsonPath);
                        JSONArray entries;
                        if (parentJson == null) {
                            Log.i(TAG, "uploadFileAndIndex: JSON pai não encontrado em " + parentJsonPath + ". Criando novo JSON pai em memória.");
                            parentJson = new JSONObject();
                            entries = new JSONArray();
                            parentJson.put("entries", entries);
                        } else {
                            entries = parentJson.optJSONArray("entries");
                            if (entries == null) {
                                entries = new JSONArray();
                                parentJson.put("entries", entries);
                            }
                        }

                        JSONObject newEntry = new JSONObject();
                        newEntry.put("name", fileNameForDisplay);
                        newEntry.put("type", "file");
                        newEntry.put("ia_s3_key", targetS3Key);
                        newEntry.put("ia_url", fileUrl);
                        newEntry.put("size", String.valueOf(originalFileSize));
                        newEntry.put("last_modified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

                        // Adicionar ou atualizar lógica (se necessário, por enquanto apenas adiciona)
                        boolean entryExists = false;
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject currentEntry = entries.getJSONObject(i);
                            if (fileNameForDisplay.equals(currentEntry.optString("name")) && "file".equals(currentEntry.optString("type"))) {
                                entries.put(i, newEntry); // Atualiza a entrada existente
                                entryExists = true;
                                break;
                            }
                        }
                        if (!entryExists) {
                            entries.put(newEntry); // Adiciona nova entrada
                        }
                        // parentJson.put("entries", entries); // Não é necessário se 'entries' for uma referência direta

                        if (gitHubService.updateJsonFile(parentJsonPath, parentJson, "Adicionado/Atualizado arquivo LdCloud: " + fileNameForDisplay)) {
                            Log.i(TAG, "Indexação no GitHub para " + fileNameForDisplay + " bem-sucedida.");
                            LdUploadService.this.handleSuccess(fileNameForDisplay + " enviado e indexado!");
                        } else {
                            Log.e(TAG, "Falha ao indexar " + fileNameForDisplay + " no GitHub.");
                            LdUploadService.this.handleError("Upload IA OK, mas falha ao indexar no GitHub: " + fileNameForDisplay, fileNameForDisplay);
                        }
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Erro durante a indexação no GitHub para " + fileNameForDisplay, e);
                        LdUploadService.this.handleError("Erro na indexação GitHub: " + e.getMessage(), fileNameForDisplay);
                    } finally {
                         if (finalFileToUpload != null && finalFileToUpload.exists()) {
                            finalFileToUpload.delete();
                            Log.d(TAG, "Arquivo temporário de upload deletado: " + finalFileToUpload.getName());
                        }
                    }
                }

                @Override
                public void onFailure(String errorMessage) { // Corrigido de onError para onFailure
                    Log.e(TAG, "Falha no upload para IA (" + fileNameForDisplay + "): " + errorMessage);
                    LdUploadService.this.handleError("Falha no upload para IA: " + errorMessage, fileNameForDisplay);
                    if (finalFileToUpload != null && finalFileToUpload.exists()) {
                        finalFileToUpload.delete();
                        Log.d(TAG, "Arquivo temporário de upload deletado devido a erro: " + finalFileToUpload.getName());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro em handleUploadAndIndex ao preparar ou iniciar upload para " + fileNameForDisplay, e);
            handleError("Erro ao preparar upload: " + e.getMessage(), fileNameForDisplay);
            if (tempFileToUpload != null && tempFileToUpload.exists()) { // Limpar se a cópia falhou
                 tempFileToUpload.delete();
            }
        }
        // InputStream é fechado pelo try-with-resources na cópia.
    }

    private void handleError(String errorMessage, String fileNameForDisplay) {
        Log.e(TAG, "handleError: Para arquivo '" + fileNameForDisplay + "', Erro: " + errorMessage);
        updateNotification("Erro: " + fileNameForDisplay, -1);
        Log.d(TAG, "handleError: Chamando stopForeground(false).");
        stopForeground(false);
        Log.d(TAG, "handleError: Postando notificação final de erro.");
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID_FOREGROUND + java.util.UUID.randomUUID().hashCode(),
                createFinalNotification("Erro no Upload de LdCloud", fileNameForDisplay + ": " + errorMessage, false));
        }
        releaseResourcesAndStop();
    }

    private void handleSuccess(String message) {
        Log.i(TAG, "handleSuccess: " + message);
        updateNotification(message, 100);
        Log.d(TAG, "handleSuccess: Chamando stopForeground(false).");
        stopForeground(false);
        Log.d(TAG, "handleSuccess: Postando notificação final de sucesso.");
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID_FOREGROUND + java.util.UUID.randomUUID().hashCode(),
                createFinalNotification("LdCloud Upload", message, true));
        }
        releaseResourcesAndStop();
    }

    private void releaseResourcesAndStop() {
        if (wakeLock != null && wakeLock.isHeld()) {
            Log.d(TAG, "releaseResourcesAndStop: Liberando WakeLock.");
            wakeLock.release();
        }
        Log.d(TAG, "releaseResourcesAndStop: Chamando stopSelf().");
        stopSelf();
    }

    private Notification createFinalNotification(String title, String text, boolean success) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int icon = success ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: LdUploadService destruindo...");
        if (executorService != null && !executorService.isShutdown()) {
            Log.d(TAG, "onDestroy: Desligando ExecutorService.");
            executorService.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            Log.d(TAG, "onDestroy: Liberando WakeLock se ainda estiver retido.");
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LdCloud Uploads",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificações para uploads de arquivos do LdCloud.");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.i(TAG, "Canal de notificação criado/atualizado: " + CHANNEL_ID);
            }
        }
    }

    private void updateNotification(String text, int progress) {
        Log.d(TAG, "updateNotification: Atualizando notificação ID " + NOTIFICATION_ID_FOREGROUND + " Texto: '" + text + "', Progresso: " + progress);
        Notification notification = buildNotification(text, progress);
        if (notificationManager != null) {
            Log.d(TAG, "updateNotification: Notificando com NotificationManager.");
            notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification);
        }
    }

    private Notification buildNotification(String text, int progress) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LdCloud Upload")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

            if (progress >= 0) {
                builder.setProgress(100, progress, progress == 0);
            } else {
                builder.setProgress(0,0,false);
        }
        return builder.build();
    }
}
