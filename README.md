# J2ME Emulator

Trình giả lập J2ME cho Android — launcher riêng + engine J2ME Loader tích hợp
bên trong, đóng gói thành một APK độc lập. Không yêu cầu cài J2ME Loader riêng.

- Package: `com.khahdihdz.j2meemulator`
- Version: `0.1.0` (versionCode `1`)
- Branch phát triển: `feat/launcher-engine`
- Ngôn ngữ: Kotlin + Java (engine dùng Java)
- Build: Gradle + Android Gradle Plugin

## Trạng thái hiện tại: engine đã tích hợp (Giai đoạn 2), launcher Home/Details/About xong (Giai đoạn 3), import JAR/JAD xong (Giai đoạn 4)

Engine J2ME Loader 1.8.2 đã được hợp nhất vào app qua script
`tools/merge-engine.py` chạy trên GitHub Actions, và CI trên nhánh
`feat/launcher-engine` đang xanh (merge → unit test → lint → build
debug APK). Home hiển thị danh sách game đã cài, mở file
`.jar`/`.jad`/`.kjx` qua ACTION_VIEW/ACTION_SEND hoặc trình chọn file
(SAF), và chạy game sau khi import thành công. **Chưa cài/chơi thử
trên máy thật.** Các giai đoạn còn lại (quản lý thư viện, điều khiển,
tests, ký release v0.1.0) xem trong
[`docs/ENGINE-INTEGRATION.md`](docs/ENGINE-INTEGRATION.md).

## Engine

Engine là [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader),
giấy phép **Apache License 2.0**, pin tại tag **`1.8.2`** (commit `7e978c5`).

J2ME Loader **không phải thư viện** — không có AAR/Maven artifact, không có
module library công khai. Nó là một app Android nguyên khối (`app` + module
phụ `dexlib`). Vì vậy tích hợp = fork/submodule toàn bộ source engine, giữ
nguyên module `app` của engine làm module chính, đổi `applicationId` sang
`com.khahdihdz.j2meemulator`, giữ `Config` / `MicroActivity` / `MicroLoader` /
`AppInstaller` / `dexlib` / native M3G nguyên vẹn, và viết launcher riêng lên
trên. Xem chi tiết trong [`docs/ENGINE-INTEGRATION.md`](docs/ENGINE-INTEGRATION.md).

Khai báo submodule (chưa checkout nội dung trong skeleton này):

```git
[submodule "vendor/j2me-loader"]
    path = vendor/j2me-loader
    url = https://github.com/nikita36078/J2ME-Loader.git
```

Khi bắt đầu tích hợp engine:

```sh
git submodule update --init
git -C vendor/j2me-loader checkout 1.8.2
```

Lưu ý: file `.gitmodules` của upstream ở tag `1.8.2` trả 404 qua GitHub raw
(không có trong cây tag đó); trên `master` upstream có một submodule duy nhất
là `TinySoundFont` (`app/src/main/cpp/mmapi_tsf/TinySoundFont`,
`https://github.com/schellingb/TinySoundFont.git`). Khi checkout engine ở
`1.8.2`, cần kiểm tra lại submodule của engine ở đúng tag đó trước khi build
native.

## Toolchain đã xác minh từ upstream tag 1.8.2 (đọc trực tiếp file gốc)

| Hạng mục | Giá trị đã xác minh | Nguồn |
|---|---|---|
| Android Gradle Plugin | 8.5.1 | `build.gradle` gốc |
| Gradle | 8.7 | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | 34 | `build.gradle` gốc (`ext`) |
| minSdk | 14 | `build.gradle` gốc (`ext`) |
| NDK | 22.1.7171670 | `build.gradle` gốc (`ext`) |
| JDK | CHƯA XÁC MINH trong file upstream | AGP 8.5.1/Gradle 8.7 yêu cầu JDK 17 để chạy — ghi là giả định, CI phải xác minh |
| Room | 2.5.2 | `app/build.gradle` gốc |
| Kotlin BOM | 1.8.0 | `app/build.gradle` gốc (chỉ `platform(...)`, không có plugin Kotlin trong file gốc) |

Mâu thuẫn cần ghi rõ: README của upstream nói Android 5.0+ (API 21) nhưng
`app/build.gradle` gốc đặt `MIN_SDK = 14` (Android 4.0). Skeleton này giữ
`minSdk = 14` theo `app/build.gradle` gốc. Chốt minSdk cuối cùng khi build
engine gốc pass trên CI.

Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)
được lấy từ upstream tag `1.8.2`; SHA git blob của `gradle-wrapper.jar` đã
khớp với upstream (`13372aef5e24af05341d49695ee84e5f9b594659`).

## Version qua biến môi trường (phục vụ apksign)

`app/build.gradle` đọc version từ biến môi trường, để workflow ký chung
`khahdihdz/apksign` (`android-shared-signing.yml`) truyền vào:

```groovy
versionCode = System.getenv("APP_VERSION_CODE")?.toInteger() ?: 1
versionName = System.getenv("APP_VERSION_NAME") ?: "0.1.0"
```

Nếu không có biến môi trường, mặc định là versionCode `1`, versionName `0.1.0`.

## Build (chưa chạy được ở máy local hiện tại)

Máy làm skeleton này **không có JDK, Gradle hay Android SDK** (`java`,
`gradle`, `ANDROID_HOME` đều không tồn tại), nên **chưa build hay kiểm tra
cú pháp Kotlin/Gradle được ở local**. Build đầu tiên phải chạy trên GitHub
Actions (JDK 17 + Android SDK + Gradle 8.7 + AGP 8.5.1) và được báo cáo trung
thực theo kết quả CI.

```sh
./gradlew assembleDebug
```

## Ký APK

Dùng repo [khahdihdz/apksign](https://github.com/khahdihdz/apksign) (MIT):
chạy workflow sync để cấp 4 secret `ANDROID_SIGNING_*` cho repo này (chỉ chạy
được sau khi `src/main/AndroidManifest.xml` đã có trên nhánh mặc định), rồi
gọi reusable workflow `android-shared-signing.yml` (`workflow_call`,
`version-code: "1"`, `version-name: "0.1.0"`, `secrets: inherit`). Workflow ký
chung không tạo GitHub Release — việc tạo release làm ở workflow của repo này.

## Giấy phép

- Code launcher của repo này: Apache License 2.0 (xem `LICENSE`).
- Engine J2ME Loader: Apache License 2.0, Copyright 2017–2026 Nikita Shakarun.
  Giữ nguyên file `LICENSE` và header license của engine khi tích hợp, ghi rõ
  thay đổi theo điều khoản Apache-2.0 §4.
- Lưu ý: bên trong engine còn có thể có thành phần giấy phép khác (M3G của
  Nokia, FFmpeg, font keypad) — phải kiểm tra trực tiếp header license trong
  `app/src/main/cpp` và `assets` của engine trước khi phát hành.
