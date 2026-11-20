# Conciliador de Inventário

Aplicação web em Java e Spring Boot para comparar um inventário em CSV com itens extraídos de documentos fiscais e gerar um relatório Excel. A descrição, a marca e o modelo do produto são usados para formar sua identidade e encontrar correspondências; códigos são mantidos como informação, mas não determinam a identidade do produto. Resultados baseados em OCR podem exigir conferência manual, pois a leitura depende da qualidade e do layout do documento.

## Funcionalidades

- Importação de inventário em CSV no formato padrão de cinco colunas, com soma das quantidades de linhas que compartilham descrição, marca e modelo normalizados.
- Leitura de notas fiscais em XML, incluindo tags comuns de NF-e (`cProd`, `xProd`, `qCom`) e estruturas simplificadas.
- Extração de texto de PDFs e OCR de PDFs digitalizados e imagens PNG, JPG ou JPEG.
- Correspondência por similaridade textual da descrição, com modelos informados no CSV usados como evidência de identidade e comparação genérica de marca. Quando os dados não confirmam a correspondência, os itens permanecem sem vínculo. Códigos de inventário e de fornecedor não são usados como critério.
- Agrupamento de linhas fiscais com a mesma descrição normalizada dentro de cada documento, sem combinar documentos diferentes.
- Relatório com alocação das quantidades físicas entre as notas correspondentes, mantendo uma linha por alocação e identificando separadamente o saldo físico sem NF ou o excedente fiscal sem item físico.
- Revisão opcional na interface para itens sem conciliação automática, com até quatro sugestões por proximidade; a seleção explícita gera no Excel o status `CONCILIADO MANUALMENTE`.
- Geração de relatório `.xlsx` com quantidades, rastreabilidade do documento de origem e status da conciliação.

O OCR é sensível à qualidade e à disposição das colunas. Em imagens, a aplicação tenta reconhecer a coluna de quantidades; se não conseguir alinhar seus valores às linhas dos itens, usa a quantidade extraída do texto. Quando a quantidade não é identificada no OCR ou está ausente/inválida no XML, o item fiscal não é associado ao inventário: ele recebe o status `ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)` e a célula de quantidade exibe `NÃO IDENTIFICADA`. Confira esses casos manualmente, especialmente quando o documento estiver inclinado, pouco legível ou tiver um layout diferente. A quantidade padrão 1 usada para linhas sem quantidade no CSV do inventário é independente desse tratamento fiscal.

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

O formato padrão é um arquivo UTF-8 separado por vírgulas com cinco colunas: `codigo`, `descricao`, `marca`, `modelo` e `estado`. Os nomes dos cabeçalhos podem estar em maiúsculas ou minúsculas. Cada linha representa uma unidade. `Imagem` e `Quantidade` são colunas opcionais; se `Quantidade` estiver ausente ou vazia, cada linha representa uma unidade.

```csv
codigo,descricao,marca,modelo,estado
INV-001,Notebook,Dell,Latitude 5440,Bom
INV-002,Cadeira de escritório,Flexform,Uni Me,Regular
```

Quando usada, a coluna `Quantidade` pode ser fracionária; `Imagem` e `Quantidade` podem ser incluídas ao final do cabeçalho. Linhas com descrição, marca e modelo normalizados equivalentes são agrupadas e suas quantidades somadas, mesmo que tenham códigos ou estados diferentes. Os estados distintos do grupo são preservados no relatório. Descrição, marca ou modelo diferentes mantêm produtos separados. Se marca e modelo estiverem vazios, a descrição é usada sozinha.

Na conciliação, todas as possibilidades são pontuadas antes da alocação. Dentro de cada documento, uma linha fiscal e um grupo de inventário só podem ser usados uma vez; candidatos empatados não são associados automaticamente. Modelos identificáveis diferentes impedem a correspondência. Quando o modelo confirma uma possível correspondência, a marca informada no inventário também precisa ser encontrada na descrição fiscal; marca ausente ou diferente não é considerada confirmação. Variações pequenas de digitação são comparadas de forma genérica, sem uma lista fixa de fabricantes. Dimensões são comparadas sem depender da ordem (por exemplo, `120x60` e `60x120`), com unidade convertida para centímetros quando informada; medidas métricas isoladas também são convertidas (por exemplo, `57cm` e `570mm`). Diferenças acima de `conciliador.conciliacao.tolerancia-medida-cm` impedem a associação. O padrão é `2` cm e pode ser alterado por propriedade ou variável de ambiente. Quando há dimensões iguais, uma correspondência sem modelo pode ser confirmada se a similaridade atingir `conciliador.conciliacao.limite-similaridade-confirmada` (padrão `0.35`); o limite também pode ser configurado. Cor e material são tratados como informações auxiliares: diferenças não bloqueiam a conciliação quando ambos os itens informam algum desses atributos, pois cores podem mudar e materiais podem estar descritos incorretamente. Divergências entre ferro e aço/aço carbono podem ser sugeridas com status `REVISÃO MANUAL — MATERIAL`, sem confirmação automática; a sugestão exige descrição suficientemente similar, quantidade fiscal válida e dimensões compatíveis, e não substitui a validação humana. Se atributos aparecem apenas em uma das descrições, a ausência de evidência exige revisão e não é conciliada automaticamente. A correspondência ainda depende das dimensões, da similaridade da descrição e dos identificadores de modelo.

Quando não há evidência suficiente para conciliar, o inventário permanece como `SEM NF CORRESPONDENTE` e o item fiscal sem correspondência como `ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)`. Na interface web, esses casos são apresentados antes da geração do Excel, com cada linha fiscal como referência e até quatro sugestões físicas ordenadas por similaridade textual, desde que não haja incompatibilidade conhecida de modelo ou dimensões e exista ao menos um termo característico em comum além de cor, material e medidas. O limiar mínimo das sugestões é configurável por `conciliador.conciliacao.limite-similaridade-sugestao` (padrão `0.10`). A similaridade é apenas uma ordenação, não uma probabilidade de acerto: nenhuma sugestão é pré-selecionada, e o usuário pode manter cada linha fiscal sem vínculo. O mesmo grupo físico pode ser selecionado para mais de uma linha fiscal enquanto houver saldo físico; se o saldo for menor que a quantidade fiscal, a parte disponível é alocada e a falta aparece no relatório. A interface atualiza as opções conforme o saldo é consumido e o servidor valida novamente antes de gerar o relatório. A data de emissão, quando extraída do XML ou do texto identificado por OCR, é exibida como contexto e não altera a similaridade nem a conciliação automática. Confirmações manuais aparecem como `CONCILIADO MANUALMENTE` ou `CONCILIADO MANUALMENTE — FALTANDO NO FÍSICO` quando a quantidade alocada não atende toda a quantidade fiscal. Os arquivos processados ficam temporariamente na sessão do navegador até a geração do relatório ou expiração da sessão. Linhas de inventário com descrições normalizadas equivalentes têm suas quantidades somadas antes da alocação. Por exemplo, um inventário de 10 unidades e duas notas com 4 unidades cada gera duas linhas `OK` de 4 unidades e uma linha de 2 unidades físicas com `-` na quantidade fiscal e status `SEM NF CORRESPONDENTE`. Para cada item conciliado de uma NF, o relatório apresenta uma única linha com a quantidade física alocada, a quantidade fiscal total e a diferença `Físico - NF`. Assim, se a NF indicar 5 unidades e houver 3 no inventário, a linha exibe 3 na quantidade do inventário, 5 na quantidade fiscal, diferença `-2` e status `FALTANDO NO FÍSICO`. Diferenças negativas indicam falta física; positivas, excedente físico. A diferença aparece como `-` quando não há quantidade disponível para um dos lados da comparação. Os códigos dos itens físicos são distribuídos entre as linhas conciliadas e os saldos sem NF na ordem em que os itens aparecem no inventário, sem repetir o grupo completo em cada linha. Se um registro do inventário representar várias unidades, seu código pode aparecer em mais de uma linha quando essa quantidade for dividida entre alocações. Quantidades fiscais identificadas são alocadas na ordem dos documentos enviados. Quantidades fiscais desconhecidas ou iguais a zero não são associadas ao inventário, e aparecem como item apenas na NF; o saldo físico, se houver, permanece sem NF correspondente. A correspondência textual é aproximada e pode deixar itens sem associação, especialmente quando o inventário não informa modelo ou quando as descrições são curtas ou ambíguas.

Diferenças de medidas dentro da tolerância podem ser conciliadas automaticamente quando a similaridade da descrição e os demais atributos confirmam o item. Quando uma descrição informa duas dimensões e a outra três, as duas maiores dimensões são comparadas; essa equivalência ainda depende da compatibilidade do produto e dos atributos.

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
