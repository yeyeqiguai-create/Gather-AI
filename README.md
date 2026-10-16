# Gather-AI
一个手机应用，通过调用api来对话，支持各种强大功能，可根据任务自主选择模型，图像理解，coading，后续可能会加入类似muse的功能

## 版本管理

- 版本名和 Android `versionCode` 的唯一配置源是根目录 `gradle.properties` 中的 `gather.versionName` 与 `gather.versionCode`。
- 版本名必须符合 `vX.X.X.XX (ReviseX)`；基础功能完善前，Gradle 校验只接受 `0.1.x`，拒绝升级到 `0.2` 或 `1.0`。
- 每次 Beta 或正式测试分发前都要提高 `gather.versionCode`，不得复用或降低已分发的版本码。它与面向用户显示的版本名分开管理。
- 内部开发使用 `gradlew.bat assembleDebug`，APK 文件名带 `internal-dev` 标记。
- Beta 使用 `gradlew.bat assembleRelease -Pgather.releaseChannel=beta`，APK 文件名带 `beta` 标记。
- 正式测试使用 `gradlew.bat assembleRelease`，默认 APK 文件名带 `formal-test` 标记。也可显式指定 `-Pgather.releaseChannel=formal-test`。
- Beta 和正式测试共用现有 release 构建，不增加构建变体；渠道属性只区分产物文件名。当前未配置 release 签名，产物签名与分发准备仍需单独处理，签名密钥不得提交到仓库。
