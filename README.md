# Lista Inteligente

Aplicação Android nativa para organizar materiais de construção e acompanhar compras e custos.

## Funcionalidades
- Criar listas identificadas por obra ou projeto.
- Adicionar, editar e remover materiais.
- Registar quantidade, unidade, categoria e preço unitário.
- Calcular automaticamente o total por artigo e o orçamento global.
- Marcar artigos como comprados e filtrar por estado.
- Pesquisar materiais.
- Introdução por voz em português de Portugal (requer serviço de reconhecimento de voz disponível no Android e autorização do microfone).
- Partilhar a lista através das aplicações instaladas no telemóvel.
- Guardar os dados localmente no dispositivo, sem necessidade de conta.

## Gerar o APK
O GitHub Actions compila automaticamente a aplicação em cada atualização da branch `main`.

1. Abrir o separador **Actions** do repositório.
2. Selecionar **Android APK**.
3. Abrir a execução mais recente concluída com sucesso.
4. Na secção **Artifacts**, descarregar `Lista-Inteligente-APK`.
5. Extrair o ZIP e instalar `app-debug.apk` no Android.

Também é possível iniciar a compilação manualmente através de **Actions → Android APK → Run workflow**.

## Ambiente técnico
- Kotlin
- Jetpack Compose / Material 3
- Android minSdk 26
- JDK 17
- Gradle 8.9 / Android Gradle Plugin 8.7.3

## Nota
Esta é a primeira versão funcional em desenvolvimento. O APK só deve ser considerado pronto para distribuição depois de uma execução de compilação bem-sucedida e testes num dispositivo Android.
