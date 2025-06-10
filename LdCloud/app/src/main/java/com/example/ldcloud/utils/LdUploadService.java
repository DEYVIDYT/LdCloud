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

import com.amazonaws.auth.BasicAWSCredentials;
// import com.amazonaws.mobile.client.AWSMobileClient; // Removido
// import com.amazonaws.mobile.client.Callback; // Removido
// import com.amazonaws.mobile.client.UserStateDetails; // Removido
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import com.example.ldcloud.MainActivity;
import com.example.ldcloud.R;

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
    private static final int NOTIFICATION_ID_ERROR_INIT = 2;

    private ExecutorService executorService;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private GitHubService gitHubService;
    private TransferUtility transferUtility;
    private AmazonS3Client s3Client;

    private static final String PREFS_NAME = "LdCloudSettings";
    private static final String KEY_IA_ACCESS_KEY = "accessKey";
    private static final String KEY_IA_SECRET_KEY = "secretKey";

    private volatile boolean awsComponentsInitialized = false;

    public static final String EXTRA_FILE_URI = "fileUri";
    public static final String EXTRA_TARGET_S3_KEY = "targetS3Key";
    public static final String EXTRA_FILE_SIZE = "fileSize";
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

        // Inicialização Direta de S3Client e TransferUtility
        Log.d(TAG, "onCreate: Tentando inicializar S3Client e TransferUtility diretamente.");
        this.awsComponentsInitialized = false; // Começa como falso por padrão
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String iaAccessKey = prefs.getString(KEY_IA_ACCESS_KEY, null);
            String iaSecretKey = prefs.getString(KEY_IA_SECRET_KEY, null);

            if (iaAccessKey != null && !iaAccessKey.isEmpty() && iaSecretKey != null && !iaSecretKey.isEmpty()) {
                BasicAWSCredentials credentials = new BasicAWSCredentials(iaAccessKey, iaSecretKey);
                s3Client = new AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1));
                s3Client.setEndpoint("s3.us.archive.org");
                Log.i(TAG, "AmazonS3Client para IA configurado diretamente.");

                transferUtility = TransferUtility.builder()
                                .context(getApplicationContext())
                                .s3Client(s3Client)
                                // .awsConfiguration(AWSMobileClient.getInstance().getConfiguration()) // Removido pois AWSMobileClient não é mais inicializado aqui
                                .build();
                Log.i(TAG, "TransferUtility inicializada diretamente com S3Client customizado para IA.");
                this.awsComponentsInitialized = true;
                Log.i(TAG, "Componentes AWS (S3Client e TransferUtility) inicializados diretamente com sucesso.");
            } else {
                Log.e(TAG, "Falha ao inicializar componentes AWS diretamente: credenciais IA ausentes ou incompletas.");
                // this.awsComponentsInitialized permanece false
                handleEarlyInitializationError("Credenciais do Internet Archive ausentes ou incompletas. Verifique as Configurações.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exceção ao inicializar S3Client/TransferUtility diretamente", e);
            // this.awsComponentsInitialized permanece false
            handleEarlyInitializationError("Erro ao configurar S3/TransferUtility: " + e.getMessage());
        }

        Log.d(TAG, "onCreate: LdUploadService criação concluída.");
    }

    private void handleEarlyInitializationError(String errorMessage) {
        Log.e(TAG, "handleEarlyInitializationError: " + errorMessage);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Erro de Inicialização LdCloud")
                .setContentText(errorMessage)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
             notificationManager.notify(NOTIFICATION_ID_ERROR_INIT, builder.build());
        }

        Log.d(TAG, "Parando o serviço devido a erro de inicialização.");
        stopSelf();
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

        Log.d(TAG, "onStartCommand: Extraindo extras do Intent...");
        final String fileUriString = intent.getStringExtra(EXTRA_FILE_URI);
        Log.d(TAG, "  EXTRA_FILE_URI: " + fileUriString);

        final String targetS3Key = intent.getStringExtra(EXTRA_TARGET_S3_KEY);
        Log.d(TAG, "  EXTRA_TARGET_S3_KEY: " + targetS3Key);

        final long fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0);
        Log.d(TAG, "  EXTRA_FILE_SIZE: " + fileSize);

        final String iaItemTitle = intent.getStringExtra(EXTRA_IA_ITEM_TITLE);
        Log.d(TAG, "  EXTRA_IA_ITEM_TITLE: " + iaItemTitle);

        final String parentJsonPath = intent.getStringExtra(EXTRA_PARENT_JSON_PATH);
        Log.d(TAG, "  EXTRA_PARENT_JSON_PATH: " + parentJsonPath);

        final String fileNameForDisplay = targetS3Key != null ? targetS3Key : (fileUriString != null ? new java.io.File(Uri.parse(fileUriString).getPath()).getName() : "Arquivo desconhecido");
        Log.d(TAG, "  fileNameForDisplay: " + fileNameForDisplay);

        if (fileUriString == null || targetS3Key == null || iaItemTitle == null || parentJsonPath == null) {
            Log.e(TAG, "onStartCommand: Um ou mais parâmetros essenciais são nulos. Abortando tarefa de upload.");
            handleError("Parâmetros de upload inválidos.", fileNameForDisplay);
            return START_NOT_STICKY;
        }

        // Log.d(TAG, "onStartCommand: Preparando para submeter tarefa ao executorService.");
        executorService.submit(() -> { // This will be uncommented in the next step
            if (!awsComponentsInitialized || transferUtility == null) {
                Log.e(TAG, "Upload não pode prosseguir: Componentes AWS não estão inicializados. awsComponentsInitialized=" + awsComponentsInitialized + ", transferUtilityIsNull=" + (transferUtility == null));
                handleError("Serviço de upload não está pronto. Tente novamente em alguns instantes.", fileNameForDisplay);
                return;
            }
            handleUploadAndIndex(fileUriString, fileNameForDisplay, targetS3Key, fileSize, iaItemTitle, parentJsonPath);
        });

        return START_NOT_STICKY;
    }

    private void handleUploadAndIndex(String fileUriString, String fileNameForDisplay, String targetS3Key, long fileSize,
                                      String iaItemTitle, String parentJsonPath) {
        Log.i(TAG, "handleUploadAndIndex: Iniciado para arquivo: " + fileNameForDisplay + ", URI: " + fileUriString + ", S3Key: " + targetS3Key);
        Uri fileUri = Uri.parse(fileUriString);
        File fileToUpload = null;

        try {
            String tempFileName = "upload_temp_" + System.currentTimeMillis();
            String extension = getFileExtension(fileNameForDisplay);
            if (extension != null && !extension.isEmpty()) tempFileName += "." + extension;

            fileToUpload = new File(getCacheDir(), tempFileName);
            try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                 FileOutputStream outputStream = new FileOutputStream(fileToUpload)) {
                if (inputStream == null) throw new IOException("InputStream nulo para URI: " + fileUri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesCopied = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesCopied += bytesRead;
                }
                Log.i(TAG, "Arquivo copiado para o cache: " + fileToUpload.getAbsolutePath() + ", Bytes: " + totalBytesCopied);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar arquivo do URI para o cache", e);
            handleError("Erro ao preparar arquivo para upload: " + e.getMessage(), fileNameForDisplay);
            if (fileToUpload != null && fileToUpload.exists()) {
                fileToUpload.delete();
            }
            return;
        }

        if (!awsComponentsInitialized || transferUtility == null) {
            Log.e(TAG, "Upload não pode prosseguir dentro de handleUploadAndIndex: Componentes AWS não estão inicializados (transferUtility é nulo).");
            handleError("Serviço de upload não pronto (interno - transferUtility nulo).", fileNameForDisplay);
            if (fileToUpload != null && fileToUpload.exists()) {
                fileToUpload.delete();
            }
            return;
        }

        final File finalFileToUpload = fileToUpload;
        Log.d(TAG, "handleUploadAndIndex: Chamando transferUtility.upload() para " + iaItemTitle + "/" + targetS3Key);
        TransferObserver uploadObserver = transferUtility.upload(
                iaItemTitle,
                targetS3Key,
                finalFileToUpload
        );

        uploadObserver.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.d(TAG, "TransferListener.onStateChanged: " + fileNameForDisplay + " - " + state);
                if (state == TransferState.COMPLETED) {
                    Log.i(TAG, "S3 Upload COMPLETED para " + fileNameForDisplay + ". Iniciando indexação no GitHub.");
                    LdUploadService.this.updateNotification("Indexando " + fileNameForDisplay + "...", 100);

                    try {
                        JSONObject parentJson = gitHubService.getJsonFileContent(parentJsonPath);
                        if (parentJson == null) {
                            Log.i(TAG, "uploadFileAndIndex: JSON pai não encontrado em " + parentJsonPath + ". Criando novo JSON pai em memória.");
                            parentJson = new JSONObject();
                            parentJson.put("entries", new JSONArray());
                        }
                        JSONArray entries = parentJson.optJSONArray("entries");
                        if (entries == null) entries = new JSONArray();

                        JSONObject newEntry = new JSONObject();
                        newEntry.put("name", fileNameForDisplay);
                        newEntry.put("type", "file");
                        newEntry.put("ia_s3_key", targetS3Key);
                        newEntry.put("size", String.valueOf(finalFileToUpload.length()));
                        newEntry.put("last_modified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

                        boolean entryUpdated = false;
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject currentEntry = entries.getJSONObject(i);
                            if (fileNameForDisplay.equals(currentEntry.optString("name")) && "file".equals(currentEntry.optString("type"))) {
                                entries.put(i, newEntry);
                                entryUpdated = true;
                                break;
                            }
                        }
                        if (!entryUpdated) {
                            entries.put(newEntry);
                        }
                        parentJson.put("entries", entries);

                        boolean githubUpdateSuccess = gitHubService.updateJsonFile(parentJsonPath, parentJson, "Adicionado arquivo: " + fileNameForDisplay);
                        Log.d(TAG, "uploadFileAndIndex: Atualização do JSON " + parentJsonPath + (githubUpdateSuccess ? " bem-sucedida." : " falhou."));
                        if (githubUpdateSuccess) {
                            handleSuccess(fileNameForDisplay + " enviado e indexado com sucesso!");
                        } else {
                            handleError("Upload S3 OK, mas falha ao indexar no GitHub: " + fileNameForDisplay, fileNameForDisplay);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Erro durante a indexação no GitHub para " + fileNameForDisplay, e);
                        handleError("Erro na indexação GitHub: " + e.getMessage(), fileNameForDisplay);
                    } finally {
                         if(finalFileToUpload.exists()) finalFileToUpload.delete();
                    }

                } else if (state == TransferState.FAILED || state == TransferState.CANCELED) {
                    Log.e(TAG, "Upload S3 para " + fileNameForDisplay + " falhou ou foi cancelado. Estado: " + state);
                    handleError("Falha no upload S3 para " + fileNameForDisplay, fileNameForDisplay);
                    if(finalFileToUpload.exists()) finalFileToUpload.delete();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                int progress = (int) ((double) bytesCurrent / bytesTotal * 100);
                Log.v(TAG, "TransferListener.onProgressChanged: " + fileNameForDisplay + " - " + progress + "% (" + bytesCurrent + "/" + bytesTotal + ")");
                LdUploadService.this.updateNotification("Enviando " + fileNameForDisplay + "...", progress);
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e(TAG, "Erro no upload S3 para " + fileNameForDisplay, ex);
                handleError("Erro no upload S3: " + ex.getMessage(), fileNameForDisplay);
                if(finalFileToUpload.exists()) finalFileToUpload.delete();
            }
        });
    }

    private void handleError(String errorMessage, String fileNameForDisplay) {
        Log.e(TAG, "handleError: Para arquivo '" + fileNameForDisplay + "', Erro: " + errorMessage);
        updateNotification("Erro: " + fileNameForDisplay, -1);
        Log.d(TAG, "handleError: Chamando stopForeground(false).");
        stopForeground(false);
        Log.d(TAG, "handleError: Postando notificação final de erro.");
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND + 2, createFinalNotification("Erro no Upload de LdCloud", fileNameForDisplay + ": " + errorMessage, false));
        releaseResourcesAndStop();
    }

    private void handleSuccess(String message) {
        Log.i(TAG, "handleSuccess: " + message);
        updateNotification(message, 100);
        Log.d(TAG, "handleSuccess: Chamando stopForeground(false).");
        stopForeground(false);
        Log.d(TAG, "handleSuccess: Postando notificação final de sucesso.");
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND + 1, createFinalNotification("LdCloud Upload", message, true));
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
