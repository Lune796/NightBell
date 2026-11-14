# 夜铃 NightBell

一个把 AI 恋人做成“能给你打电话”的安卓客户端。界面是本地 WebView，语音识别在手机本地跑，合成语音可接多家 TTS，对话内容与 Operit 内的 AI 角色互通。

## 使用前提（重要）

1. 夜铃不是独立可用的 App，通讯本体是 Operit AI。没装 Operit、没配置角色卡，装上也只是一个空壳。
2. 必须在设置里填：对话窗口 ID、角色卡名称。两个都填对，你的回话才会落到正确的对话里，AI 也才认得出是你。
3. 语音合成支持五家：阿里、MOSS、ElevenLabs、火山、MiniMax。在设置里选一家、填它自己的 Key 与音色即可；不填则只有本地拨号音，听不到说话。
   - 流式播放：ElevenLabs（交给系统播放器边下边播）、火山（SSE 分片，收到就播）两家都是边收边播；阿里、MOSS、MiniMax 走整段合成后播放。
4. 语音识别默认走 sherpa-onnx（SenseVoice），无网也能识别，模型已随安装包内置，首次启动自动解到手机本地（约 228MB），不用自己下载配置。
5. 通话记录、录音、识别文本全部存在本机 `Download/Operit/app_data/nightbell/`，不上传任何服务器。

## 依赖

- 安卓 11 以上（8.0–10 可安装，界面显示不保证）
- Operit AI（提供对话与角色卡）
- 后台运行权限、悬浮窗权限、麦克风权限（需允许自启动、允许后台弹出界面）

## 开源许可

本项目代码以 MIT 许可发布，见 `LICENSE`。随包分发的第三方组件及各自许可，见下方「第三方组件声明」。

## 第三方组件声明

夜铃（NightBell）在分发版本中包含以下开源组件。按各自许可证要求，此处保留版权与许可声明。

### sherpa-onnx（语音识别，优先引擎）
- 项目：https://github.com/k2-fsa/sherpa-onnx
- 版权：Copyright (c) 2023-2026 k2-fsa
- 许可证：Apache License 2.0（全文见 https://www.apache.org/licenses/LICENSE-2.0 ）
- 说明：以本地离线方式运行，音频不离开设备。

### SenseVoice 模型（语音识别模型）
- 版权：Copyright (c) 2024 FunAudioLLM / Alibaba Group
- 许可证：Apache License 2.0
- 说明：随 App 附带，仅用于本机离线识别。

### onnxruntime（推理运行时）
- 项目：https://github.com/microsoft/onnxruntime
- 版权：Copyright (c) Microsoft Corporation
- 许可证：MIT License

### 百度翻译开放平台（翻译服务）
- 服务：https://fanyi-api.baidu.com/
- 说明：翻译功能通过百度翻译开放平台的通用翻译 API 以 HTTP 方式调用，本项目不包含百度提供的任何 SDK 或代码。appid 与密钥由使用者自行申请并填入设置，分发版本不附带任何密钥。使用该接口须遵守百度翻译开放平台的服务条款。

### MD5（百度翻译接口签名）
- 规范：RFC 1321（R. Rivest, 1992）
- 说明：`assets/app.html` 中的 `bdMd5()` 为按 RFC 1321 自行实现的纯 JS 版本，未引用任何第三方代码。MD5 算法本身不受版权保护，此处仅作来源说明。

### Apache License 2.0 全文
分发时请随包附上 Apache License 2.0 完整文本（见仓库内 `LICENSE-Apache-2.0.txt`），不得以一句“使用了开源库”代替。

---

若修改并再分发上述 Apache 2.0 组件，须保留原版权声明，并在修改过的文件中注明已修改。
