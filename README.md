# 📦 Motor Híbrido de Conciliação de Inventário

Sistema corporativo de conciliação e auditoria automatizada de inventários físicos versus documentos fiscais (XML, PDF e Imagens/OCR). Desenvolvido em **Spring Boot 3** com arquitetura desacoplada, motor de correspondência híbrido (*Hybrid Matching Engine*) e aprendizado contínuo de sinônimos via JPA.

---

## 🚀 Funcionalidades Principais

* **Motor Híbrido de Correspondência (Hybrid Matching Engine)**:

    * **Stemming (Redução de Sufixos)**: Agrupamento inteligente de variações morfológicas (ex: *cadeira* / *cadeiras*).
    * **Distância de Levenshtein (Fuzzy Matching)**: Tolerância a erros de digitação e variações ortográficas simples.
    * **Índice de Jaccard**: Cálculo de similaridade baseado em conjuntos de termos sobrepostos.
    * **Expressões Regulares (Regex)**: Detecção e validação de dimensões de produtos (ex: `120x60cm`) para prevenção de falsos positivos por ambiguidade de medida.
* **Auto-aprendizagem de Sinônimos (JPA)**:

    * Cadastro automático e dinâmico de termos equivalentes na base de dados quando a confiança da correspondência for superior a 85%.
* **Processamento Polivalente de Documentos**:

    * **XML (NFe)**: Parser flexível para tags oficiais brasileiras (`cProd`, `xProd`, `qCom`) e estruturas simplificadas (`det`, `item`).
    * **PDF & Imagens (OCR)**: Leitura de documentos não estruturados via **Apache PDFBox** e **Tess4J (Tesseract OCR)**.
* **Interface Assíncrona & Responsiva**:

    * Frontend moderno desenvolvido com design minimalista corporativo (Typography Inter, micro-interações).
    * Submissão via **Fetch API** assíncrona, garantindo que a tela não permaneça bloqueada após o download automático do relatório Excel.
* **Exportação em Excel**:

    * Geração automática de relatórios `.xlsx` categorizados por status (`OK`, `DIVERGENTE - SOBRA`, `DIVERGENTE - FALTA`, `NÃO ENCONTRADO NO DOCUMENTO`, `REVISÃO MANUAL`).

---

## 🛠️ Tecnologias Utilizadas

* **Linguagem**: Java 17
* **Framework Backend**: Spring Boot 3.x
    * Spring Data JPA
    * Spring Web (MVC)
    * Thymeleaf
* **Banco de Dados**:
    * H2 Database (In-Memory / Desenvolvimento & Testes)
    * MySQL / PostgreSQL (Ambiente de Produção)
* **Bibliotecas de Processamento**:
    * Apache POI (Geração de planilhas Excel)
    * Apache PDFBox (Extração de texto de PDFs)
    * Tess4J / Tesseract OCR (Reconhecimento óptico de caracteres em imagens)
    * Lucene Analyzers (Stemming no idioma Português)
* **Frontend**:
    * HTML5 / CSS3 Moderno (Custom Properties, Flexbox)
    * JavaScript (Fetch API / Async)

---

## 📋 Pré-requisitos

Antes de iniciar, certifique-se de ter instalado em sua máquina:

* **JDK 17** ou superior.
* **Maven 3.8+**.
* **Tesseract OCR** (necessário para o processamento de imagens):
    * **Linux (Ubuntu/Debian)**: `sudo apt-get install tesseract-ocr tesseract-ocr-por`
    * **macOS**: `brew install tesseract tesseract-lang`
    * **Windows**: Instalar o pacote Tesseract-OCR e registrar a variável de ambiente `TESSDATA_PREFIX`.

---

## ⚙️ Configuração e Execução

### 1\. Clonar o Repositório

```sh
git clone https://github.com/mauriciosoaresd/conciliador-inventario-web.git
cd conciliador-inventario-web
```

### 2\. Executar os Testes Unitários

Certifique-se de que a suíte de testes do motor híbrido passa com sucesso:

```sh
mvn clean test
```

### 3\. Executar a Aplicação

```sh
mvn spring-boot:run
```

Acesse a aplicação no navegador através do endereço: 👉 **`http://localhost:8080`**

---

## 📊 Estrutura dos Arquivos de Entrada

### Inventário (.CSV)

O arquivo de inventário deve utilizar o separador **vírgula (`,`)**:

```csv
codigo,descricao,estado
PROD-001,Cadeira de Escritorio Presidente 120x60cm,Novo
PROD-002,Notebook Dell Inspiron i5 16GB,Bom
PROD-003,Mesa de Reuniao Oval 200x100cm,Usado
```

&nbsp;

### Documentos Fiscais (.XML / .PDF / .PNG / .JPG)

* **XML**: Suporta XMLs oficiais de NFe (`<det><prod>...`) ou genéricos de teste com a tag `<item>`.
* **PDF / Imagens**: Documentos fiscais digitalizados contendo os códigos, descrições e quantidades negociadas.

---

## 📄 Licença

Este projeto está sob a licença **MIT**. Veja o arquivo [LICENSE](http://LICENSE) para mais detalhes.