package com.example.ldcloud.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64; // Para decodificar conteúdo do GitHub
import android.util.Log;

import org.json.JSONObject; // Para manipular JSON

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GitHubService {
    private static final String TAG = "GitHubService";
    private static final String API_BASE_URL = "https://api.github.com/repos/";

    private OkHttpClient httpClient;
    private String owner;
    private String repo;
    private String token; // PAT do GitHub
    private Context context;

    // Chaves de SharedPreferences (devem corresponder às de SettingsFragment)
    private static final String PREFS_NAME = "LdCloudSettings";
    private static final String KEY_GITHUB_REPO = "github_repo";
    private static final String KEY_GITHUB_TOKEN = "github_token";

    public GitHubService(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        loadGitHubCredentials();
    }

    private void loadGitHubCredentials() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String fullRepo = sharedPreferences.getString(KEY_GITHUB_REPO, "");
        this.token = sharedPreferences.getString(KEY_GITHUB_TOKEN, "");

        if (!fullRepo.isEmpty() && fullRepo.contains("/")) {
            String[] parts = fullRepo.split("/");
            this.owner = parts[0];
            this.repo = parts[1];
        } else {
            Log.e(TAG, "Repositório GitHub não configurado corretamente (formato esperado: usuario/repositorio)");
            this.owner = null;
            this.repo = null;
        }
    }

    // Método para buscar conteúdo de arquivo JSON
    public JSONObject getJsonFileContent(String filePath) throws IOException {
        if (owner == null || repo == null) {
            throw new IOException("Owner ou repositório do GitHub não configurado.");
        }
        if (token == null || token.isEmpty()) {
             Log.w(TAG, "Token do GitHub não fornecido. Tentando acesso público.");
             // Algumas APIs podem funcionar sem token para repositórios públicos, mas é melhor ter.
        }

        String url = API_BASE_URL + owner + "/" + repo + "/contents/" + filePath;
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (token != null && !token.isEmpty()) {
            requestBuilder.addHeader("Authorization", "token " + token);
        }
        requestBuilder.addHeader("Accept", "application/vnd.github.v3+json"); // API version

        Request request = requestBuilder.build();
        Log.d(TAG, "Requesting URL: " + url);

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                Log.e(TAG, "Falha ao buscar arquivo do GitHub: " + response.code() + " - " + errorBody);
                throw new IOException("Falha ao buscar arquivo do GitHub: " + response.code() + " " + errorBody);
            }

            String responseString = responseBody.string();
            JSONObject jsonResponse = new JSONObject(responseString);
            String contentBase64 = jsonResponse.optString("content");
            if (contentBase64.isEmpty()) {
                 // Pode ser um diretório ou arquivo vazio não encontrado como esperado
                if ("Not Found".equals(jsonResponse.optString("message"))){
                     Log.e(TAG, "Arquivo não encontrado no GitHub: " + filePath);
                     return null; // Ou lançar uma exceção específica
                }
                Log.e(TAG, "Conteúdo do arquivo está vazio ou não é um arquivo: " + filePath);
                throw new IOException("Conteúdo do arquivo está vazio ou não é um arquivo: " + filePath);
            }

            byte[] decodedBytes = Base64.decode(contentBase64.replaceAll("\s", ""), Base64.DEFAULT); // Remover newlines e espaços
            String decodedJsonString = new String(decodedBytes);
            return new JSONObject(decodedJsonString);

        } catch (Exception e) { // org.json.JSONException ou IOException
            Log.e(TAG, "Erro ao processar arquivo JSON do GitHub: " + filePath, e);
            throw new IOException("Erro ao processar arquivo JSON do GitHub: " + e.getMessage(), e);
        }
    }

    // Método para atualizar/criar arquivo JSON
    public boolean updateJsonFile(String filePath, JSONObject newJsonData, String commitMessage) throws IOException {
        if (owner == null || repo == null) {
            throw new IOException("Owner ou repositório do GitHub não configurado.");
        }
        if (token == null || token.isEmpty()) {
            throw new IOException("Token do GitHub é necessário para atualizar arquivos.");
        }

        String url = API_BASE_URL + owner + "/" + repo + "/contents/" + filePath;
        String currentFileSha = null;

        // 1. Tentar obter o SHA do arquivo existente (para atualização)
        try {
            Request getRequest = new Request.Builder().url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build();
            Log.d(TAG, "Buscando SHA para: " + filePath);
            try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                if (getResponse.isSuccessful() && getResponse.body() != null) {
                    String getResponseBody = getResponse.body().string();
                    JSONObject getJsonResponse = new JSONObject(getResponseBody);
                    currentFileSha = getJsonResponse.optString("sha");
                    Log.d(TAG, "SHA encontrado para " + filePath + ": " + currentFileSha);
                } else if (getResponse.code() != 404) { // Se não for 404, é um erro diferente
                    Log.w(TAG, "Erro ao buscar SHA para " + filePath + ": " + getResponse.code() + " - " + (getResponse.body() != null ? getResponse.body().string() : ""));
                } else {
                    Log.d(TAG, "Arquivo " + filePath + " não encontrado. Será criado um novo.");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível obter SHA para " + filePath + ", continuando para criar/sobrescrever. Erro: " + e.getMessage());
        }

        // 2. Preparar conteúdo e fazer PUT request
        String newContentBase64 = Base64.encodeToString(newJsonData.toString(2).getBytes(), Base64.NO_WRAP); // Usar toString(2) para pretty print

        JSONObject payload = new JSONObject();
        try {
            payload.put("message", commitMessage);
            payload.put("content", newContentBase64);
            if (currentFileSha != null && !currentFileSha.isEmpty()) {
                payload.put("sha", currentFileSha);
            }
        } catch (org.json.JSONException e) {
             Log.e(TAG, "Erro ao criar payload JSON para update", e);
            throw new IOException("Erro ao criar payload JSON: " + e.getMessage(), e);
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request putRequest = new Request.Builder().url(url)
            .addHeader("Authorization", "token " + token)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .put(body)
            .build();

        Log.d(TAG, "Atualizando arquivo: " + filePath + " com SHA: " + currentFileSha);
        try (Response response = httpClient.newCall(putRequest).execute()) {
             ResponseBody responseBody = response.body();
            if (!response.isSuccessful()) {
                String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                Log.e(TAG, "Falha ao atualizar arquivo no GitHub: " + response.code() + " - " + filePath + " - " + errorBody);
                throw new IOException("Falha ao atualizar arquivo no GitHub: " + response.code() + " - " + errorBody);
            }
            Log.i(TAG, "Arquivo atualizado com sucesso no GitHub: " + filePath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro durante a atualização do arquivo no GitHub: " + filePath, e);
            throw new IOException("Erro durante a atualização do arquivo no GitHub: " + e.getMessage(), e);
        }
    }
}
