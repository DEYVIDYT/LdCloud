package com.example.ldcloud.utils;

import android.util.Base64;
import android.util.Log; // Adicionado para logging

import org.json.JSONObject;
import org.json.JSONException; // Adicionado para tratar JSONException

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException; // Adicionado para tratar NoSuchAlgorithmException
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class InternetArchiveUploader {

    private static final String TAG = "IAUploader"; // Tag para logging

    public interface UploadCallback {
        void onProgress(int progress);
        void onSuccess(String message);
        void onFailure(String errorMessage);
    }

    private String accessKey;
    private String secretKey;

    public InternetArchiveUploader(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    private String generateSignature(String message) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key_spec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key_spec);
            byte[] hash = sha256_HMAC.doFinal(message.getBytes());
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar assinatura HMAC-SHA256", e);
            return null;
        }
    }

    private String calculateAndGetMd5ForInternalFile(File internalFile) {
        try (InputStream fis = new FileInputStream(internalFile)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Erro ao calcular MD5 do arquivo interno", e);
            return null;
        }
    }


    public boolean uploadFile(String itemId, String remoteFileName, File fileToUpload, UploadCallback callback) {
        HttpURLConnection connection = null;
        try {
            // 1. (Opcional) Criar o item se ele não existir - pode ser feito separadamente
            // if (!checkOrCreateItem(itemId)) {
            //     callback.onFailure("Falha ao verificar/criar item no Internet Archive.");
            //     return false;
            // }

            // 2. Configurar a conexão para o upload do arquivo
            URL url = new URL("https://s3.us.archive.org/" + itemId + "/" + remoteFileName);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true); // Essencial para PUT com corpo
            connection.setUseCaches(false);

            // Cabeçalhos necessários
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String dateHeader = sdf.format(new Date());

            String fileMd5 = calculateAndGetMd5ForInternalFile(fileToUpload);
            if (fileMd5 == null) {
                callback.onFailure("Não foi possível calcular o MD5 do arquivo.");
                return false;
            }

            connection.setRequestProperty("Date", dateHeader);
            connection.setRequestProperty("Content-MD5", fileMd5);
            connection.setRequestProperty("Content-Type", "application/octet-stream"); // Ou tipo MIME específico
            connection.setRequestProperty("Content-Length", String.valueOf(fileToUpload.length()));

            // Cabeçalhos x-archive-meta-* (opcional, mas útil para metadados)
            // connection.setRequestProperty("x-archive-meta-title", "Título do Arquivo");
            // connection.setRequestProperty("x-archive-meta-collection", "mediatype:data"); // Exemplo

            // Cabeçalho de Autorização
            String stringToSign = "PUT\n" +
                                  fileMd5 + "\n" +
                                  "application/octet-stream" + "\n" +
                                  dateHeader + "\n" +
                                  // Adicionar cabeçalhos x-amz-* e x-archive-* aqui se usados, em ordem alfabética
                                  // Ex: "x-archive-meta-collection:mediatype:data\n" +
                                  //     "x-archive-meta-title:Título do Arquivo\n" +
                                  "/" + itemId + "/" + remoteFileName;

            String signature = generateSignature(stringToSign);
            if (signature == null) {
                callback.onFailure("Falha ao gerar assinatura de autorização.");
                return false;
            }
            connection.setRequestProperty("Authorization", "LOW " + accessKey + ":" + signature);

            Log.d(TAG, "Iniciando upload para: " + url.toString());
            Log.d(TAG, "Cabeçalhos: " + connection.getRequestProperties().toString());
            Log.d(TAG, "String para Assinar: \n" + stringToSign);


            // 3. Enviar o arquivo
            connection.setFixedLengthStreamingMode(fileToUpload.length()); // Para arquivos grandes

            try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
                 FileInputStream fis = new FileInputStream(fileToUpload)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesSent = 0;
                long fileSize = fileToUpload.length();

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    totalBytesSent += bytesRead;
                    if (callback != null && fileSize > 0) {
                        int progress = (int) ((totalBytesSent * 100) / fileSize);
                        callback.onProgress(progress);
                    }
                }
                dos.flush();
            }

            // 4. Verificar resposta
            int responseCode = connection.getResponseCode();
            Log.i(TAG, "Código de Resposta do Upload: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) { // Sucesso (200 OK, 201 Created, 204 No Content)
                if (callback != null) callback.onSuccess("Arquivo '" + remoteFileName + "' enviado com sucesso para o item " + itemId);
                return true;
            } else {
                String errorMessage = "Falha no upload: " + responseCode + " " + connection.getResponseMessage();
                // Ler corpo do erro se houver
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        errorMessage += " - Detalhes: " + sb.toString();
                    }
                }
                Log.e(TAG, errorMessage);
                if (callback != null) callback.onFailure(errorMessage);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Exceção durante o upload", e);
            if (callback != null) callback.onFailure("Exceção: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Método para criar um item (se não existir) - simplificado
    // Em um app real, isso teria mais tratamento de erro e talvez verificaria a existência primeiro.
    public boolean createItem(String itemId, String title) {
        HttpURLConnection connection = null;
        try {
            // Criar um arquivo _meta.xml simples para o item
            File metaXmlFile = new File(System.getProperty("java.io.tmpdir"), itemId + "_meta.xml");
            try (FileWriter writer = new FileWriter(metaXmlFile)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<metadata>\n");
                writer.write("  <identifier>" + itemId + "</identifier>\n");
                writer.write("  <title>" + title + "</title>\n");
                writer.write("  <mediatype>data</mediatype>\n"); // Ou outro mediatype
                // Adicionar mais metadados se necessário (creator, date, etc.)
                writer.write("</metadata>\n");
            }

            // Configurar a conexão para o upload do _meta.xml
            // O endpoint para criar/atualizar metadados de um item é o próprio item.
            // O upload é feito para um arquivo especial como _meta.xml ou via cabeçalhos.
            // Aqui, vamos tentar fazer upload de _meta.xml para o item.
            // A documentação do IA pode ter detalhes mais específicos sobre a criação programática de itens.
            // Esta é uma abordagem simplificada.

            // Para criar o item, geralmente se faz um PUT para o nome do item com os metadados.
            // O nome do arquivo aqui é arbitrário para o PUT, mas os cabeçalhos x-archive-meta-* são importantes.
            URL url = new URL("https://s3.us.archive.org/" + itemId + "/" + itemId + "_meta.xml"); // Ou apenas itemId?
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String dateHeader = sdf.format(new Date());

            connection.setRequestProperty("Date", dateHeader);
            connection.setRequestProperty("Content-Type", "text/xml");
            connection.setRequestProperty("Content-Length", String.valueOf(metaXmlFile.length()));

            // Cabeçalhos essenciais para criação/definição de metadados do item
            connection.setRequestProperty("x-archive-auto-make-bucket", "1"); // Pede para criar o item se não existir
            connection.setRequestProperty("x-archive-meta-title", title);
            connection.setRequestProperty("x-archive-meta-collection", "mediatype:data");
            // Outros metadados...
            // connection.setRequestProperty("x-archive-meta-creator", "LdCloud App");
            // connection.setRequestProperty("x-archive-meta-date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));


            String stringToSign = "PUT\n" +
                                  "" + "\n" + // MD5 do corpo (vazio se metadados só nos headers, ou MD5 do _meta.xml se enviado)
                                  "text/xml" + "\n" +
                                  dateHeader + "\n" +
                                  "x-archive-auto-make-bucket:1\n" +
                                  "x-archive-meta-collection:mediatype:data\n" +
                                  "x-archive-meta-title:" + title + "\n" +
                                  // Adicionar outros x-archive-meta-* aqui em ordem alfabética
                                  "/" + itemId + "/" + itemId + "_meta.xml"; // Ou o path do recurso sendo acessado

            String signature = generateSignature(stringToSign);
            if (signature == null) return false;
            connection.setRequestProperty("Authorization", "LOW " + accessKey + ":" + signature);

            Log.d(TAG, "Tentando criar/atualizar item: " + itemId + " com título: " + title);
            Log.d(TAG, "String para assinar (criação item): \n" + stringToSign);

            // Enviar o _meta.xml (se essa for a abordagem, ou corpo vazio se metadados só nos headers)
             try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
                 FileInputStream fis = new FileInputStream(metaXmlFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();
            }

            int responseCode = connection.getResponseCode();
            Log.i(TAG, "Resposta da criação/atualização do item " + itemId + ": " + responseCode);

            if (!metaXmlFile.delete()) { Log.w(TAG, "Não foi possível deletar arquivo temporário _meta.xml");}

            return (responseCode >= 200 && responseCode < 300);

        } catch (Exception e) {
            Log.e(TAG, "Exceção ao criar/atualizar item " + itemId, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
