# MaaEnd Android

<p align="center">
  <img alt="MaaEnd Android app icon" src="docs/branding/app-icon-1024.png" width="160" height="160" />
</p>

`MaaEnd Android` 是 MaaEnd 的 Android Root 宿主应用，面向已经 Root 的 arm64 设备，负责运行时准备、Root Runtime 拉起、虚拟显示预览、任务配置与实时日志展示。

当前仓库当前公开发布版本为 `1.0.1`。

## 1.0.1 包含内容

- Root-only Android 宿主应用与 Root Runtime 启动链路
- 运行时文件打包、资源目录准备与 GitHub 资源更新
- MaaFramework 任务执行、预设/任务配置与运行状态展示
- 预览窗口、截图链路与实时日志页面
- 仅保留内存态的日志页数据，杀后台后自动清空

## 运行要求

- Android 11 及以上
- `arm64-v8a`
- 已获取 Root 权限
- 默认构建与打包不会读取同级 `../MaaEnd` 仓库

## 快速开始

1. 准备运行时文件：
   - `runtime/agent/go-service`
   - `runtime/agent/maa-go-runner`
   - `runtime/maafw/`
2. 首次启动应用时保持联网，应用会自动同步 `MaaEnd` GitHub 资源仓库。
3. 构建 APK：
   - 调试包：`./gradlew assembleDebug`
   - 发布包：`./gradlew assembleRelease`
4. 安装到设备：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## 仓库结构

- `app/`：Android 应用、Root Runtime 桥接、Compose UI 与测试
- `runtime/`：本地打包时需要放入的 Android 运行时二进制
- `tools/`：运行时准备与 Go runner 相关工具
- `docs/`：会话总结、开发备忘与发布素材

## 运行时打包说明

打包时会组合以下来源：

- 当前仓库 `runtime/` 下的 Android 可执行文件与 MaaFramework so
- 构建阶段生成的 `bundled_runtime` 运行时目录

`interface/tasks/resource` 与 `__Private` 辅助资源都不再内置到 APK，首次启动和运行前会从 `MaaEnd` GitHub 仓库同步到本地缓存。

如果 `runtime/` 为空，应用仍可启动，但完整任务执行能力不会可用。

## 开源协议

本仓库源码采用 `GNU Affero General Public License v3.0` (`AGPL-3.0`) 发布，详见 [LICENSE](LICENSE)。

需要特别注意：

- 本仓库与 MaaEnd 上游资源协议保持一致；如果你显式复用上游源码、运行时或同步资源，仍需分别遵守对应组件的许可证与归属说明。
- 构建或发布 APK 时，如果你一并分发了运行时二进制、资源、模型或其他第三方组件，还需要分别遵守这些组件各自的许可证与归属说明。
- 本仓库不会覆盖或改变 MaaFramework、模型资源以及其他第三方依赖原有的许可证。

## 安全与发布建议

- 不要提交 `local.properties`、签名文件、私有 token、用户日志、截图或诊断包。
- 发布前至少执行一次敏感信息扫描与一次 `assembleRelease` 验证。
- 若需要 GitHub Release，推荐使用 `v1.0.1` 形式的 tag，与 `versionName` 保持一致。

## 开发说明

- 构建脚本会在 `preBuild` 阶段准备打包资产与 JNI so。
- 运行期日志文件正常落盘，但日志页面只消费实时内存增量，不会回读旧日志文件。
- 最近一次 Android 端开发总结见 `docs/SESSION_SUMMARY.md`。
