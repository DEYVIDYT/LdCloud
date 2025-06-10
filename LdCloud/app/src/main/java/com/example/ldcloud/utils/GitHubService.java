package com.example.ldcloud.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64; // Para decodificar conteúdo do GitHub
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException; // Adicionado para tratamento de erro
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
        Log.d(TAG, "getJsonFileContent: Tentando buscar JSON em: " + filePath);
        if (owner == null || repo == null) {
            throw new IOException("Owner ou repositório do GitHub não configurado.");
        }
        if (token == null || token.isEmpty()) {
             Log.w(TAG, "Token do GitHub não fornecido. Tentando acesso público.");
        }

        String url = API_BASE_URL + owner + "/" + repo + "/contents/" + filePath;
        Log.d(TAG, "getJsonFileContent: URL da API GitHub: " + url);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (token != null && !token.isEmpty()) {
            requestBuilder.addHeader("Authorization", "token " + token);
        }
        requestBuilder.addHeader("Accept", "application/vnd.github.v3+json");

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            Log.d(TAG, "getJsonFileContent: Resposta para " + filePath + " - Código: " + response.code());
            ResponseBody responseBody = response.body();

            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    Log.i(TAG, "getJsonFileContent: Arquivo JSON não encontrado (404): " + filePath + ". Retornando null.");
                    return null;
                }
                String errorBodyString = responseBody != null ? responseBody.string() : "Corpo do erro desconhecido";
                Log.e(TAG, "Falha ao buscar arquivo do GitHub: " + response.code() + " - " + filePath + " - " + errorBodyString);
                throw new IOException("Falha ao buscar arquivo do GitHub: " + response.code() + " - " + errorBodyString);
            }

            if (responseBody == null) {
                Log.e(TAG, "Corpo da resposta nulo ao buscar arquivo do GitHub: " + filePath);
                throw new IOException("Corpo da resposta nulo do GitHub para " + filePath);
            }

            String responseString = responseBody.string();
            JSONObject jsonResponse = new JSONObject(responseString);
            String contentBase64 = jsonResponse.optString("content");

            if (contentBase64.isEmpty()) {
                if (jsonResponse.has("type") && "dir".equals(jsonResponse.optString("type"))) {
                    Log.w(TAG, "O caminho '" + filePath + "' é um diretório, não um arquivo JSON. API retornou info de diretório.");
                    throw new IOException("O caminho especificado '" + filePath + "' é um diretório, não um arquivo.");
                }
                Log.w(TAG, "Conteúdo Base64 está vazio para o arquivo (pode ser um arquivo JSON vazio ou 0 bytes): " + filePath + ". Resposta da API: " + jsonResponse.toString(2));
            }

            byte[] decodedBytes = Base64.decode(contentBase64.replaceAll("\s", ""), Base64.DEFAULT);
            String decodedJsonString = new String(decodedBytes);

            if (decodedJsonString.trim().isEmpty()) {
                Log.i(TAG, "getJsonFileContent: Conteúdo JSON vazio/inválido para " + filePath + ". Retornando JSONObject vazio.");
                return new JSONObject();
            }

            JSONObject resultJson = new JSONObject(decodedJsonString);
            Log.d(TAG, "getJsonFileContent: JSON parseado com sucesso para: " + filePath + ". Tamanho aprox.: " + decodedJsonString.length());
            return resultJson;

        } catch (JSONException e) {
            Log.e(TAG, "Erro de parsing JSON para o arquivo: " + filePath, e);
            throw new IOException("Erro de parsing no JSON do GitHub para '" + filePath + "': " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(TAG, "IOException ao processar arquivo JSON do GitHub: " + filePath, e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Exceção inesperada ao processar arquivo JSON do GitHub: " + filePath, e);
            throw new IOException("Exceção inesperada ao processar arquivo JSON do GitHub para '" + filePath + "': " + e.getMessage(), e);
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
        String currentFileSha = null; // Mover para antes do log que o usa

        // 1. Tentar obter o SHA do arquivo existente (para atualização)
        try {
            Request getRequest = new Request.Builder().url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build();
            // Log movido para depois da inicialização de currentFileSha
            // Log.d(TAG, "Buscando SHA para: " + filePath);
            try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                if (getResponse.isSuccessful() && getResponse.body() != null) {
                    String getResponseBody = getResponse.body().string();
                    JSONObject getJsonResponse = new JSONObject(getResponseBody);
                    currentFileSha = getJsonResponse.optString("sha");
                    Log.d(TAG, "SHA encontrado para " + filePath + ": " + currentFileSha);
                } else if (getResponse.code() != 404) {
                    Log.w(TAG, "Erro ao buscar SHA para " + filePath + ": " + getResponse.code() + " - " + (getResponse.body() != null ? getResponse.body().string() : ""));
                } else {
                    Log.d(TAG, "Arquivo " + filePath + " não encontrado (será criado). SHA será null.");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível obter SHA para " + filePath + ", continuando para criar/sobrescrever. Erro: " + e.getMessage());
        }

        Log.d(TAG, "updateJsonFile: Tentando atualizar/criar JSON em: " + filePath + " com mensagem: '" + commitMessage + "'. SHA atual: " + currentFileSha);

        // 2. Preparar conteúdo e fazer PUT request
        String newJsonString;
        try {
            newJsonString = newJsonData.toString(2);
        } catch (JSONException e) {
            Log.e(TAG, "Erro ao converter JSONObject para String no updateJsonFile para: " + filePath, e);
            throw new IOException("Erro ao formatar JSON para atualização: " + e.getMessage(), e);
        }
        String newContentBase64 = Base64.encodeToString(newJsonString.getBytes(), Base64.NO_WRAP);
        Log.d(TAG, "updateJsonFile: Payload (primeiros 100 chars da Base64): " + (newContentBase64.length() > 100 ? newContentBase64.substring(0, 100) : newContentBase64) + "...");

        JSONObject payload = new JSONObject();
        try {
            payload.put("message", commitMessage);
            payload.put("content", newContentBase64);
            if (currentFileSha != null && !currentFileSha.isEmpty()) {
                payload.put("sha", currentFileSha);
            }
        } catch (JSONException e) {
             Log.e(TAG, "Erro ao criar payload JSON para update", e);
            throw new IOException("Erro ao criar payload JSON: " + e.getMessage(), e);
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request putRequest = new Request.Builder().url(url)
            .addHeader("Authorization", "token " + token)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .put(body)
            .build();

        // Log.d(TAG, "Atualizando arquivo: " + filePath + " com SHA: " + currentFileSha); // Redundante com o log acima
        try (Response response = httpClient.newCall(putRequest).execute()) {
             Log.d(TAG, "updateJsonFile: Resposta da atualização para " + filePath + " - Código: " + response.code());
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

    public boolean deleteJsonFile(String filePath, String commitMessage) throws IOException {
        if (owner == null || repo == null) {
            throw new IOException("Owner ou repositório do GitHub não configurado.");
        }
        if (token == null || token.isEmpty()) {
            throw new IOException("Token do GitHub é necessário para deletar arquivos.");
        }

        String url = API_BASE_URL + owner + "/" + repo + "/contents/" + filePath;
        String currentFileSha = null;

        // 1. Obter o SHA do arquivo existente
        Log.d(TAG, "deleteJsonFile: Tentando obter SHA para deletar arquivo: " + filePath);
        Request getRequest = new Request.Builder().url(url)
            .addHeader("Authorization", "token " + token)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .get()
            .build();

        try (Response getResponse = httpClient.newCall(getRequest).execute()) {
            if (getResponse.isSuccessful() && getResponse.body() != null) {
                String getResponseBody = getResponse.body().string();
                JSONObject getJsonResponse = new JSONObject(getResponseBody);
                currentFileSha = getJsonResponse.optString("sha");
                if (currentFileSha == null || currentFileSha.isEmpty()) {
                    Log.e(TAG, "deleteJsonFile: SHA não encontrado na resposta para " + filePath + ". O arquivo existe?");
                    throw new IOException("SHA do arquivo não encontrado para deleção: " + filePath);
                }
                Log.d(TAG, "deleteJsonFile: SHA obtido para " + filePath + ": " + currentFileSha);
            } else if (getResponse.code() == 404) {
                Log.w(TAG, "deleteJsonFile: Arquivo não encontrado (404) para deletar: " + filePath + ". Nada a fazer.");
                return false;
            } else {
                String errorBody = getResponse.body() != null ? getResponse.body().string() : "Erro desconhecido";
                Log.e(TAG, "deleteJsonFile: Falha ao obter SHA para " + filePath + ". Código: " + getResponse.code() + " - " + errorBody);
                throw new IOException("Falha ao obter SHA para deleção: " + getResponse.code() + " - " + errorBody);
            }
        } catch (JSONException e) {
            Log.e(TAG, "deleteJsonFile: Erro ao parsear JSON para obter SHA de " + filePath, e);
            throw new IOException("Erro ao parsear resposta para obter SHA: " + e.getMessage(), e);
        }
        // Se currentFileSha ainda for nulo aqui, algo deu errado (embora o throw acima devesse pegar).
        if (currentFileSha == null) {
             Log.e(TAG, "deleteJsonFile: currentFileSha é nulo antes de tentar deletar " + filePath);
             throw new IOException("Não foi possível obter o SHA do arquivo para deleção.");
        }

        // 2. Preparar payload e fazer DELETE request
        JSONObject payload = new JSONObject();
        try {
            payload.put("message", commitMessage);
            payload.put("sha", currentFileSha);
        } catch (JSONException e) {
            Log.e(TAG, "deleteJsonFile: Erro ao criar payload JSON para deleção de " + filePath, e);
            throw new IOException("Erro ao criar payload JSON para deleção: " + e.getMessage(), e);
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request deleteRequest = new Request.Builder().url(url)
            .addHeader("Authorization", "token " + token)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .delete(body)
            .build();

        Log.d(TAG, "deleteJsonFile: Tentando deletar arquivo: " + filePath + " com SHA: " + currentFileSha);
        try (Response response = httpClient.newCall(deleteRequest).execute()) {
            if (response.isSuccessful()) {
                Log.i(TAG, "deleteJsonFile: Arquivo deletado com sucesso do GitHub: " + filePath + ". Código: " + response.code());
                return true;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "Erro desconhecido";
                Log.e(TAG, "deleteJsonFile: Falha ao deletar arquivo no GitHub: " + filePath + ". Código: " + response.code() + " - " + errorBody);
                throw new IOException("Falha ao deletar arquivo no GitHub: " + response.code() + " - " + errorBody);
            }
        } catch (IOException e) {
             Log.e(TAG, "deleteJsonFile: Exceção durante a deleção do arquivo no GitHub: " + filePath, e);
            throw e;
        }
    }
}
