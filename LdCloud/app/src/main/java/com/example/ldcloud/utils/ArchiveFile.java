package com.example.ldcloud.utils;

public class ArchiveFile {
    public String name;
    public boolean isDirectory;
    public String size; // Manter como String para consistência ("DIR" ou tamanho em bytes)
    public String lastModified; // Manter como String formatada

    // Novos campos
    public String iaS3Key;    // Chave do objeto no Internet Archive S3 (para arquivos OU pastas S3)
    public String jsonPath;   // Caminho para o arquivo JSON que descreve o conteúdo (para diretórios GitHub)

    // Construtor atualizado
    public ArchiveFile(String name, boolean isDirectory, String size, String lastModified, String iaS3Key, String jsonPath) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.iaS3Key = iaS3Key; // Para arquivos IA S3, este é o 'key'. Para pastas IA S3, também é o 'key' (prefixo).
        this.jsonPath = jsonPath; // Usado para diretórios que são representados por um JSON no GitHub
    }

    // Construtor simplificado para arquivos/pastas S3 onde jsonPath não é inicialmente relevante
    public ArchiveFile(String name, boolean isDirectory, String size, String lastModified, String iaS3Key) {
        this(name, isDirectory, size, lastModified, iaS3Key, null);
    }


    // Getters (opcional, mas bom para encapsulamento futuro)
    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getSize() {
        return size;
    }

    public String getLastModified() {
        return lastModified;
    }

    public String getIaS3Key() {
        return iaS3Key;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    // Setters (opcionais)
    public void setName(String name) {
        this.name = name;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public void setIaS3Key(String iaS3Key) {
        this.iaS3Key = iaS3Key;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }
}
