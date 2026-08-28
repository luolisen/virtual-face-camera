# Virtual Face Camera

Virtual Face Camera 0.2.2 是基于 [Android CamSwap](https://github.com/zensu357/Android-CamSwap-OpenSource) 开发的 Android Xposed/LSPosed 虚拟摄像头模块，准确基线为上游 `v2.8`。本版本正在等待新设备实测验证。本项目保留上游 GPL-3.0 许可证和版权信息，并感谢 [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) 的代码与思路启发。

项目仓库：[luolisen/virtual-face-camera](https://github.com/luolisen/virtual-face-camera)

## 身份

- 应用名称：Virtual Face Camera
- 版本：`0.2.2`（`versionCode 4`，开发中）
- package / namespace：`io.github.alanlaw.vfc`
- Provider authority：`io.github.alanlaw.vfc.provider`

## 功能

- Camera1 / Camera2 虚拟摄像头替换
- LSPosed 注入
- 视频实时替换与指定视频热切换
- 动态虚拟传感器取景、FIT（完整显示、保持比例、黑边填充）/ CROP（保持比例、铺满裁切）
- 0° / 90° / 180° / 270° 实时旋转
- 多预设：每个预设固定包含“点、左、右、张、眨”五个视频槽位
- App 内创建、重命名、删除和切换当前预设
- 通过 Android 系统媒体选择器导入并直接绑定视频
- 悬浮窗附近切换当前预设，固定五键热切换
- 悬浮窗提供实时旋转和动态取景控制
- 预览按真实 EGL Camera buffer 尺寸进行几何适配，不使用宿主 Activity 尺寸改写目标比例

固定快捷键顺序：

`点` | `左` | `右` | `张` | `眨`

## 构建环境

- JDK 17
- Android SDK Platform 36
- Android Build Tools（由 Gradle 实际解析）
- NDK `25.1.8937393`
- CMake `3.22.1`
- Gradle Wrapper `8.11.1`

项目使用 ABI split，Release 会按实际配置生成 `arm64-v8a`、`armeabi-v7a` 和 `x86_64` APK。

标准命令：

```bash
./gradlew assembleRelease
```

0.2.2 开发更新：修复横向 Camera buffer 在竖屏预览中的比例适配；移除废弃的 Java/native 麦克风 Hook；移除通知栏控制；将旋转控制迁移到悬浮窗；增加动态虚拟传感器取景控制，并提升复杂媒体应用的兼容性。该版本尚未创建 GitHub Release，需完成设备回归后再发布。

Release 必须使用本机正式签名配置。签名 keystore 和密码不属于仓库内容；请在本机 `local.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，不要把密码提交或分享。

## 安装与启用

1. 从 [Releases](https://github.com/luolisen/virtual-face-camera/releases) 下载与你设备 ABI 匹配的 APK，通常为 `arm64-v8a`。
2. 安装后在 LSPosed 管理器中手动启用 **Virtual Face Camera**。
3. 为实际目标应用重新勾选 LSPosed 作用域，然后按目标应用需要重新启动目标进程。
4. 打开 Virtual Face Camera，在“媒体库”中创建预设，展开预设后点击五个槽位之一，使用系统媒体选择器导入并完成绑定。
5. 在“设置”中选择动态、FIT 或 CROP；动态模式还可以调整取景移动步长，并按需开启悬浮窗。

本版本更换了 applicationId。旧版 `io.github.zensu357.camswap` 不会被自动卸载或修改作用域；新包需要人工重新启用。旧 App 与新 App 不建议同时运行，因为历史媒体目录和配置文件可能共享。

配置与受管理视频仍位于：

```text
/sdcard/DCIM/Camera1/
/sdcard/DCIM/Camera1/cs_config.json
```

## 链接

- GitHub：[https://github.com/luolisen/virtual-face-camera](https://github.com/luolisen/virtual-face-camera)
- Releases：[https://github.com/luolisen/virtual-face-camera/releases](https://github.com/luolisen/virtual-face-camera/releases)
- Telegram：[https://t.me/virtualfacecarema](https://t.me/virtualfacecarema)
- Upstream：[https://github.com/zensu357/Android-CamSwap-OpenSource](https://github.com/zensu357/Android-CamSwap-OpenSource)

## 许可证

本项目继续遵守 [GPL-3.0](LICENSE)。
