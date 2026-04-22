# MaaEnd Android

<p align="center">
  <img alt="MaaEnd Android app icon" src="docs/branding/app-icon-1024.png" width="160" height="160" />
</p>

`MaaEnd Android` 是一个面向已 Root Android 设备的 `MaaEnd` 宿主应用。它负责在手机上准备运行时、拉起 Root Runtime、同步上游任务资源、展示预览与日志，并直接执行 `MaaEnd` 任务。

当前公开版本：`1.0.2`

## 项目定位

- 目标平台：`Android 11+`
- 目标架构：`arm64-v8a`
- 使用前提：设备必须已经获取 `Root`
- 当前定位：`Root-only`，不提供 `Shizuku` 或无 Root 兼容路径

如果设备没有 Root，本项目就不能正常完成运行时拉起、输入注入、虚拟显示控制和任务执行。

## 参考项目

本项目是在 Android 侧承载 `MaaEnd` 的一次工程化实现，开发过程中直接参考或复用了这些上游项目的设计与运行时能力：

- [MaaEnd](https://github.com/MaaEnd/MaaEnd)
  - 提供任务定义、界面元数据、资源目录结构，以及 Android 端需要兼容的任务入口与资源组织方式。
- [MAA-Meow](https://github.com/Aliothmoon/MAA-Meow)
  - 在 Android 宿主形态、移动端交互组织和设备侧运行体验上提供了重要参考。
- [MaaFramework](https://github.com/MaaXYZ/MaaFramework)
  - 提供任务执行框架与运行时核心能力。
- [maa-framework-go](https://github.com/MaaXYZ/maa-framework-go)
  - 本项目中的 `go-runner` 通过它调用 `MaaFramework` 并绑定 Android 控制器。
- [libsu](https://github.com/topjohnwu/libsu)
  - 用于申请和维持 Root Shell / Root Service，是当前 Android 端 Root 链路的基础。

这不是 `MaaEnd` 官方上游仓库，而是一个 Android Root 宿主实现。

## 数据来源

运行时真正使用的任务与资源数据，默认来自 `MaaEnd` GitHub 仓库：

- 主仓库：`MaaEnd/MaaEnd`
- 默认分支：`v2`
- 资源同步范围：上游 `assets/` 下的 `interface`、`tasks`、`resource`、`resource_adb` 等目录
- 模型资源：启动同步时会额外解析并下载 `MaaEnd-AI` 子模块对应版本

也就是说：

- APK 默认不会把完整 `MaaEnd` 任务资源直接打进包里
- 首次启动或资源失效时，应用会从 GitHub 同步上游资源到本地缓存
- 任务列表、任务中文名、任务参数和大部分图像资源都以同步到本地的 `MaaEnd` 数据为准

如果你只是编译这个仓库，不额外同步 GitHub 资源，应用可以启动，但并不等于所有任务都具备可运行的完整资源。

## Root 要求

`Root` 不是可选项，而是硬性前提。

本项目当前依赖 Root 来完成这些核心能力：

- 拉起和维持特权运行时
- 注入触摸、按键和多指输入
- 建立预览所需的显示控制链路
- 让 `MaaFramework` 与 Android 控制器在同一条特权执行链上工作

因此当前不支持：

- 无 Root 设备
- 仅 ADB 权限直接运行
- `Shizuku` 兼容模式

## 快速开始

### 1. 准备运行时文件

构建前需要准备这些运行时内容：

- `runtime/agent/go-service`
- `runtime/agent/maa-go-runner`
- `runtime/maafw/`

其中：

- `go-service` 必须是 Android 可执行文件，不是桌面 Linux ELF
- `runtime/maafw/` 里需要包含可用于 Android 的 `MaaFramework` 相关 so

### 2. 编译 APK

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

默认产物位置：

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### 3. 安装到真机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. 首次启动

- 首次启动请保持联网
- 应用会同步 `MaaEnd` GitHub 资源仓库
- 只有在 Root 可用、运行时完整、资源同步完成后，任务执行能力才会完整可用

## 仓库结构

- `app/`：Android 应用、Root Runtime 桥接、Compose UI、AIDL 和测试
- `runtime/`：随 APK 一起打包或运行前准备的 Android 运行时二进制
- `tools/`：运行时准备脚本和 `go-runner`
- `docs/`：开发记录、排障文档和发布素材

## 开源协议

本仓库源码采用 [GNU Affero General Public License v3.0](LICENSE) 发布，也就是 `AGPL-3.0`。

需要特别注意两层边界：

1. 本仓库自己的 Android 代码，遵循本仓库 `LICENSE` 中声明的 `AGPL-3.0`。
2. 运行时依赖、上游资源和第三方组件，不会因为进入这个仓库就自动改成 `AGPL-3.0`。

这意味着如果你分发、修改或二次开发本项目，还需要分别遵守对应上游组件的许可证和归属要求，尤其包括但不限于：

- `MaaEnd` 上游任务与资源
- `MaaFramework` 及其相关运行时
- `maa-framework-go`
- `libsu`
- 你自行打包进 APK 或运行时目录的其他二进制、模型和资源

如果你打算公开发布自己的修改版 APK，建议在发布前逐项确认这些上游组件的许可证兼容性与归属说明。
