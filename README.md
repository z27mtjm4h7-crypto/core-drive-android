# CORE DRIVE — versão 0.4.2

## Correção desta versão
O fluxo de CI não depende mais de `./gradlew` nem do arquivo `gradlew`, que não existia no pacote enviado ao GitHub. Ele configura o Gradle 8.9 e executa `gradle --no-daemon assembleDebug`.

## Aplicar a correção no GitHub
1. Baixe e extraia este ZIP.
2. No repositório GitHub, substitua os arquivos do projeto pelos arquivos desta pasta, mantendo a estrutura na raiz do repositório.
3. Confirme que `.github/workflows/android.yml` foi enviado.
4. Remova workflows antigos duplicados em `.github/workflows/` que ainda executem `chmod +x gradlew` ou `./gradlew`.
5. Abra Actions → Android CI → Run workflow.
6. Se a compilação passar, baixe o artefato `core-drive-debug-apk`.

## Status
A correção endereça o erro visto no log (`gradlew` ausente). Ainda não foi possível executar a compilação no ambiente de criação; a próxima execução do GitHub verificará erros adicionais de dependências ou código.
