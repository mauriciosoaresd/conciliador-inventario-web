# Conciliador de Inventário

Aplicação web em Java e Spring Boot para comparar um inventário em CSV com itens extraídos de documentos fiscais e gerar um relatório Excel. A descrição do produto é usada para encontrar correspondências; códigos são mantidos como informação, mas não determinam a identidade do produto. Resultados baseados em OCR podem exigir conferência manual, pois a leitura depende da qualidade e do layout do documento.

## Funcionalidades

- Importação de inventário em CSV, com soma das quantidades de linhas que compartilham a mesma descrição normalizada.
- Leitura de notas fiscais em XML, incluindo tags comuns de NF-e (`cProd`, `xProd`, `qCom`) e estruturas simplificadas.
- Extração de texto de PDFs e OCR de PDFs digitalizados e imagens PNG, JPG ou JPEG.
- Correspondência por similaridade textual da descrição, com validações para números e dimensões; códigos de inventário e de fornecedor não são usados como critério de correspondência.
- Agrupamento de linhas fiscais com a mesma descrição normalizada dentro de cada documento, sem combinar documentos diferentes.
- Relatório com alocação das quantidades físicas entre as notas correspondentes, mantendo uma linha por alocação e identificando separadamente o saldo físico sem NF ou o excedente fiscal sem item físico.
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

São aceitos arquivos com extensão `.xml`, `.pdf`, `.png`, `.jpg` ou `.jpeg` para os documentos fiscais. O XML é analisado com suporte a namespaces e declarações `DOCTYPE` são rejeitadas. Arquivos vazios, extensões não suportadas ou CSV que não possa ser interpretado são informados como erro na interface.

### Formato do CSV

O arquivo deve ser UTF-8, separado por vírgulas, com os cabeçalhos abaixo. `Quantidade` é opcional; se ausente ou vazia, cada linha representa uma unidade.

```csv
Codigo,Descricao,Marca,Modelo,Estado,Imagem,Quantidade
INV-001,Notebook Dell Latitude,Dell,Latitude,Bom,,2
INV-002,Cadeira de escritório,,,Regular,,1
```

Os nomes dos cabeçalhos devem corresponder aos apresentados no exemplo. A quantidade pode ser fracionária. Linhas com descrições normalizadas equivalentes são agrupadas e suas quantidades somadas, mesmo que tenham códigos diferentes. Descrições diferentes permanecem separadas.

Na conciliação, itens com a mesma descrição são totalizados separadamente em cada documento fiscal e as quantidades são alocadas sem contar a mesma unidade física mais de uma vez. Por exemplo, um inventário de 10 unidades e duas notas com 4 unidades cada gera duas linhas `OK` de 4 unidades e uma linha de 2 unidades físicas com `-` na quantidade fiscal e status `SEM NF CORRESPONDENTE`. Se uma nota indicar mais unidades do que restam no inventário, a parte alocada aparece como `OK` e o excedente fiscal é listado separadamente como não alocado ao físico. As quantidades fiscais identificadas são alocadas na ordem dos documentos enviados; a alocação é por quantidade e não identifica individualmente cada peça física. Quantidades fiscais desconhecidas ou descrições com medidas conflitantes exigem revisão manual e não são tratadas como saldo sem NF. A correspondência por descrição é aproximada e pode exigir revisão, especialmente para descrições curtas ou ambíguas.

## Banco de dados

Ao executar localmente, o perfil padrão (`local`) usa H2 persistente em `./data/conciliadordb`, relativo ao diretório de onde a aplicação foi iniciada. O usuário padrão é `sa`, sem senha, e o Hibernate atualiza o schema (`ddl-auto=update`). O console do H2 fica desabilitado por padrão; para habilitá-lo somente em desenvolvimento, execute com `--spring.h2.console.enabled=true`.

As configurações locais podem ser sobrescritas pelas variáveis `CONCILIADOR_DB_URL`, `CONCILIADOR_DB_USERNAME` e `CONCILIADOR_DB_PASSWORD`. Para iniciar com o perfil `prod`, defina também essas três variáveis: nesse perfil, o console fica desabilitado e o Hibernate apenas valida o schema (`ddl-auto=validate`), sem atualizá-lo automaticamente. Apesar do nome do perfil, ele ainda usa o driver H2; portanto, não constitui configuração pronta para produção com MySQL ou PostgreSQL. O projeto não inclui esses drivers nem migrações de schema.

O diretório `data/` contém somente o banco local e não é versionado. Faça cópias de segurança desse arquivo antes de removê-lo ou substituí-lo.

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
