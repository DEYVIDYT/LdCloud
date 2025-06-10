# LdCloud

LdCloud é um cliente Android para o Internet Archive que permite aos usuários gerenciar seus arquivos armazenados em "itens" do Internet Archive, tratando-os como buckets S3. O aplicativo utiliza a API S3-compatível do Internet Archive para operações de arquivo.

## Funcionalidades Implementadas

*   Configuração de credenciais do Internet Archive (Access Key, Secret Key) e Item Title (nome do bucket S3).
*   Listagem de arquivos e pastas dentro de um Item.
*   Upload de arquivos para um Item.
*   Download de arquivos de um Item.
*   Criação de novas pastas dentro de um Item.
*   Interface de usuário baseada no Material You (Material 3).

## Como Compilar e Executar

1.  Clone este repositório.
2.  Abra o projeto no Android Studio (versão mais recente recomendada).
3.  O projeto deve sincronizar as dependências Gradle automaticamente.
4.  Compile e execute em um emulador ou dispositivo Android.

## Configuração do Aplicativo

Para usar o LdCloud, você precisará de credenciais do Internet Archive:

1.  **Access Key e Secret Key**:
    *   Faça login no [archive.org](https://archive.org/).
    *   Visite `https://archive.org/account/s3.php` para obter suas chaves S3.
2.  **Item Title**:
    *   Este é o identificador único do "item" no Internet Archive que você deseja usar como seu armazenamento em nuvem (bucket S3). Você pode criar um novo item no archive.org se necessário, ou usar um existente.
    *   O "Item Title" deve ser inserido exatamente como aparece no URL do item (ex: se o URL é `https://archive.org/details/meuitemdeteste`, o Item Title é `meuitemdeteste`).
3.  **Endpoint S3**:
    *   O aplicativo está configurado para usar o endpoint S3 do Internet Archive: `s3.us.archive.org`.

Insira essas informações na tela de "Configurações" dentro do aplicativo.

## Limitações Conhecidas e Desenvolvimento Futuro

*   A interface do usuário para listagem de arquivos não implementa paginação ou rolagem infinita; o desempenho pode degradar com um número extremamente grande de arquivos.
*   A lista de arquivos na tela "Arquivos" não é atualizada automaticamente em tempo real após um upload na tela "Uploads"; requer reentrada na tela ou uma atualização manual (não implementada).
*   O estado do aplicativo (ex: uploads/downloads em progresso) não é totalmente preservado durante a rotação da tela.
*   O feedback de erro pode ser mais específico (ex: diferenciar erros de rede de credenciais inválidas).

## Contribuindo

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou pull requests.
