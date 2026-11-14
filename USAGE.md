# 夜铃 NightBell 使用教程

## 一、下载

固定下载地址（以后发新版也是这个，不用换）：

```
https://github.com/Lune796/NightBell/releases/latest/download/NightBell.apk
```

国内打不开 GitHub 的话，在前面加一个加速前缀：

```
https://gh-proxy.com/https://github.com/Lune796/NightBell/releases/latest/download/NightBell.apk
```

上面这个加速要是也打不开，再换这两个：

```
https://ghfast.top/https://github.com/Lune796/NightBell/releases/latest/download/NightBell.apk
```

```
https://kkgithub.com/Lune796/NightBell/releases/latest/download/NightBell.apk
```

安装包约 252MB，因为本地语音识别模型直接打在包里了，装完不用再下模型。只支持 arm64（现在绝大多数手机都是），系统要求安卓 11 以上。

## 关于许可

本项目用 MIT 协议发布：**随便改、随便用、随便分发，商用也行**，不用来问，不用署名到你的作品里，只要求把原来的版权声明和许可文本留着。源码在 `https://github.com/Lune796/NightBell`，想怎么改都行。

## 二、安装

1. 用浏览器打开上面的链接，下载 apk。
2. 点开下载好的文件，系统会提示「禁止安装未知应用」，点设置，允许浏览器安装，再返回继续装。
3. 装完先别急着打开，把权限给完再开。

## 三、给权限

进「设置 → 应用 → 夜铃」，逐项打开：

- 麦克风：允许（不给就没法听你说话）
- 悬浮窗：允许（来电弹窗要用）
- 通知：允许
- 电池：选「不受限制」，别让系统省电时把它掐掉
- 自启动、后台弹出界面：允许

vivo、小米、OPPO 这些系统里，自启动和后台弹出界面通常在「应用权限」或「电池」里单独一项，找不到就在设置顶部搜「自启动」。

## 四、填配置

打开夜铃，点右上角进设置：

1. **对话 ID**：填 Operit 里你要通话的那个对话窗口的 ID。填哪个窗口，电话里的消息就进哪个窗口。
2. **角色卡名称**：填你要通话的角色卡名字，要和 Operit 里的一字不差。
3. **语音合成（TTS）**：支持阿里、MOSS、ElevenLabs、火山、MiniMax 五家，选一家，填它自己的 Key 和音色。
   - MiniMax 除了 Key，还要填「群组编号」（GroupId）。
   - 火山要填「资源 ID」和「号码」。
   - 不填也能打，只是只有拨号音，听不到说话。
4. **翻译（可选）**：要用翻译功能才填，百度翻译开放平台自己申请的 appid 和密钥，不填就是没有翻译。

## 五、打电话

回到主界面，点拨号，输入数字拨出去。接通后对着手机说话就行，识别在本地跑，不联网也能用。

通话记录、录音、识别出来的文字都存在本机 `Download/Operit/app_data/nightbell/` 里，不会上传到任何服务器。

## 六、怎么查看包里的 LICENSE 和 README

apk 本质上就是一个压缩包，里面的 `assets` 目录带着三份文件：

- `LICENSE`（MIT 许可全文）
- `LICENSE-Apache-2.0.txt`（Apache 2.0 全文）
- `README.md`（项目说明与第三方组件声明）

**手机上查看：**

用 MT 管理器、ZArchiver 这类能看压缩包的文件管理器，长按 apk → 打开方式 → 压缩包查看 → 进 `assets` 目录 → 点开对应的文件即可。

**电脑上查看：**

- Windows：把 apk 复制一份，改名成 `.zip`，用压缩软件打开，进 `assets`。
- Mac / Linux：`unzip 夜铃.apk -d nightbell`，然后看 `nightbell/assets/`。

**不用下安装包也能看：**

仓库页面上直接就能读：`https://github.com/Lune796/NightBell`，根目录下 README.md、LICENSE、LICENSE-Apache-2.0.txt 都在。
