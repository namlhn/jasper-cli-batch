# Jasper CLI Batch Renderer

CLI Java 21 để xuất một hoặc nhiều PDF từ nhiều mẫu JasperReports. Mỗi mẫu gồm:

- một file `*.jrxml` thiết kế bằng Jaspersoft Studio;
- một file `*.yaml` khai báo mã mẫu, mapping dữ liệu và kiểu dữ liệu;
- dữ liệu đầu vào nằm trực tiếp trong JSON batch.

## Kiến trúc

```text
batch JSON array (inline data)
         ↓
TemplateRegistry (*.yaml + *.jrxml)
         ↓
Parameter mapping / image / QR / records array
         ↓
JasperReports
         ↓
PDF riêng từng job + PDF gộp tùy chọn
```

## Build

```bash
mvn clean test package
```

Kết quả:

```text
target/jasper-cli.jar
```

Project dùng **JasperReports 6.21.5** — tương thích với mẫu thiết kế bằng Jaspersoft Studio 6.x. Nên giữ source `.jrxml` và compile bằng cùng phiên bản lúc build hoặc runtime.

## Các lệnh CLI

CLI mới có các subcommand:

```text
render        xuất PDF từ JasperReports
generate-key  tạo PKCS#12 self-signed để phát triển/thử nghiệm
sign          ký PDF theo chuẩn PAdES
verify        xác minh chữ ký và độ tin cậy
```

Lệnh render cũ không có tiền tố `render` vẫn được hỗ trợ để không làm hỏng script hiện tại.

## Chạy dữ liệu inline

Từ thư mục project:

```bash
java -jar target/jasper-cli.jar \
  --input examples/batch-inline.json \
  --templates config/templates \
  --assets . \
  --output output \
  --merge output/all-reports.pdf
```

Cú pháp tương đương mới là:

```bash
java -jar target/jasper-cli.jar render \
  --input examples/batch-inline.json \
  --templates config/templates \
  --assets . \
  --output output
```

Trên PowerShell, thay dấu `\` cuối dòng bằng dấu backtick `` ` `` hoặc viết lệnh trên một dòng.

## Ký số PAdES

Project dùng **European Commission DSS 6.4** với implementation PDFBox.

### Tạo key thử nghiệm

Khuyến nghị đặt password qua biến môi trường thay vì `--password` trên command line (tránh lộ trong process list hoặc shell history):

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

Khi viết lệnh nhiều dòng trong bash/zsh, **mỗi dòng trừ dòng cuối phải kết thúc bằng `\`**. Nếu thiếu `\`, shell sẽ chạy lệnh `java` trước rồi coi các option còn lại (ví dụ `--password`) là lệnh riêng và báo `command not found`.

Nếu muốn truyền password trực tiếp, thêm `\` trước dòng cuối:

```bash
java -jar target/jasper-cli.jar generate-key \
  --output keys/local-signer.p12 \
  --alias signer \
  --dn "CN=Local PDF Signer,O=Example,C=VN" \
  --days 365 \
  --key-size 3072 \
  --password change-me
```

Hoặc viết trên một dòng:

```bash
java -jar target/jasper-cli.jar generate-key --output keys/local-signer.p12 --alias signer --dn "CN=Local PDF Signer,O=Example,C=VN" --days 365 --key-size 3072 --password change-me
```

Các tham số:

| Tham số | Bắt buộc | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--output` / `-o` | Có | — | Đường dẫn file PKCS#12 đầu ra |
| `--password` / `-p` | Không* | — | Mật khẩu keystore; ưu tiên biến môi trường `JASPER_KEYSTORE_PASSWORD` |
| `--alias` | Không | `signing-key` | Alias private key trong PKCS#12 |
| `--dn` | Không | `CN=Local PDF Signer` | Subject DN của chứng thư X.509 |
| `--days` | Không | `365` | Số ngày hiệu lực chứng thư |
| `--key-size` | Không | `3072` | Độ dài khóa RSA (tối thiểu 2048) |

\* Phải có password qua `--password` hoặc `JASPER_KEYSTORE_PASSWORD`. CLI không ghi đè file output đã tồn tại.

Chứng thư self-signed này chỉ dành cho local/dev; production phải dùng private key và certificate chain do CA/TSP phù hợp cấp.

### Ký PDF

Mặc định lệnh tạo chữ ký PAdES Baseline-B:

```bash
java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me
```

Nếu PKCS#12 chỉ có một private key thì có thể bỏ `--alias`. CLI từ chối ghi đè output đã tồn tại.

Để tạo Baseline-T, cấu hình RFC 3161 TSA:

```bash
java -jar target/jasper-cli.jar sign \
  --input input.pdf \
  --output signed-t.pdf \
  --keystore signer.p12 \
  --alias signer \
  --password change-me \
  --level T \
  --tsa-url https://tsa.example.test
```

Các mức `LT` và `LTA` còn yêu cầu `--truststore`, `JASPER_TRUSTSTORE_PASSWORD` và `--online` để DSS xây dựng certificate path và tải OCSP/CRL. Một TSA URL không đủ để tạo chữ ký dài hạn hợp lệ. LTA cũng cần được bổ sung archive timestamp mới trước khi TSA certificate hoặc thuật toán cũ hết an toàn.

### Verify PDF

Kiểm tra chữ ký với truststore cục bộ. Với key self-signed vừa tạo, có thể dùng chính file `.p12` làm truststore:

```bash
java -jar target/jasper-cli.jar verify \
  --input output/cert-nguyen-van-an-signed.pdf \
  --truststore keys/local-signer.p12 \
  --truststore-password change-me
```

Thêm `--online` khi muốn tải AIA certificate, OCSP và CRL. Không có truststore, CLI vẫn kiểm tra cấu trúc/toàn vẹn chữ ký nhưng signer không được tin cậy sẽ không trả kết quả hợp lệ. Exit code là `0` khi mọi chữ ký hợp lệ và trusted, `2` khi không có chữ ký hoặc validation không đạt.

### Luồng thử nhanh (generate → sign → verify)

```bash
mkdir -p keys output

java -jar target/jasper-cli.jar generate-key \
  --output keys/local-signer.p12 \
  --alias signer \
  --dn "CN=Local PDF Signer,O=Example,C=VN" \
  --days 365 \
  --key-size 3072 \
  --password change-me

java -jar target/jasper-cli.jar sign \
  --input output/cert-nguyen-van-an.pdf \
  --output output/cert-nguyen-van-an-signed.pdf \
  --keystore keys/local-signer.p12 \
  --alias signer \
  --password change-me

java -jar target/jasper-cli.jar verify \
  --input output/cert-nguyen-van-an-signed.pdf \
  --truststore keys/local-signer.p12 \
  --truststore-password change-me
```

## Chạy bằng Docker

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

## Thêm mẫu mới

Tạo `config/templates/my-report.jrxml` trong Jaspersoft Studio và một file mapping:

```yaml
code: MY_REPORT_V1
jrxml: my-report.jrxml
defaultOutputName: my-report
recordsPath: items # bỏ nếu report chỉ dùng parameter
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
```

Các kiểu hỗ trợ:

- `string`, `integer`, `long`, `double`, `boolean`
- `date`: parse ISO date/offset datetime rồi format
- `image`: local path dưới `--assets`, HTTP(S), hoặc data URI base64
- `qr`: tạo `BufferedImage` QR từ chuỗi

### Danh sách/bảng động

Đặt `recordsPath` trỏ đến một JSON array. Các phần tử array được đưa vào `JRMapCollectionDataSource`; tên field trong JRXML phải trùng key JSON.

## Lưu ý production

- Nên compile và cache template một lần; project hiện cache `JasperReport` trong một lần chạy CLI.
- Với hàng chục nghìn bằng, chia nhiều batch CLI/worker thay vì giữ toàn bộ `JasperPrint` để merge trong RAM.
- Mỗi bằng chính thức nên lưu file riêng; file gộp chỉ phục vụ in hàng loạt.
- Font Noto Sans hỗ trợ tiếng Việt đã được đóng gói và nhúng vào PDF; license nằm trong `fonts/LICENSE-NotoSans.txt`.
- DSS được phát hành theo LGPL-2.1; cần giữ thông báo và tuân thủ điều khoản license khi phân phối binary.
