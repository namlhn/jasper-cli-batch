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

JasperReports 7 không tương thích nhị phân với các file `.jasper` cũ. Nên giữ source `.jrxml` và compile bằng cùng phiên bản lúc build hoặc runtime.
Các file JRXML phải dùng cú pháp JasperReports 7; có thể chuyển đổi bằng chức năng **Update JasperReports files** của Jaspersoft Studio 7.

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

Trên PowerShell, thay dấu `\` cuối dòng bằng dấu backtick `` ` `` hoặc viết lệnh trên một dòng.

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
