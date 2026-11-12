# Conciliador de Inventário

Aplicação web em Java e Spring Boot para comparar um inventário em CSV com itens extraídos de documentos fiscais e gerar um relatório Excel. A aplicação usa correspondência por código e similaridade de descrições; resultados baseados em OCR podem exigir conferência manual, pois a leitura depende da qualidade e do layout do documento.

## Funcionalidades

- Importação de inventário em CSV, com soma das quantidades de linhas que compartilham o mesmo código.
- Leitura de notas fiscais em XML, incluindo tags comuns de NF-e (`cProd`, `xProd`, `qCom`) e estruturas simplificadas.
- Extração de texto de PDFs e OCR de PDFs digitalizados e imagens PNG, JPG ou JPEG.
- Correspondência primeiro por código e, quando necessário, por similaridade textual, com validações para números e dimensões.
- Cadastro de sinônimos no H2 quando uma correspondência por descrição alcança o limite de confiança implementado.
- Geração de relatório `.xlsx` com quantidades, rastreabilidade do documento de origem e status da conciliação.

O OCR é sensível à qualidade e à disposição das colunas. Em imagens, a aplicação tenta reconhecer a coluna de quantidades; se não conseguir alinhar seus valores às linhas dos itens, usa a quantidade extraída do texto. Quando a quantidade não é identificada no OCR ou está ausente/inválida no XML, o relatório marca o item como `REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA` e não assume uma quantidade. A célula correspondente no Excel exibe `NÃO IDENTIFICADA`. Confira esses casos manualmente, especialmente quando o documento estiver inclinado, pouco legível ou tiver um layout diferente. A quantidade padrão 1 usada para linhas sem quantidade no CSV do inventário é independente desse tratamento fiscal.

## Tecnologias

- Java 21 e Spring Boot 4.1.2
- Spring MVC, Thymeleaf e Spring Data JPA
- H2 como único banco configurado neste projeto
- OpenCSV, Apache POI, Apache PDFBox, Tess4J/Tesseract e Apache Commons Text

MySQL e PostgreSQL não estão configurados: o projeto não inclui seus drivers nem perfis de conexão.

## Pré-requisitos

- JDK 21.
- Tess4J/Tesseract é usado para processar imagens e documentos digitalizados.
- O modelo de idioma português está incluído em `src/main/resources/tessdata/por.traineddata`.

O Maven Wrapper está incluído no repositório, portanto não é necessário instalar Maven separadamente.

## Executar a aplicação

Na raiz do repositório, execute:

```powershell
.\mvnw.cmd spring-boot:run
```

No Linux ou macOS:

```bash
./mvnw spring-boot:run
```

Acesse `http://localhost:8080`. Envie um CSV de inventário e um ou mais documentos XML, PDF ou imagens PNG/JPG/JPEG para baixar o relatório Excel.

### Formato do CSV

O arquivo deve ser UTF-8, separado por vírgulas, com os cabeçalhos abaixo. `Quantidade` é opcional; se ausente ou vazia, cada linha representa uma unidade.

```csv
Codigo,Descricao,Marca,Modelo,Estado,Imagem,Quantidade
INV-001,Notebook Dell Latitude,Dell,Latitude,Bom,,2
INV-002,Cadeira de escritório,,,Regular,,1
```

Os nomes dos cabeçalhos devem corresponder aos apresentados no exemplo. A quantidade pode ser fracionária. Linhas com o mesmo código são agrupadas e suas quantidades somadas.

## Banco de dados

Ao executar a aplicação, o perfil padrão usa H2 persistente em arquivo no caminho `./data/conciliadordb`, relativo ao diretório de onde a aplicação foi iniciada. O usuário padrão é `sa`, sem senha. O Hibernate atualiza o schema existente (`ddl-auto=update`).

O console do H2 está habilitado em `http://localhost:8080/h2-console`. Para conectar, use a URL JDBC `jdbc:h2:file:./data/conciliadordb`, usuário `sa` e senha vazia.

## Testes

Os testes usam um banco H2 em memória, configurado em `src/test/resources/application.properties`, com o schema criado e removido para a execução. Esse banco é separado do arquivo persistente usado pela aplicação. A suíte também contém testes de OCR com imagens em `src/test/resources/ocr/` e, por isso, requer Tesseract instalado.

Execute todos os testes:

```powershell
.\mvnw.cmd test
```

No Linux ou macOS:

```bash
./mvnw test
```
