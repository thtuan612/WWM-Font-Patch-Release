<div align="center">

# WWM Font Patch

### Custom Font Patcher for Where Winds Meet

Tùy chỉnh font chữ trong Where Winds Meet ngay trên Android.

**Offline · Hỗ trợ Root & Shizuku**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)

[![Tham gia Server Discord](https://img.shields.io/badge/Tham_gia_Server_Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/ajZXSthPN2)

</div>

---

## 📖 Giới thiệu

**WWM Font Patch** là ứng dụng Android được phát triển nhằm giúp người chơi tùy chỉnh font chữ trong game *Where Winds Meet* (燕云十六声) mà không cần sử dụng máy tính.

Ứng dụng tập trung vào việc thay thế font chữ của game, hỗ trợ người dùng lựa chọn font TTF yêu thích để cải thiện khả năng hiển thị văn bản hoặc cá nhân hóa giao diện.

## ✨ Tính năng

- 🎨 **Custom Font:** Sử dụng font `.ttf` do người dùng lựa chọn.
- 🔤 **Ba nhóm font:** Tùy chỉnh Font 1, Font 2 và Font 3 tương ứng với các nhóm font trong game.
- 📱 **Hoạt động trực tiếp trên Android:** Không bắt buộc phải sử dụng máy tính.
- 🔌 **Hỗ trợ Root:** Ghi dữ liệu trực tiếp khi thiết bị có quyền root.
- 🛠️ **Hỗ trợ Shizuku:** Cung cấp phương thức ghi dữ liệu thông qua Shizuku.
- 🎮 **Hỗ trợ nhiều phiên bản game:** Hướng đến Where Winds Meet Global, phiên bản Funtap (VN) và phiên bản Trung Quốc.
- 🧹 **Gỡ patch có chọn lọc:** Xóa phần patch font do ứng dụng tạo mà không chủ động xóa các patch khác.
- 🚀 **Mở khóa FPS:** Chọn 60 / 90 / 120 FPS, ép game chạy đúng mức này (bỏ qua cài đặt FPS trong game) và tắt vsync; gỡ riêng được, không ảnh hưởng patch font.

---

## 📲 Cài đặt

### 1. Tải ứng dụng

Truy cập repository và mở mục **Releases** để tìm bản APK được phát hành.

### 2. Cài đặt APK

1. Tải APK về thiết bị Android.
2. Cho phép cài đặt ứng dụng từ nguồn bạn sử dụng nếu Android yêu cầu.
3. Cài đặt và mở WWM Font Patch.
4. Cấp quyền phù hợp với phương thức ghi file bạn định sử dụng.

> ⚠️ Chỉ cài APK từ nguồn mà bạn tin tưởng.

---

## 🎨 Hướng dẫn sử dụng

### Bước 1 — Chuẩn bị font

Chuẩn bị file font có định dạng `.ttf` mà bạn muốn sử dụng.

> ⚠️ Bạn nên chọn font có hỗ trợ đầy đủ các ký tự cần thiết, đặc biệt nếu muốn hiển thị tiếng Việt với các dấu: `ă â đ ê ô ơ ư` và các ký tự có dấu thanh.

### Bước 2 — Thêm font vào ứng dụng

1. Mở WWM Font Patch.
2. Chọn chức năng thêm file `.ttf` từ bộ nhớ.
3. Chọn file font mong muốn.
4. Gán font vào một trong ba nhóm Font Chính, Font Giao Diện hoặc Font Hội Thoại.

### Bước 3 — Chọn phương thức ghi dữ liệu

WWM Font Patch cung cấp các phương thức ghi dữ liệu khác nhau.

| Phương thức   | Điều kiện                                          | Ghi chú                                                              |
| ------------- | -------------------------------------------------- | -------------------------------------------------------------------- |
| Root (`su`)   | Thiết bị đã root và cấp quyền cho ứng dụng         | Có thể ghi trực tiếp vào dữ liệu game                                |
| Shizuku       | Shizuku đang hoạt động và đã cấp quyền phù hợp     | Sử dụng cơ chế thực thi lệnh thông qua Shizuku                       |
| Game Provider | Phiên bản game hỗ trợ phương thức này              | Yêu cầu game chạy nền; (không hỗ trợ bản Trung Quốc)                 |

> Nếu chọn chế độ tự động, ứng dụng ưu tiên phương thức Root khi khả dụng.

### Bước 4 — Áp dụng và kiểm tra

Sau khi chọn font và phương thức ghi:

1. Kiểm tra lại lựa chọn.
2. Thực hiện thao tác áp dụng patch.
3. Mở game và kiểm tra hiển thị font.
4. Nếu font không thay đổi hoặc game gặp lỗi, hãy khôi phục bản sao lưu trước khi thử phương án khác.

---

## 💾 Sao lưu, khôi phục và gỡ patch

### Sao lưu

Trước mỗi lần chỉnh sửa, ứng dụng tạo bản sao `db_mp` trong vùng lưu trữ riêng của ứng dụng.

> Bạn nên giữ bản sao lưu này cho đến khi xác nhận game hoạt động bình thường.

### Khôi phục

Nếu gặp sự cố:

1. Mở WWM Font Patch.
2. Chọn chức năng **Khôi phục**.
3. Đưa dữ liệu về bản sao lưu gần nhất.
4. Khởi động lại game và kiểm tra.

### Gỡ patch font

Chức năng **Gỡ patch font** được thiết kế để loại bỏ đoạn patch do WWM Font Patch tạo ra.

Mục tiêu là giữ nguyên những patch khác, chẳng hạn patch dịch thuật hoặc patch FPS.

---

## ⚠️ Cảnh báo và trách nhiệm sử dụng

WWM Font Patch là công cụ chỉnh sửa dữ liệu cục bộ của game. Việc thay đổi dữ liệu có thể dẫn đến các vấn đề như:

- Font không hiển thị đúng hoặc xuất hiện ký tự bị lỗi.
- Game không nhận patch sau khi cập nhật.
- Game bị treo, lỗi khởi động hoặc tự khôi phục dữ liệu.
- Dữ liệu game bị hỏng do ghi file không thành công.
- Việc chỉnh sửa file game có thể không phù hợp với điều khoản dịch vụ của nhà phát hành.

**Bạn tự chịu trách nhiệm khi sử dụng công cụ này.**

---

## 📜 Bản quyền

*Where Winds Meet* và các tài nguyên, dữ liệu, tên gọi hoặc thành phần thuộc game vẫn thuộc quyền sở hữu của nhà phát hành và các chủ sở hữu tương ứng.

WWM Font Patch là công cụ hỗ trợ tùy chỉnh font, **không phải** sản phẩm chính thức của nhà phát hành.

Dự án không đại diện, không liên kết và không được bảo trợ bởi NetEase Games hoặc Funtap.

Hiệu ứng Liquid Glass được chuyển thể sang Java/View từ [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Apache License 2.0), thông qua bản chuyển thể trong [KernelSU Manager](https://github.com/tiann/KernelSU). Phần thuật toán khúc xạ gốc thuộc về tác giả của dự án đó.

Phần mềm được phát hành theo giấy phép [MIT License](LICENSE).

---

## 🔗 Liên kết

<div align="center">

[Repository](https://github.com/thtuan612/WWM-Font-Patch-Release) · [Issues](https://github.com/thtuan612/WWM-Font-Patch-Release/issues) · [Releases](https://github.com/thtuan612/WWM-Font-Patch-Release/releases) · [Discord](https://discord.gg/ajZXSthPN2)

</div>

---

<div align="center">

**WWM Font Patch — Make Where Winds Meet yours.**

*Được thiết kế dành cho người chơi Android muốn kiểm soát nhiều hơn kiểu chữ trong game..*

</div>

> ⚠️ Hiện tại vẫn đang trong quá trình hoàn thiện nên có thể gặp nhiều vấn đề.
