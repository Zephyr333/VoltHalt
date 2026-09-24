# VoltHalt 开机恢复检查

检查基准：上游 `im-atp/VoltHalt`，提交 `7867341`。修复位于本地分支 `codex/fix-boot-monitoring`，未发布到 GitHub。

## 根因与修复范围

- Manifest 声明了 `RECEIVE_BOOT_COMPLETED`，但原代码没有开机 Receiver，系统没有可调用的恢复入口。
- MainActivity 的恢复条件只读 Max 开关，遗漏 Low-only；设置页 Low 开关只写设置，不启动服务。
- 新增 BootReceiver，接收 `BOOT_COMPLETED` 和 `MY_PACKAGE_REPLACED`。`goAsync()` 保持广播处理有效，设置读取限时 8 秒，并在 finally 中结束广播。
- 所有启动入口读取原有 DataStore 的同一份状态快照，以 `Max || Low` 决定是否运行。不新增独立的“运行中”持久化标记，原设置键保持兼容。
- BatteryService 及时进入前台，读取完整设置后才处理缓存的首次电池广播；设置改变也会重新判断报警及服务是否应停止。
- “Stop Monitoring”先在同一个 DataStore 事务中关闭两个开关，写入完成后停止，避免 onDestroy 取消未完成写入。停止声音仍保留服务的 START_STICKY 恢复能力。
- 服务销毁取消待播放任务、停止声音、清除报警通知及前台通知。播放与停止在主线程协调，避免异步读取设置后又启动已停止的报警。
- 快捷磁贴先保存 Max 开关，再同步服务；不会因尚未加载的 Low 缓存误停 Low-only 监控。磁贴显示任一报警是否启用，并不是系统实际运行状态的证明。
- AlarmActivity 在注册首次电池回调前确定报警类型，避免未插电的低电量报警被当作 Max 报警关闭。
- 补充构建配置引用但上游缺失的 `app/proguard-rules.pro`；保留现有 SDK 和生产包名。

## 重启后恢复流程

设备正常重启 → 首次解锁 → 系统发送 BOOT_COMPLETED → Receiver 读取 DataStore → 任一开关开启才请求前台服务 → 服务加载设置 → 对当前电池状态开始监控，必要时报警。

两个开关都关闭时，Receiver 不请求启动服务。普通的进程回收后，START_STICKY 重建仍重新读取设置；它不能绕过强制停止或厂商后台限制。

## 小米平板 5 / 澎湃 OS 1 实机验收

本次由用户自行实机测试。以下项目完成前，不把本地测试通过等同于设备封版通过。

测试包显示名称为 **VoltHalt Boot Test**，包名 `com.im_atp.volthalt.boottest`，与原版独立安装、独立保存设置和系统权限。请为测试包重新完成通知、自启动、后台运行与电池设置；不要把原版的授权视为测试包已有授权。不要卸载原版来尝试覆盖安装。

每轮先设置对应开关、确认当下监控状态，再正常重启平板。首次解锁后不要打开 VoltHalt Boot Test，直接检查通知；为避免双重报警，可先关闭原版的两个报警。

| Max | Low | 重启并首次解锁后的期望 |
|---|---|---|
| 开 | 关 | 监控通知自动出现，充电达到 Max 阈值可报警 |
| 关 | 开 | 监控通知自动出现，未充电且低于 Low 阈值可报警 |
| 开 | 开 | 监控通知自动出现，按充电状态使用对应报警 |
| 关 | 关 | 没有监控通知，不无条件启动监控 |

补充检查：

1. 在设置页单独打开 Low，确认立即监控；重启后仍恢复。
2. 保留 Low 时关闭 Max（首页和磁贴均测），监控继续；再关闭 Low，通知消失。
3. 点击通知“Stop Monitoring”，确认两个开关变为关闭，再重启，不能自动恢复。
4. 点击“Stop Alarm”只停止当前声音，监控通知保留；拔插充电器后对应报警仍工作。
5. 重启时电量已低于 Low 阈值，首次解锁后不等待下一次电量变化，也能触发低电量报警。
6. 分别确认铃声、TTS、振动、音量、锁屏报警画面，以及设置重启后仍保留。首次解锁前不属于本次实现范围。
7. 首次解锁后再锁屏并待机，检查监控是否持续；这用于排除厂商后续清理。

## 系统“自启动”开关自行关闭

当前代码没有修改厂商自启动开关、AppOps 或禁用自身组件的操作。本次确认的是 App 缺少广播接收入口，不能据此解释系统开关为什么从开变关。

开关属于系统/厂商的管理状态；若复现，记录发生前后是否有应用更新、重装、系统更新或省电策略变化，并保留设置截图。未取得这类证据前，不断言是哪种系统机制导致。修复不能替系统保留或重新打开这个开关。

Android 对受限后台应用可能推迟开机广播；具体厂商行为需要实机确认。通知被禁用时，没有通知也不一定等于没有服务。

可选诊断（用户有 ADB 时；无需模拟或伪造开机广播）：

```text
adb shell dumpsys activity services com.im_atp.volthalt.boottest
adb logcat -d -s VoltHaltBoot VoltHaltMonitoring AndroidRuntime
```

`VoltHaltBoot` 记录收到的广播；`VoltHaltMonitoring` 记录启动请求和拒绝/读取失败。启动请求日志不等于系统已经成功运行服务，应结合服务状态和通知判断。

## 本地验证

测试使用 Robolectric 的 Android 模型，不是平板实机或真实重启。覆盖 Manifest 接收器注册、四种报警开关组合的开机分流、Low-only 初始电池事件、停止声音后保留 sticky、关闭最后一个报警、空 Intent 重建、停止监控后的持久化及下次开机、应用更新恢复及无关广播过滤。

D 盘构建副本：`D:\GradleCache\volthalt\workspace`。Gradle 缓存：`D:\GradleCache\volthalt`。Android SDK：`D:\DevTools\Android\Sdk`。OneDrive 目录中的生成文件曾被占用，因此最终验证在 D 盘构建副本执行。

```powershell
$env:GRADLE_USER_HOME = 'D:\GradleCache\volthalt'
$env:ANDROID_HOME = 'D:\DevTools\Android\Sdk'
$env:ANDROID_USER_HOME = 'D:\GradleCache\volthalt\android'
.\gradlew.bat '-PtestApplicationIdSuffix=.boottest' :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
```

构建与测试最终结果将在完成后记录。Release 未配置原作者签名，不作为可覆盖原版的正式安装包。

## 依据

- [Android 前台服务后台启动例外](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android 后台限制与开机广播](https://developer.android.com/topic/performance/background-optimization)
- [Direct Boot 与凭据加密存储](https://developer.android.com/privacy-and-security/direct-boot)
