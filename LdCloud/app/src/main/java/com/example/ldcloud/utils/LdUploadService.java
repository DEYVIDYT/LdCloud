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

// Importações da AWS SDK (serão mais usadas no Passo 3, mas podem ser adicionadas agora)
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
// import com.amazonaws.services.s3.AmazonS3Client; // Se for passar explicitamente para TransferUtility

import com.example.ldcloud.MainActivity; // Para o PendingIntent da Notificação
import com.example.ldcloud.R; // Para ícones de notificação

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LdUploadService extends Service {

    private static final String TAG = "LdUploadService";
    private static final String CHANNEL_ID = "LdUploadChannel";
    private static final int NOTIFICATION_ID_FOREGROUND = 1; // ID para a notificação do foreground service

    private ExecutorService executorService;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private GitHubService gitHubService;
    private TransferUtility transferUtility;
    private AmazonS3Client s3Client; // For TransferUtility

    // SharedPreferences Keys (algumas podem ser duplicadas de SettingsFragment, idealmente usar as de lá)
    private static final String PREFS_NAME = "LdCloudSettings";
    private static final String KEY_IA_ACCESS_KEY = "accessKey";
    private static final String KEY_IA_SECRET_KEY = "secretKey";
    // KEY_IA_ITEM_TITLE, KEY_ROOT_JSON_PATH são passados via Intent ou lidos se necessário

    // Chaves para os extras do Intent
    public static final String EXTRA_FILE_URI = "fileUri";
    public static final String EXTRA_TARGET_S3_KEY = "targetS3Key";
    public static final String EXTRA_FILE_SIZE = "fileSize"; // long
    public static final String EXTRA_IA_ITEM_TITLE = "iaItemTitle";
    public static final String EXTRA_PARENT_JSON_PATH = "parentJsonPath";
    // As credenciais IA e GitHub podem ser lidas das SharedPreferences dentro do serviço
    // ou passadas via Intent se houver necessidade de variar por upload.
    // Por simplicidade, vamos assumir que o serviço as lê das SharedPreferences.

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: LdUploadService criando...");

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        executorService = Executors.newSingleThreadExecutor();
        gitHubService = new GitHubService(this); // GitHubService precisa de Context

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LdUploadService::WakeLock");
            wakeLock.setReferenceCounted(false);
        } else {
            Log.e(TAG, "PowerManager não disponível.");
        }

        // Inicializar AWSMobileClient - essencial para TransferUtility
        // Esta inicialização é assíncrona. Operações que dependem dela devem esperar o callback.
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails userStateDetails) {
                Log.i(TAG, "AWSMobileClient inicializado. Estado do usuário: " + userStateDetails.getUserState());
                // Inicializar TransferUtility aqui, pois depende do AWSMobileClient estar pronto.
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String iaAccessKey = prefs.getString(KEY_IA_ACCESS_KEY, null);
                String iaSecretKey = prefs.getString(KEY_IA_SECRET_KEY, null);

                if (iaAccessKey != null && iaSecretKey != null) {
                    BasicAWSCredentials credentials = new BasicAWSCredentials(iaAccessKey, iaSecretKey);
                    // Usar uma instância de Region, não Regions.
                    s3Client = new AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1));
                    s3Client.setEndpoint("s3.us.archive.org"); // IA S3 Endpoint

                    transferUtility = TransferUtility.builder()
                                        .context(getApplicationContext())
                                        .s3Client(s3Client)
                                        .awsConfiguration(AWSMobileClient.getInstance().getConfiguration()) // Adicionado para consistência
                                        .build();
                    Log.i(TAG, "TransferUtility inicializada com S3Client customizado para IA.");
                } else {
                    Log.e(TAG, "Credenciais do Internet Archive não encontradas. Upload S3 não funcionará.");
                    // Considerar parar o serviço ou notificar o usuário de forma mais robusta.
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Erro ao inicializar AWSMobileClient!", e);
                // Lidar com o erro - talvez parar o serviço ou notificar o usuário
                // que uploads não funcionarão.
            }
        });
        Log.d(TAG, "onCreate: LdUploadService criação concluída.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Recebido comando para iniciar upload.");
        if (intent == null) {
            Log.w(TAG, "onStartCommand: Intent nulo, parando serviço.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Iniciar em primeiro plano com uma notificação inicial
        // O conteúdo da notificação será atualizado conforme o progresso
        startForeground(NOTIFICATION_ID_FOREGROUND, createNotification("Preparando upload...", 0));

        // Adquirir WakeLock antes de iniciar a tarefa no executor
        if (wakeLock != null && !wakeLock.isHeld()) {
            Log.d(TAG, "Adquirindo WakeLock");
            wakeLock.acquire(30*60*1000L /* timeout 30 minutos, por exemplo */);
        }

        // Extrair parâmetros do Intent e iniciar a lógica de upload no executor
        // (A lógica detalhada do upload será no Passo 3)
        final String fileUriString = intent.getStringExtra(EXTRA_FILE_URI);
        final String targetS3Key = intent.getStringExtra(EXTRA_TARGET_S3_KEY);
        final long fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0);
        final String iaItemTitle = intent.getStringExtra(EXTRA_IA_ITEM_TITLE);
        final String parentJsonPath = intent.getStringExtra(EXTRA_PARENT_JSON_PATH);
        final String fileNameForDisplay = targetS3Key != null ? targetS3Key : (fileUriString != null ? new File(Uri.parse(fileUriString).getPath()).getName() : "Arquivo desconhecido");


        Log.i(TAG, "Preparando para submeter tarefa de upload para: " + fileNameForDisplay);
        executorService.submit(() -> {
            if (transferUtility == null) {
                // AWSMobileClient.initialize() é assíncrono. Se não estiver pronto, transferUtility será null.
                // Uma solução mais robusta usaria um CountDownLatch ou um loop com sleep/check
                // para esperar a inicialização antes de prosseguir.
                // Ou, falhar a tarefa de upload se não estiver pronto após um timeout.
                Log.e(TAG, "TransferUtility não inicializado quando a tarefa de upload foi executada. AWSMobileClient.initialize() pode não ter completado ou falhado.");
                // A notificação de erro será tratada dentro de handleUploadAndIndex se transferUtility for null lá.
                 try {
                    // Dar uma pequena chance para a inicialização assíncrona completar
                    Thread.sleep(2000); // Espera 2 segundos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (transferUtility == null) { // Checar novamente
                     handleError("Erro interno: Serviço de upload não pronto.", fileNameForDisplay);
                     return;
                }
            }
            handleUploadAndIndex(fileUriString, fileNameForDisplay, targetS3Key, fileSize, iaItemTitle, parentJsonPath);
        });

        return START_NOT_STICKY;
    }

    private void handleUploadAndIndex(String fileUriString, String fileNameForDisplay, String targetS3Key, long fileSize,
                                      String iaItemTitle, String parentJsonPath) {
        Log.d(TAG, "handleUploadAndIndex: Iniciando para " + fileNameForDisplay + " S3Key: " + targetS3Key);
        Uri fileUri = Uri.parse(fileUriString);
        File fileToUpload;

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
                    // Optionally, update notification with caching progress if it's slow
                }
                Log.i(TAG, "Arquivo copiado para o cache: " + fileToUpload.getAbsolutePath() + ", Bytes: " + totalBytesCopied);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar arquivo do URI para o cache", e);
            handleError("Erro ao preparar arquivo para upload: " + e.getMessage(), fileNameForDisplay);
            return;
        }

        if (transferUtility == null) { // Re-check after potential sleep in onStartCommand
            Log.e(TAG, "TransferUtility não está inicializada ao tentar upload!");
            handleError("Serviço de upload não inicializado corretamente.", fileNameForDisplay);
            if(fileToUpload.exists()) fileToUpload.delete();
            return;
        }

        Log.d(TAG, "Iniciando upload S3 para: " + iaItemTitle + "/" + targetS3Key);
        TransferObserver uploadObserver = transferUtility.upload(
                iaItemTitle,
                targetS3Key,
                fileToUpload
        );

        uploadObserver.setTransferListener(new com.amazonaws.mobileconnectors.s3.transferutility.TransferListener() {
            @Override
            public void onStateChanged(int id, com.amazonaws.mobileconnectors.s3.transferutility.TransferState state) {
                Log.d(TAG, "onStateChanged: " + fileNameForDisplay + " - " + state);
                if (state == com.amazonaws.mobileconnectors.s3.transferutility.TransferState.COMPLETED) {
                    Log.i(TAG, "Upload S3 para " + fileNameForDisplay + " COMPLETO.");
                    updateNotification("Indexando " + fileNameForDisplay + "...", 100);

                    try {
                        JSONObject parentJson = gitHubService.getJsonFileContent(parentJsonPath);
                        if (parentJson == null) {
                            parentJson = new JSONObject();
                            parentJson.put("entries", new org.json.JSONArray());
                        }
                        org.json.JSONArray entries = parentJson.optJSONArray("entries");
                        if (entries == null) entries = new org.json.JSONArray();

                        JSONObject newEntry = new JSONObject();
                        newEntry.put("name", fileNameForDisplay);
                        newEntry.put("type", "file");
                        newEntry.put("ia_s3_key", targetS3Key);
                        newEntry.put("size", String.valueOf(fileToUpload.length())); // Use actual cached file size
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

                        if (gitHubService.updateJsonFile(parentJsonPath, parentJson, "Adicionado arquivo: " + fileNameForDisplay)) {
                            Log.i(TAG, "Indexação no GitHub para " + fileNameForDisplay + " bem-sucedida.");
                            handleSuccess(fileNameForDisplay + " enviado e indexado com sucesso!");
                        } else {
                            Log.e(TAG, "Falha ao indexar " + fileNameForDisplay + " no GitHub.");
                            handleError("Upload S3 OK, mas falha ao indexar no GitHub: " + fileNameForDisplay, fileNameForDisplay);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Erro durante a indexação no GitHub para " + fileNameForDisplay, e);
                        handleError("Erro na indexação GitHub: " + e.getMessage(), fileNameForDisplay);
                    } finally {
                         if(fileToUpload.exists()) fileToUpload.delete();
                    }

                } else if (state == com.amazonaws.mobileconnectors.s3.transferutility.TransferState.FAILED ||
                           state == com.amazonaws.mobileconnectors.s3.transferutility.TransferState.CANCELED) {
                    Log.e(TAG, "Upload S3 para " + fileNameForDisplay + " falhou ou foi cancelado. Estado: " + state);
                    handleError("Falha no upload S3 para " + fileNameForDisplay, fileNameForDisplay);
                    if(fileToUpload.exists()) fileToUpload.delete();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                int progress = (int) ((double) bytesCurrent / bytesTotal * 100);
                updateNotification("Enviando " + fileNameForDisplay + "...", progress);
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e(TAG, "Erro no upload S3 para " + fileNameForDisplay, ex);
                handleError("Erro no upload S3: " + ex.getMessage(), fileNameForDisplay);
                if(fileToUpload.exists()) fileToUpload.delete();
            }
        });
    }

    private void handleError(String errorMessage, String fileNameForDisplay) {
        Log.e(TAG, "Erro no upload de " + fileNameForDisplay + ": " + errorMessage);
        updateNotification("Erro: " + fileNameForDisplay, -1);
        stopForeground(false);
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND + 2, createFinalNotification("Erro no Upload de LdCloud", fileNameForDisplay + ": " + errorMessage, false));
        releaseResourcesAndStop();
    }

    private void handleSuccess(String message) {
        Log.i(TAG, message);
        updateNotification(message, 100);
        stopForeground(false);
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND + 1, createFinalNotification("LdCloud Upload", message, true));
        releaseResourcesAndStop();
    }

    private void releaseResourcesAndStop() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock liberado em releaseResourcesAndStop.");
        }
        Log.d(TAG, "Parando o serviço (stopSelf).");
        stopSelf();
    }

    private Notification createFinalNotification(String title, String text, boolean success) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Usar ícones existentes ou criar/importar ic_success e ic_error
        int icon = success ? R.drawable.ic_upload_fab : android.R.drawable.stat_notify_error;

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
            Log.d(TAG, "Desligando ExecutorService.");
            executorService.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            Log.d(TAG, "Liberando WakeLock.");
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Não é um serviço vinculado
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LdCloud Uploads",
                    NotificationManager.IMPORTANCE_LOW); // LOW para não fazer som, mas ainda visível
            channel.setDescription("Notificações para uploads de arquivos do LdCloud.");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Canal de notificação criado.");
            }
        }
    }

    // Método para criar notificações (será expandido no Passo 3)
    private Notification createNotification(String text, int progress) {
        Intent notificationIntent = new Intent(this, MainActivity.class); // Leva para MainActivity ao clicar
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LdCloud Upload")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_upload_fab) // Usar um ícone de upload do projeto
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true) // Não alertar repetidamente para atualizações de progresso
                .setOngoing(true); // Importante para foreground service

            // Modificado para remover a barra de progresso se progress < 0
            if (progress >= 0) {
                builder.setProgress(100, progress, progress == 0); // true para indeterminado se progresso for 0
            } else {
                builder.setProgress(0,0,false); // Remove a barra para progresso negativo (erro)
        }
        return builder.build();
    }
}
