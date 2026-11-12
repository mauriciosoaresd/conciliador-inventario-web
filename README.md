# 📦 Motor Híbrido de Conciliação de Inventário

Sistema corporativo de conciliação e auditoria automatizada de inventários físicos versus documentos fiscais (XML, PDF e Imagens/OCR). Desenvolvido em **Java 21 / Spring Boot** com arquitetura desacoplada, motor de correspondência híbrido (*Hybrid Matching Engine*) e aprendizado contínuo de sinônimos via JPA.

---

## 🚀 Funcionalidades Principais

* **Motor Híbrido de Correspondência (Hybrid Matching Engine)**:
  * **Stemming (Redução de Sufixos)**: Agrupamento inteligente de variações morfológicas (ex: *cadeira* / *cadeiras*).
  * **Distância de Levenshtein (Fuzzy Matching)**: Tolerância a erros de digitação e variações ortográficas simples.
  * **Índice de Jaccard**: Cálculo de similaridade baseado em conjuntos de termos sobrepostos.
  * **Blindagem de Especificações Técnicas**: Validação numérica automática (capacidades, BTUs, potências) para evitar falsos positivos entre modelos diferentes do mesmo fabricante.
  * **Expressões Regulares (Regex)**: Detecção e validação de dimensões de produtos (ex: `120x60cm`) para prevenção de falsos positivos por ambiguidade de medida.

* **Auto-aprendizagem de Sinônimos (JPA)**:
  * Cadastro automático e dinâmico de termos equivalentes na base de dados quando a confiança da correspondência for superior a 85%.

* **Processamento Polivalente de Documentos**:
  * **XML (NFe)**: Parser flexível para tags oficiais brasileiras (`cProd`, `xProd`, `qCom`) e estruturas simplificadas.
  * **PDF & Imagens (OCR)**: Leitura de documentos não estruturados via **Apache PDFBox** e **Tess4J (Tesseract OCR)**, com blindagem contra ruídos de cabeçalho e rodapé.

* **Auditoria de Itens Órfãos e Exclusivos**:
  * Identificação de itens físicos não encontrados nas notas (`NÃO ENCONTRADO NO DOCUMENTO`).
  * Identificação de itens exclusivos de notas fiscais sem cadastro físico prévio (`ITEM APENAS NA NF`).
  * Formatação limpa com preenchimento por traços (`-`) em colunas sem correspondência para evitar poluição visual.

* **Exportação em Excel (.xlsx)**:
  * Geração automática de relatórios categorizados por status (`OK`, `DIVERGENTE - SOBRA`, `DIVERGENTE - FALTA`, `NÃO ENCONTRADO NO DOCUMENTO`, `ITEM APENAS NA NF`, `REVISÃO MANUAL`).

---

## 🛠️ Tecnologias Utilizadas

* **Linguagem**: Java 21
* **Framework Backend**: Spring Boot
  * Spring Data JPA
  * Spring Web (MVC)
  * Thymeleaf
* **Banco de Dados**:
  * H2 Database (In-Memory / Desenvolvimento & Testes)
  * MySQL / PostgreSQL (Ambiente de Produção)
* **Bibliotecas de Processamento**:
  * Apache POI (Geração de planilhas Excel)
  * Apache PDFBox (Extração de texto de PDFs)
  * Tess4J / Tesseract OCR (Reconhecimento óptico de caracteres)
* **Frontend**:
  * HTML5 / CSS3 / JavaScript (Fetch API / Async)

---

## 📋 Pré-requisitos

Antes de iniciar, certifique-se de ter instalado em sua máquina:

* **JDK 21** ou superior.
* **Maven 3.8+**.
* **Tesseract OCR** (necessário para o processamento de imagens):
  * **Linux (Ubuntu/Debian)**: `sudo apt-get install tesseract-ocr tesseract-ocr-por`
  * **macOS**: `brew install tesseract tesseract-lang`
  * **Windows**: Instalar o pacote Tesseract-OCR e registrar a variável de ambiente `TESSDATA_PREFIX`.

## 🧪 Testes

Execute a suíte com `.\mvnw.cmd test` no Windows ou `./mvnw test` no Linux/macOS. Os testes usam um banco H2 em memória configurado em `src/test/resources/application.properties`, separado do banco local persistente da aplicação.