# Jasper CLI Batch

CLI Java 21 gộp hai nhóm chức năng trong một fat JAR:

1. **Render batch** — xuất một hoặc nhiều PDF từ mẫu JasperReports + JSON inline.
2. **Ký số PAdES** — tạo khóa thử nghiệm, ký PDF (có/không khung hiển thị), xác minh chữ ký.

Stack chính: **JasperReports 6.21.5**, **European Commission DSS 6.4** (PDFBox), **picocli**, **Jackson**.

## Mục lục

- [Kiến trúc](#kiến-trúc)
- [Build](#build)
- [Cấu trúc CLI](#cấu-trúc-cli)
- [Biến môi trường & exit code](#biến-môi-trường--exit-code)
- [Lệnh `render`](#lệnh-render)
- [Lệnh `generate-key`](#lệnh-generate-key)
- [Lệnh `sign`](#lệnh-sign)
- [Lệnh `verify`](#lệnh-verify)
- [Luồng end-to-end](#luồng-end-to-end)
- [Docker](#docker)
- [Thêm mẫu JasperReports](#thêm-mẫu-jasperreports)
- [Lưu ý production](#lưu-ý-production)

## Kiến trúc

### Tổng quan

```text
┌─────────────────────────────────────────────────────────────────┐
│  java -jar jasper-cli.jar                                       │
│  Main.java — dispatch theo args[0]                              │
└───────────────┬─────────────────────────────┬───────────────────┘
                │ args bắt đầu bằng "-"         │ subcommand
                ▼                               ▼
         RenderCommand (legacy)          JasperCliCommand
                │                      render | sign | verify | generate-key
                ▼                               │
    TemplateRegistry + ValueResolver              ▼
                │                      JasperReports  │  DSS 6.4
                ▼                               │         │
         JasperBatchRenderer                    PDF      PAdES sign/verify
                │                                         │
                ▼                                         ▼
         PDF riêng + merge tùy chọn              PKCS#12 / truststore
```

### Luồng render

```text
batch JSON array (inline data)
         │
         ▼
TemplateRegistry  ←  *.yaml (mapping) + *.jrxml (thiết kế)
         │
         ▼
ValueResolver     ←  string / date / image / qr / assetPath / records array
         │
         ▼
JasperReports compile + fill + export PDF
         │
         ▼
PDF từng job (--output)  +  PDF gộp tùy chọn (--merge)
```

Mỗi job JSON gồm `id`, `template` (mã trong YAML), `outputName` (tên file không đuôi), và object `data` chứa dữ liệu inline.

### Luồng ký số

```text
PKCS#12 signing key
         │
         ▼
PdfSigner (DSS PAdESService + PDFBox)
         │
         ├── PAdES Baseline B / T / LT / LTA
         ├── Visible signature (mặc định) hoặc --invisible
         └── RFC 3161 TSA + truststore + OCSP/CRL (T/LT/LTA)
         │
         ▼
Signed PDF  ──►  PdfVerifier (SignedDocumentValidator + SimpleReport)
```

### Package chính

| Package | Vai trò |
| --- | --- |
| `vn.trace.reportcli.cli` | Subcommand picocli: `RenderCommand`, `SignCommand`, `VerifyCommand`, `GenerateKeyCommand`, `CliPasswords` |
| `vn.trace.reportcli.render` | `TemplateRegistry`, `JasperBatchRenderer`, `ValueResolver` |
| `vn.trace.reportcli.config` | `ReportDefinition`, `ValueSpec` — schema YAML mẫu |
| `vn.trace.reportcli.sign` | `PdfSigner`, `PdfVerifier`, `SelfSignedKeyGenerator`, `VisualSignatureSupport`, `DssSupport` |
| `vn.trace.reportcli.model` | `BatchJob` — record JSON batch |
| `vn.trace.reportcli.util` | `JsonSupport` — Jackson JSON/YAML |

### Khung chữ ký hiển thị (visible signature)

Mặc định lệnh `sign` vẽ khung chữ ký trên trang PDF:

- **Vị trí:** góc dưới-phải trang (margin `--sign-x` / `--sign-y`, mặc định 12pt).
- **Xoay trang:** `VisualSignatureRotation.AUTOMATIC` — PDF landscape từ Jasper (thường có `Rotate=90`) vẫn đặt đúng góc nhìn thấy.
- **Nền:** trong suốt; font **Times New Roman** cỡ **8pt** (có thể 8–12).
- **Nội dung:** dòng 1 = CN chứng thư hoặc `--sign-text`; dòng 2 = ngày giờ ký `dd/MM/yyyy HH:mm`.
- **Căn chữ:** phải trong khung chữ ký.

## Build

```bash
mvn clean test package
```

Kết quả:

```text
target/jasper-cli.jar
```

Project dùng **JasperReports 6.21.5** — tương thích mẫu thiết kế bằng Jaspersoft Studio 6.x. Nên giữ source `.jrxml` và compile bằng cùng phiên bản lúc build hoặc runtime.

## Cấu trúc CLI

```text
java -jar target/jasper-cli.jar [--help]

# Legacy (không đổi script cũ): args bắt đầu bằng "-" → render
java -jar target/jasper-cli.jar --input ... --templates ...

# Subcommand mới
java -jar target/jasper-cli.jar render   ...
java -jar target/jasper-cli.jar sign     ...
java -jar target/jasper-cli.jar verify   ...
java -jar target/jasper-cli.jar generate-key ...
```

| Subcommand | Mô tả |
| --- | --- |
| `render` | Xuất PDF từ JSON batch + mẫu JasperReports |
| `generate-key` | Tạo PKCS#12 self-signed cho dev/test |
| `sign` | Ký PDF theo PAdES Baseline (B/T/LT/LTA) |
| `verify` | Xác minh chữ ký và chuỗi tin cậy |

Trên bash/zsh, **mỗi dòng trừ dòng cuối phải kết thúc bằng `\`**. Trên PowerShell, thay `\` bằng backtick `` ` `` hoặc viết một dòng.

## Biến môi trường & exit code

| Biến | Dùng cho |
| --- | --- |
| `JASPER_KEYSTORE_PASSWORD` | Mật khẩu PKCS#12 ký (`sign`) hoặc tạo key (`generate-key`); ưu tiên hơn `--password` |
| `JASPER_TRUSTSTORE_PASSWORD` | Mật khẩu truststore (`sign` LT/LTA, `verify`) |

| Exit code | Ý nghĩa |
| --- | --- |
| `0` | Thành công |
| `2` | `render`: có job lỗi; `verify`: không hợp lệ hoặc không có chữ ký |
| khác | Lỗi picocli / exception |

Khuyến nghị đặt password qua biến môi trường thay vì `--password` trên command line (tránh lộ trong process list hoặc shell history).

---

## Lệnh `render`

Xuất một hoặc nhiều PDF từ file JSON array. Ví dụ dữ liệu: `examples/batch-inline.json`.

### Ví dụ (cú pháp legacy)

```bash
java -jar target/jasper-cli.jar \
  --input examples/batch-inline.json \
  --templates config/templates \
  --assets . \
  --output output \
  --merge output/all-reports.pdf
```

### Ví dụ (subcommand)

```bash
java -jar target/jasper-cli.jar render \
  --input examples/batch-inline.json \
  --templates config/templates \
  --assets . \
  --output output \
  --merge output/all-reports.pdf
```

### Tham số

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--input` / `-i` | Có | — | File JSON array chứa danh sách job render |
| `--templates` / `-t` | Có | — | Thư mục chứa `*.yaml` + `*.jrxml` |
| `--assets` / `-a` | Không | `.` | Gốc đường dẫn tương đối cho ảnh/asset local |
| `--output` / `-o` | Không | `output` | Thư mục ghi PDF từng job |
| `--merge` | Không | — | Đường dẫn PDF gộp tất cả job thành công |
| `--fail-fast` | Không | tắt | Dừng ngay khi một job lỗi |

### Cấu trúc job JSON

```json
{
  "id": "dakao-001",
  "template": "DakaoCerN1",
  "outputName": "cert-nguyen-van-an",
  "data": {
    "cerNo": "AT-2026-0001",
    "fullName": "NGUYỄN VĂN AN",
    "avatarPath": "examples/anhthe.jpg"
  }
}
```

- `template`: khớp field `code` trong file YAML (ví dụ `config/templates/DakaoCerN1.yaml`).
- `outputName`: tên file đầu ra (không có `.pdf`); mặc định lấy từ `defaultOutputName` trong YAML nếu bỏ trống.

---

## Lệnh `generate-key`

Tạo file PKCS#12 chứa private key RSA và chứng thư X.509 self-signed. **Chỉ dùng local/dev** — production cần key và chain do CA/TSP cấp.

### Ví dụ

```bash
mkdir -p keys
export JASPER_KEYSTORE_PASSWORD='change-me'

java -jar target/jasper-cli.jar generate-key \
  --output keys/local-signer.p12 \
  --alias signer \
  --dn "CN=Local PDF Signer,O=Example,C=VN" \
  --days 365 \
  --key-size 3072
```

Hoặc truyền password trực tiếp (một dòng):

```bash
java -jar target/jasper-cli.jar generate-key \
  --output keys/local-signer.p12 \
  --alias signer \
  --dn "CN=Local PDF Signer,O=Example,C=VN" \
  --days 365 \
  --key-size 3072 \
  --password change-me
```

### Tham số

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--output` / `-o` | Có | — | Đường dẫn file PKCS#12 đầu ra |
| `--password` / `-p` | Không* | — | Mật khẩu keystore; ưu tiên `JASPER_KEYSTORE_PASSWORD` |
| `--alias` | Không | `signing-key` | Alias private key trong PKCS#12 |
| `--dn` | Không | `CN=Local PDF Signer` | Subject DN của chứng thư X.509 |
| `--days` | Không | `365` | Số ngày hiệu lực chứng thư |
| `--key-size` | Không | `3072` | Độ dài khóa RSA (tối thiểu 2048) |

\* Phải có password qua `--password` hoặc `JASPER_KEYSTORE_PASSWORD`. CLI không ghi đè file output đã tồn tại.

---

## Lệnh `sign`

Ký PDF bằng private key trong PKCS#12. Mặc định: **PAdES Baseline-B** + khung chữ ký hiển thị góc dưới-phải trang 1.

### Ký cơ bản (visible signature)

```bash
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --force
```

Tùy chỉnh nhãn và cỡ chữ (ngày giờ ký luôn được thêm tự động ở dòng cuối):

```bash
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --sign-text "Đã ký bởi STP" \
  --sign-font-size 8 \
  --force
```

Ký ẩn (không vẽ khung trên trang):

```bash
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --invisible \
  --force
```

### PAdES Baseline-T (có timestamp)

```bash
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed-t.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --level T \
  --tsa-url https://tsa.example.test \
  --force
```

### PAdES Baseline-LT / LTA

Cần thêm `--truststore`, `JASPER_TRUSTSTORE_PASSWORD` và `--online` để DSS xây dựng certificate path và tải OCSP/CRL:

```bash
export JASPER_TRUSTSTORE_PASSWORD='change-me'

java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed-lt.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --level LT \
  --tsa-url https://tsa.example.test \
  --truststore keys/local-signer.p12 \
  --online \
  --force
```

| Mức `--level` | TSA | Truststore + online | Ghi chú |
| --- | --- | --- | --- |
| `B` (mặc định) | Không | Không | Chữ ký cơ bản |
| `T` | Bắt buộc `--tsa-url` | Không | Có RFC 3161 timestamp |
| `LT` | Bắt buộc | Bắt buộc | Long-term validation material |
| `LTA` | Bắt buộc | Bắt buộc | LT + archive timestamp (cần TSA archive trước khi thuật toán/cert hết hạn) |

### Tham số

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--input` / `-i` | Có | — | PDF cần ký |
| `--output` / `-o` | Có | — | PDF đã ký |
| `--keystore` / `-k` | Có | — | PKCS#12/PFX chứa private key |
| `--password` / `-p` | Không* | — | Mật khẩu keystore; ưu tiên `JASPER_KEYSTORE_PASSWORD` |
| `--alias` | Không** | — | Alias key; bắt buộc nếu keystore có nhiều key |
| `--level` | Không | `B` | `B`, `T`, `LT`, `LTA` |
| `--tsa-url` | T/LT/LTA | — | URL RFC 3161 Time-Stamp Authority |
| `--truststore` | LT/LTA | — | PKCS#12/JKS tin cậy khi build LT/LTA |
| `--truststore-password` | LT/LTA | — | Mật khẩu truststore; ưu tiên `JASPER_TRUSTSTORE_PASSWORD` |
| `--online` | Không | tắt | Bật tải AIA, OCSP, CRL |
| `--invisible` | Không | tắt | Chỉ ký số ẩn, không vẽ khung |
| `--sign-page` | Không | `1` | Trang đặt khung (bắt đầu từ 1) |
| `--sign-x`, `--sign-y` | Không | `12`, `12` | Lề từ mép trang khi neo góc dưới-phải (pt) |
| `--sign-width`, `--sign-height` | Không | `180`, `42` | Kích thước khung chữ ký (pt) |
| `--sign-font-size` | Không | `8` | Cỡ chữ khung hiển thị (8–12) |
| `--sign-text` | Không | CN chứng thư | Nhãn tùy chọn; ngày giờ ký thêm ở dòng cuối |
| `--sign-image` | Không | — | Ảnh/logo tùy chọn trong khung |
| `--force` | Không | tắt | Ghi đè file output đã tồn tại |

\* Password bắt buộc qua `--password` hoặc `JASPER_KEYSTORE_PASSWORD`.  
\*\* Nếu PKCS#12 chỉ có một private key thì có thể bỏ `--alias`.  
CLI từ chối ghi đè output trừ khi có `--force`.

### Xem chữ ký trên PDF

- **Trên trang:** mở bằng Adobe Acrobat Reader hoặc Foxit — thấy khung chữ ký và panel **Signatures**.
- **Bằng CLI:** dùng lệnh `verify` bên dưới.

---

## Lệnh `verify`

Kiểm tra chữ ký PAdES trong PDF. Với key self-signed vừa tạo, có thể dùng chính file `.p12` làm truststore.

### Ví dụ

```bash
java -jar target/jasper-cli.jar verify \
  --input output/cert-nguyen-van-an-signed.pdf \
  --truststore keys/local-signer.p12 \
  --truststore-password change-me
```

Verify với tải revocation online (OCSP/CRL):

```bash
java -jar target/jasper-cli.jar verify \
  --input output/cert-nguyen-van-an-signed.pdf \
  --truststore keys/local-signer.p12 \
  --truststore-password change-me \
  --online
```

Không có truststore: CLI vẫn kiểm tra cấu trúc/toàn vẹn chữ ký nhưng signer không nằm trong trust store sẽ không được coi là trusted (`Result` khác `TOTAL_PASSED`).

### Tham số

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--input` / `-i` | Có | — | PDF đã ký cần kiểm tra |
| `--truststore` | Không | — | PKCS#12/JKS chứa cert tin cậy |
| `--truststore-password` | Có nếu có truststore | — | Mật khẩu; ưu tiên `JASPER_TRUSTSTORE_PASSWORD` |
| `--online` | Không | tắt | Tải AIA, OCSP, CRL khi validate |

### Kết quả mẫu

```text
Document: /path/to/cert-nguyen-van-an-signed.pdf
Signatures: 1, valid: 1
- ID: ...
  Signer: CN=Local PDF Signer, ...
  Profile: PAdES-BASELINE-B
  Signing time: 2026-08-02T09:15:00Z
  Result: TOTAL_PASSED
```

Exit code `0` khi mọi chữ ký hợp lệ và trusted; `2` khi không có chữ ký hoặc validation không đạt.

---

## Luồng end-to-end

Render chứng chỉ → ký → verify trong một phiên:

```bash
mkdir -p keys output

# 1. Render PDF từ batch JSON
java -jar target/jasper-cli.jar render \
  --input examples/batch-inline.json \
  --templates config/templates \
  --assets . \
  --output output

# 2. Tạo key dev (bỏ qua nếu đã có keys/local-signer.p12)
java -jar target/jasper-cli.jar generate-key \
  --output keys/local-signer.p12 \
  --alias signer \
  --dn "CN=Local PDF Signer,O=Example,C=VN" \
  --days 365 \
  --key-size 3072 \
  --password change-me

# 3. Ký PDF (visible signature, góc dưới-phải)
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --sign-text "Đã ký bởi STP" \
  --force

# 4. Verify
java -jar target/jasper-cli.jar verify \
  --input output/cert-nguyen-van-an-signed.pdf \
  --truststore keys/local-signer.p12 \
  --truststore-password change-me
```

---

## Docker

```bash
docker build -t jasper-cli-batch .
docker run --rm \
  -v "$(pwd)/output:/app/output" \
  jasper-cli-batch \
  --input /app/examples/batch-inline.json \
  --templates /app/config/templates \
  --assets /app \
  --output /app/output \
  --merge /app/output/all-reports.pdf
```

Container mặc định chạy lệnh render legacy (args bắt đầu bằng `-`). Để ký số, override entrypoint/command:

```bash
docker run --rm \
  -v "$(pwd)/output:/app/output" \
  -v "$(pwd)/keys:/app/keys" \
  jasper-cli-batch \
  sign \
  --input /app/output/cert-nguyen-van-an.pdf \
  --output /app/output/cert-nguyen-van-an-signed.pdf \
  --keystore /app/keys/local-signer.p12 \
  --alias signer \
  --password change-me \
  --force
```

---

## Thêm mẫu JasperReports

Tạo `config/templates/my-report.jrxml` trong Jaspersoft Studio và file mapping:

```yaml
code: MY_REPORT_V1
jrxml: my-report.jrxml
defaultOutputName: my-report
recordsPath: items   # bỏ nếu report chỉ dùng parameter
parameters:
  REPORT_TITLE:
    path: title
    type: string
  ISSUE_DATE:
    path: issuedAt
    type: date
    format: dd/MM/yyyy
  LOGO:
    path: logo
    type: image
  QR_IMAGE:
    path: verificationUrl
    type: qr
    width: 300
    height: 300
  AVATAR:
    path: avatarPath
    type: assetPath
```

### Kiểu dữ liệu (`type`)

| Type | Mô tả |
| --- | --- |
| `string`, `integer`, `long`, `double`, `boolean` | Chuyển trực tiếp sang Jasper parameter |
| `date` | Parse ISO date/offset datetime, format theo `format` |
| `image` | Local path dưới `--assets`, HTTP(S), hoặc data URI base64 |
| `assetPath` | Đường dẫn file tương đối `--assets` (truyền path cho JRXML) |
| `qr` | Tạo `BufferedImage` QR từ chuỗi; tùy chọn `width`, `height` |

### Danh sách/bảng động

Đặt `recordsPath` trỏ đến JSON array trong `data`. Các phần tử được đưa vào `JRMapCollectionDataSource`; tên field trong JRXML phải trùng key JSON.

---

## Lưu ý production

- Nên compile và cache template một lần; project cache `JasperReport` trong một lần chạy CLI.
- Với hàng chục nghìn bằng, chia nhiều batch CLI/worker thay vì giữ toàn bộ `JasperPrint` để merge trong RAM.
- Mỗi bằng chính thức nên lưu file riêng; file gộp chỉ phục vụ in hàng loạt.
- Font **Noto Sans** hỗ trợ tiếng Việt đã được đóng gói và nhúng vào PDF render; license trong `fonts/LICENSE-NotoSans.txt`.
- Font **Times New Roman** dùng cho khung chữ ký PAdES (bundled trong JAR).
- **DSS** phát hành theo LGPL-2.1; cần giữ thông báo license khi phân phối binary.
- Self-signed key từ `generate-key` không thay thế chứng thư CA trong môi trường production.
- PDF landscape từ Jasper: visible signature tự căn góc dưới-phải nhờ `VisualSignatureRotation.AUTOMATIC` — không cần chỉnh `--sign-x`/`--sign-y` trừ khi muốn lề khác.
